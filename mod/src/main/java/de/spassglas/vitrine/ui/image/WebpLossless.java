package de.spassglas.vitrine.ui.image;

import java.io.IOException;

/**
 * Reiner Java-Decoder fuer verlustfreies WebP (VP8L), ohne AWT und ohne
 * native Bibliothek. Grund: Modrinth liefert Projekt-Icons und Galerie-Bilder
 * als VP8L-WebP, Minecraft kann aber nur PNG lesen und seine Java-Laufzeit hat
 * kein AWT (umgekocht, 2026-10-07: "bei allem das Logo anzeigen").
 *
 * <p>Umsetzung nach der WebP-Lossless-Spezifikation (RFC 9649): Praefix-Codes,
 * LZ77-Rueckverweise, Farb-Cache und die vier Transformationen. Verlustbehaftetes
 * WebP (VP8) und Animationen werden mit einer {@link IOException} abgelehnt --
 * der Aufrufer zeigt dann sein Ersatzbild.</p>
 *
 * <p>Sicherheit: Kantenlaenge hoechstens {@link #MAX_SIDE}, jeder Lesezugriff
 * hinter dem Datenende wirft, Rueckverweise vor den Bildanfang werfen.</p>
 */
public final class WebpLossless {

	public static final int MAX_SIDE = 4096;

	/** Ergebnis: ARGB-Pixel zeilenweise. */
	public record Image(int width, int height, int[] argb) {
	}

	private WebpLossless() {
	}

	public static boolean isWebp(byte[] b) {
		return b != null && b.length > 16 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
				&& b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
	}

