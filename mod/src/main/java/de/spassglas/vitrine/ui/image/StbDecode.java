package de.spassglas.vitrine.ui.image;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * JPG (und andere von stb_image gelesene Formate) zu ARGB, ohne AWT -- stb_image
 * liegt Minecraft ueber LWJGL ohnehin bei. Fuer Galeriebilder von Modrinth.
 */
final class StbDecode {

	private StbDecode() {
	}

	static WebpLossless.Image decode(byte[] bytes) throws IOException {
		ByteBuffer in = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			in.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer c = stack.mallocInt(1);
			if (!STBImage.stbi_info_from_memory(in, w, h, c)) {
				throw new IOException("Bild unlesbar: " + STBImage.stbi_failure_reason());
			}
			if (w.get(0) > WebpLossless.MAX_SIDE || h.get(0) > WebpLossless.MAX_SIDE) {
				throw new IOException("Bild zu gross");
			}
			ByteBuffer px = STBImage.stbi_load_from_memory(in, w, h, c, 4);
			if (px == null) {
				throw new IOException("Bild unlesbar: " + STBImage.stbi_failure_reason());
			}
			try {
				int width = w.get(0);
				int height = h.get(0);
				int[] argb = new int[width * height];
				for (int i = 0; i < argb.length; i++) {
					int r = px.get(i * 4) & 0xff;
					int g = px.get(i * 4 + 1) & 0xff;
					int b = px.get(i * 4 + 2) & 0xff;
					int a = px.get(i * 4 + 3) & 0xff;
					argb[i] = a << 24 | r << 16 | g << 8 | b;
				}
				return new WebpLossless.Image(width, height, argb);
			} finally {
				STBImage.stbi_image_free(px);
			}
		} finally {
			MemoryUtil.memFree(in);
		}
	}
}