	public static Image decode(byte[] file) throws IOException {
		if (!isWebp(file)) {
			throw new IOException("kein WebP");
		}
		int pos = 12;
		while (pos + 8 <= file.length) {
			String tag = new String(file, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
			int size = (file[pos + 4] & 0xff) | (file[pos + 5] & 0xff) << 8 | (file[pos + 6] & 0xff) << 16
					| (file[pos + 7] & 0xff) << 24;
			if (size < 0 || pos + 8 + size > file.length) {
				throw new IOException("WebP-Chunk ausserhalb der Datei");
			}
			if (tag.equals("VP8L")) {
				return new Decoder(file, pos + 8, pos + 8 + size).decodeHeaderAndImage();
			}
			if (tag.equals("VP8 ") || tag.equals("ANIM")) {
				throw new IOException("nur verlustfreies, nicht animiertes WebP wird unterstuetzt");
			}
			pos += 8 + size + (size & 1);
		}
		throw new IOException("kein VP8L-Chunk");
	}

	// --- Bit-Leser (LSB zuerst) -------------------------------------------

	private static final class Decoder {
		private final byte[] data;
		private final int end;
		private int bytePos;
		private long bitBuf;
		private int bitCount;

		Decoder(byte[] data, int start, int end) {
			this.data = data;
			this.bytePos = start;
			this.end = end;
		}

		int bits(int n) throws IOException {
			while (bitCount < n) {
				if (bytePos >= end) {
					// Ein paar Nullbits ueber das Ende hinaus sind erlaubt (Auffuellung),
					// ein echter Ueberlauf faellt spaeter als Fehler auf.
					if (bitCount > -64 && bytePos < end + 8) {
						bytePos++;
						bitCount += 8;
						continue;
					}
					throw new IOException("WebP-Daten zu kurz");
				}
				bitBuf |= (long) (data[bytePos++] & 0xff) << bitCount;
				bitCount += 8;
			}
			int v = (int) (bitBuf & ((1L << n) - 1));
			bitBuf >>>= n;
			bitCount -= n;
			return v;
		}

		int bit() throws IOException {
			return bits(1);
		}

		Image decodeHeaderAndImage() throws IOException {
			if (bits(8) != 0x2f) {
				throw new IOException("VP8L-Signatur fehlt");
			}
			int width = bits(14) + 1;
			int height = bits(14) + 1;
			bits(1); // alpha_is_used (nur Hinweis)
			if (bits(3) != 0) {
				throw new IOException("unbekannte VP8L-Version");
			}
			if (width > MAX_SIDE || height > MAX_SIDE) {
				throw new IOException("WebP zu gross: " + width + "x" + height);
			}
			// Transformationen
			int[] types = new int[4];
			int[] tBits = new int[4];
			int[][] tData = new int[4][];
			int[] tWidth = new int[4];
			int count = 0;
			int xsize = width;
			boolean[] seen = new boolean[4];
			while (bit() == 1) {
				int type = bits(2);
				if (seen[type]) {
					throw new IOException("Transformation doppelt");
				}
				seen[type] = true;
				types[count] = type;
				tWidth[count] = xsize;
				switch (type) {
					case 0, 1 -> {
						int b = bits(3) + 2;
						tBits[count] = b;
						tData[count] = decodeImageStream(divUp(xsize, 1 << b), divUp(height, 1 << b), false);
					}
					case 3 -> {
						int colors = bits(8) + 1;
						int b = colors > 16 ? 0 : colors > 4 ? 1 : colors > 2 ? 2 : 3;
						int[] pal = decodeImageStream(colors, 1, false);
						for (int i = 1; i < colors; i++) {
							pal[i] = addPixels(pal[i], pal[i - 1]);
						}
						tBits[count] = b;
						tData[count] = pal;
						xsize = divUp(xsize, 1 << b);
					}
					default -> {
					}
				}
				count++;
			}
			int[] pixels = decodeImageStream(xsize, height, true);
			for (int i = count - 1; i >= 0; i--) {
				int w = tWidth[i];
				switch (types[i]) {
					case 0 -> inversePredictor(pixels, w, height, tBits[i], tData[i]);
					case 1 -> inverseColor(pixels, w, height, tBits[i], tData[i]);
					case 2 -> inverseSubtractGreen(pixels);
					case 3 -> pixels = inverseIndexing(pixels, w, height, tBits[i], tData[i]);
					default -> {
					}
				}
			}
			return new Image(width, height, pixels);
		}

		int[] decodeImageStream(int xs, int ys, boolean level0) throws IOException {
			int cacheBits = 0;
			if (bit() == 1) {
				cacheBits = bits(4);
				if (cacheBits < 1 || cacheBits > 11) {
					throw new IOException("ungueltiger Farb-Cache");
				}
			}
			int prefixBits = 0;
			int[] entropy = null;
			int entropyW = 0;
			int groups = 1;
			if (level0 && bit() == 1) {
				prefixBits = bits(3) + 2;
				entropyW = divUp(xs, 1 << prefixBits);
				entropy = decodeImageStream(entropyW, divUp(ys, 1 << prefixBits), false);
				int max = 0;
				for (int i = 0; i < entropy.length; i++) {
					entropy[i] = (entropy[i] >> 8) & 0xffff;
					max = Math.max(max, entropy[i]);
				}
				groups = max + 1;
			}
			int cacheSize = cacheBits > 0 ? 1 << cacheBits : 0;
			Huffman[][] codes = new Huffman[groups][5];
			int[] sizes = {256 + 24 + cacheSize, 256, 256, 256, 40};
			for (int g = 0; g < groups; g++) {
				for (int k = 0; k < 5; k++) {
					codes[g][k] = readCode(sizes[k]);
				}
			}
			int[] cache = cacheSize > 0 ? new int[cacheSize] : null;
			int total = xs * ys;
			int[] out = new int[total];
			int pos = 0;
			int cacheInserted = 0;
			while (pos < total) {
				Huffman[] h = codes[0];
				if (entropy != null) {
					int x = pos % xs;
					int y = pos / xs;
					h = codes[entropy[(y >> prefixBits) * entropyW + (x >> prefixBits)]];
				}
				int g = h[0].read(this);
				if (g < 256) {
					int r = h[1].read(this);
					int b = h[2].read(this);
					int a = h[3].read(this);
					out[pos++] = a << 24 | r << 16 | g << 8 | b;
				} else if (g < 256 + 24) {
					int length = prefixValue(g - 256);
					int distCode = prefixValue(h[4].read(this));
					int dist = planeDistance(xs, distCode);
					if (dist > pos || pos + length > total) {
						throw new IOException("ungueltiger Rueckverweis");
					}
					for (int i = 0; i < length; i++) {
						out[pos] = out[pos - dist];
						pos++;
					}
				} else {
					if (cache == null) {
						throw new IOException("Farb-Cache-Code ohne Cache");
					}
					// alle bisher dekodierten Pixel in den Cache uebernehmen
					while (cacheInserted < pos) {
						int c = out[cacheInserted++];
						cache[(0x1e35a7bd * c) >>> (32 - cacheBits)] = c;
					}
					out[pos++] = cache[g - 280];
				}
				if (cache != null) {
					while (cacheInserted < pos) {
						int c = out[cacheInserted++];
						cache[(0x1e35a7bd * c) >>> (32 - cacheBits)] = c;
					}
				}
			}
			return out;
		}

		int prefixValue(int prefix) throws IOException {
			if (prefix < 4) {
				return prefix + 1;
			}
			int extra = (prefix - 2) >> 1;
			int offset = (2 + (prefix & 1)) << extra;
			return offset + bits(extra) + 1;
		}

		private static final int[] ORDER = {17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

		Huffman readCode(int alphabet) throws IOException {
			int[] lengths = new int[alphabet];
			if (bit() == 1) {
				int n = bits(1) + 1;
				int first8 = bits(1);
				int s0 = bits(first8 == 1 ? 8 : 1);
				if (s0 >= alphabet) {
					throw new IOException("Symbol ausserhalb des Alphabets");
				}
				lengths[s0] = 1;
				if (n == 2) {
					int s1 = bits(8);
					if (s1 >= alphabet) {
						throw new IOException("Symbol ausserhalb des Alphabets");
					}
					lengths[s1] = 1;
				}
				return Huffman.of(lengths);
			}
			int[] clLengths = new int[19];
			int num = bits(4) + 4;
			for (int i = 0; i < num; i++) {
				clLengths[ORDER[i]] = bits(3);
			}
			Huffman cl = Huffman.of(clLengths);
			int maxSymbol = alphabet;
			if (bit() == 1) {
				int nbits = 2 + 2 * bits(3);
				maxSymbol = 2 + bits(nbits);
				if (maxSymbol > alphabet) {
					throw new IOException("max_symbol zu gross");
				}
			}
			int prev = 8;
			int sym = 0;
			while (sym < alphabet) {
				if (maxSymbol-- == 0) {
					break;
				}
				int len = cl.read(this);
				if (len < 16) {
					lengths[sym++] = len;
					if (len != 0) {
						prev = len;
					}
				} else {
					int repeat;
					int value;
					if (len == 16) {
						repeat = 3 + bits(2);
						value = prev;
					} else if (len == 17) {
						repeat = 3 + bits(3);
						value = 0;
					} else {
						repeat = 11 + bits(7);
						value = 0;
					}
					if (sym + repeat > alphabet) {
						throw new IOException("Code-Laengen ueberlaufen");
					}
					for (int i = 0; i < repeat; i++) {
						lengths[sym++] = value;
					}
				}
			}
			return Huffman.of(lengths);
		}
	}

	/** Kanonischer Praefix-Code, Dekodierung Bit fuer Bit wie in zlibs "puff". */
	private static final class Huffman {
		final int[] count = new int[16];
		final int[] symbols;
		final int single;

		private Huffman(int[] lengths) throws IOException {
			int used = 0;
			int last = -1;
			for (int i = 0; i < lengths.length; i++) {
				if (lengths[i] > 0) {
					if (lengths[i] > 15) {
						throw new IOException("Code-Laenge zu gross");
					}
					count[lengths[i]]++;
					used++;
					last = i;
				}
			}
			if (used == 0) {
				throw new IOException("leerer Praefix-Code");
			}
			single = used == 1 ? last : -1;
			int[] offs = new int[16];
			for (int len = 1; len < 16; len++) {
				offs[len] = offs[len - 1] + count[len - 1];
			}
			symbols = new int[used];
			count[0] = 0;
			for (int i = 0; i < lengths.length; i++) {
				if (lengths[i] > 0) {
					symbols[offs[lengths[i]]++] = i;
				}
			}
		}

		static Huffman of(int[] lengths) throws IOException {
			return new Huffman(lengths);
		}

		int read(Decoder d) throws IOException {
			if (single >= 0) {
				return single;
			}
			int code = 0;
			int first = 0;
			int index = 0;
			for (int len = 1; len < 16; len++) {
				code |= d.bit();
				int c = count[len];
				if (code - first < c) {
					return symbols[index + code - first];
				}
				index += c;
				first += c;
				first <<= 1;
				code <<= 1;
			}
			throw new IOException("ungueltiger Praefix-Code");
		}
	}

	// --- Transformationen ---------------------------------------------------

	private static final int[] DIST_MAP = {
			0, 1, 1, 0, 1, 1, -1, 1, 0, 2, 2, 0, 1, 2, -1, 2, 2, 1, -2, 1, 2, 2, -2, 2, 0, 3, 3, 0, 1, 3, -1, 3,
			3, 1, -3, 1, 2, 3, -2, 3, 3, 2, -3, 2, 0, 4, 4, 0, 1, 4, -1, 4, 4, 1, -4, 1, 3, 3, -3, 3, 2, 4, -2, 4,
			4, 2, -4, 2, 0, 5, 3, 4, -3, 4, 4, 3, -4, 3, 5, 0, 1, 5, -1, 5, 5, 1, -5, 1, 2, 5, -2, 5, 5, 2, -5, 2,
			4, 4, -4, 4, 3, 5, -3, 5, 5, 3, -5, 3, 0, 6, 6, 0, 1, 6, -1, 6, 6, 1, -6, 1, 2, 6, -2, 6, 6, 2, -6, 2,
			4, 5, -4, 5, 5, 4, -5, 4, 3, 6, -3, 6, 6, 3, -6, 3, 0, 7, 7, 0, 1, 7, -1, 7, 5, 5, -5, 5, 7, 1, -7, 1,
			4, 6, -4, 6, 6, 4, -6, 4, 2, 7, -2, 7, 7, 2, -7, 2, 3, 7, -3, 7, 7, 3, -7, 3, 5, 6, -5, 6, 6, 5, -6, 5,
			8, 0, 4, 7, -4, 7, 7, 4, -7, 4, 8, 1, 8, 2, 6, 6, -6, 6, 8, 3, 5, 7, -5, 7, 7, 5, -7, 5, 8, 4, 6, 7,
			-6, 7, 7, 6, -7, 6, 8, 5, 7, 7, -7, 7, 8, 6, 8, 7};

	static int planeDistance(int xs, int code) {
		if (code > 120) {
			return code - 120;
		}
		int xi = DIST_MAP[(code - 1) * 2];
		int yi = DIST_MAP[(code - 1) * 2 + 1];
		int d = yi * xs + xi;
		return Math.max(1, d);
	}

	static int divUp(int a, int b) {
		return (a + b - 1) / b;
	}

	static int addPixels(int a, int b) {
		int alpha = ((a >>> 24) + (b >>> 24)) & 0xff;
		int red = (((a >> 16) & 0xff) + ((b >> 16) & 0xff)) & 0xff;
		int green = (((a >> 8) & 0xff) + ((b >> 8) & 0xff)) & 0xff;
		int blue = ((a & 0xff) + (b & 0xff)) & 0xff;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int avg2(int a, int b) {
		return (((a ^ b) & 0xfefefefe) >>> 1) + (a & b);
	}

	private static int ch(int p, int s) {
		return (p >>> s) & 0xff;
	}

	private static int select(int l, int t, int tl) {
		int pl = 0;
		int pt = 0;
		for (int s = 0; s < 32; s += 8) {
			int p = ch(l, s) + ch(t, s) - ch(tl, s);
			pl += Math.abs(p - ch(l, s));
			pt += Math.abs(p - ch(t, s));
		}
		return pl < pt ? l : t;
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : Math.min(v, 255);
	}

	private static int clampAddSubFull(int a, int b, int c) {
		int out = 0;
		for (int s = 0; s < 32; s += 8) {
			out |= clamp(ch(a, s) + ch(b, s) - ch(c, s)) << s;
		}
		return out;
	}

	private static int clampAddSubHalf(int a, int b) {
		int out = 0;
		for (int s = 0; s < 32; s += 8) {
			int x = ch(a, s);
			out |= clamp(x + (x - ch(b, s)) / 2) << s;
		}
		return out;
	}

	private static void inversePredictor(int[] px, int w, int h, int bits, int[] modes) {
		int bw = divUp(w, 1 << bits);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				int pred;
				if (y == 0) {
					pred = x == 0 ? 0xff000000 : px[i - 1];
				} else if (x == 0) {
					pred = px[i - w];
				} else {
					int mode = (modes[(y >> bits) * bw + (x >> bits)] >> 8) & 0xf;
					int l = px[i - 1];
					int t = px[i - w];
					int tl = px[i - w - 1];
					int tr = px[i - w + 1];
					pred = switch (mode) {
						case 0 -> 0xff000000;
						case 1 -> l;
						case 2 -> t;
						case 3 -> tr;
						case 4 -> tl;
						case 5 -> avg2(avg2(l, tr), t);
						case 6 -> avg2(l, tl);
						case 7 -> avg2(l, t);
						case 8 -> avg2(tl, t);
						case 9 -> avg2(t, tr);
						case 10 -> avg2(avg2(l, tl), avg2(t, tr));
						case 11 -> select(l, t, tl);
						case 12 -> clampAddSubFull(l, t, tl);
						case 13 -> clampAddSubHalf(avg2(l, t), tl);
						default -> l;
					};
				}
				px[i] = addPixels(px[i], pred);
			}
		}
	}

	private static int delta(int t, int c) {
		return ((byte) t * (byte) c) >> 5;
	}

	private static void inverseColor(int[] px, int w, int h, int bits, int[] elems) {
		int bw = divUp(w, 1 << bits);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int e = elems[(y >> bits) * bw + (x >> bits)];
				int g2r = e & 0xff;
				int g2b = (e >> 8) & 0xff;
				int r2b = (e >> 16) & 0xff;
				int i = y * w + x;
				int p = px[i];
				int green = (p >> 8) & 0xff;
				int red = ((p >> 16) + delta(g2r, green)) & 0xff;
				int blue = ((p & 0xff) + delta(g2b, green) + delta(r2b, red)) & 0xff;
				px[i] = (p & 0xff00ff00) | red << 16 | blue;
			}
		}
	}

	private static void inverseSubtractGreen(int[] px) {
		for (int i = 0; i < px.length; i++) {
			int p = px[i];
			int g = (p >> 8) & 0xff;
			int r = ((p >> 16) + g) & 0xff;
			int b = (p + g) & 0xff;
			px[i] = (p & 0xff00ff00) | r << 16 | b;
		}
	}

	private static int[] inverseIndexing(int[] packed, int w, int h, int bits, int[] palette) {
		int[] out = new int[w * h];
		int pw = divUp(w, 1 << bits);
		int perPixel = 1 << bits;
		int bitsPer = 8 >> bits;
		int mask = (1 << bitsPer) - 1;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int p = packed[y * pw + (x >> bits)];
				int idx = ((p >> 8) & 0xff) >> ((x & (perPixel - 1)) * bitsPer) & mask;
				out[y * w + x] = idx < palette.length ? palette[idx] : 0;
			}
		}
		return out;
	}
}
