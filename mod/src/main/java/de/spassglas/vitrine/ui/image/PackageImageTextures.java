package de.spassglas.vitrine.ui.image;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GATE17: macht aus einem Vorschaubild im {@code .vpkg} eine Spiel-Textur und
 * zeichnet sie seitenrichtig eingepasst. Die Bytes liefert und prueft
 * {@link PackageImageBytes}; hier passiert nur, was einen laufenden Client
 * braucht.
 *
 * <p>Je (Datei, Aenderungszeit, Eintrag) wird hoechstens einmal geladen --
 * auch ein Fehlschlag wird gemerkt, damit nicht jedes Bild bei jedem Frame
 * erneut versucht wird. Aendert sich die Datei, gilt der alte Eintrag nicht mehr.</p>
 */
public final class PackageImageTextures {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Images");

	/** Eine geladene Textur samt Originalmaszen (fuers seitenrichtige Einpassen). */
	public record Texture(Identifier id, int width, int height) {
	}

	/**
	 * Abstraktion ueber das eigentliche GPU-Registrieren/-Abmelden einer
	 * Textur (ROADMAP GATE118). Produktion verdrahtet den echten
	 * {@code TextureManager} von {@link MinecraftClient}; Tests tauschen ein
	 * zaehlendes Test-Double ein, damit sich das Ablosen einer alten
	 * Paket-Version auch ohne laufenden Client beweisen laesst.
	 */
	interface GpuTextures {
		void register(Identifier id, NativeImage image);

		void destroy(Identifier id);
	}

	private static final GpuTextures PRODUCTION_GPU = new GpuTextures() {
		@Override
		public void register(Identifier id, NativeImage image) {
			NativeImageBackedTexture backed = new NativeImageBackedTexture(image);
			// Paketbilder werden meist vergroessert oder verkleinert gezeichnet:
			// linear filtern statt pixelig (naechster Nachbar ist Minecrafts Vorgabe).
			backed.setFilter(true, false);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, backed);
		}

		@Override
		public void destroy(Identifier id) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
		}
	};

	private static GpuTextures gpu = PRODUCTION_GPU;

	/** Nur fuer Tests: ersetzt die GPU-Anbindung durch ein zaehlendes Test-Double. */
	static void useGpuForTesting(GpuTextures replacement) {
		gpu = replacement == null ? PRODUCTION_GPU : replacement;
	}

	private static final Map<String, Optional<Texture>> CACHE = new ConcurrentHashMap<>();
	private static final Map<Identifier, Texture> BY_ID = new ConcurrentHashMap<>();
	// GATE118: je logischem Paketbild (Datei + Eintrag, ohne Aenderungszeit) die
	// zuletzt vergebene Cache-Zeile. So erkennt get(), wenn eine neue
	// Aenderungszeit eine aeltere abloest -- die alte Textur wird dann
	// abgemeldet statt fuer die restliche Sitzung ungenutzt liegen zu bleiben.
	private static final Map<String, String> LATEST_VERSION_KEY = new ConcurrentHashMap<>();
	private static final AtomicInteger COUNTER = new AtomicInteger();

	private PackageImageTextures() {
	}

	/** Muss auf dem Render-Thread laufen (Textur-Hochladen). */
	public static Optional<Texture> get(Path vpkgFile, String entryPath) {
		if (vpkgFile == null || entryPath == null) {
			return Optional.empty();
		}
		String imageKey;
		String versionKey;
		try {
			imageKey = vpkgFile.toAbsolutePath() + "|" + entryPath;
			versionKey = imageKey + "|" + Files.getLastModifiedTime(vpkgFile).toMillis();
		} catch (IOException e) {
			return Optional.empty();
		}
		Optional<Texture> result = CACHE.computeIfAbsent(versionKey, k -> load(vpkgFile, entryPath));
		result.ifPresent(texture -> registerVersion(imageKey, versionKey, texture));
		return result;
	}

	private static Optional<Texture> load(Path vpkgFile, String entryPath) {
		Optional<PackageImageBytes.Image> image = PackageImageBytes.read(vpkgFile, entryPath);
		if (image.isEmpty()) {
			return Optional.empty();
		}
		try {
			NativeImage native_ = NativeImage.read(image.get().png());
			Identifier id = Identifier.of(Vitrine.MOD_ID, "package_image/" + COUNTER.incrementAndGet());
			gpu.register(id, native_);
			return Optional.of(new Texture(id, image.get().width(), image.get().height()));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Vorschaubild '{}' aus '{}' liess sich nicht als Textur laden: {}", entryPath, vpkgFile,
					e.toString());
			return Optional.empty();
		}
	}

	/**
	 * Merkt {@code texture} als aktuelle Version von {@code imageKey} vor und
	 * meldet die zuvor aktuelle Textur ab, falls sich der Versionsschluessel
	 * geaendert hat (ROADMAP GATE118).
	 */
	private static void registerVersion(String imageKey, String versionKey, Texture texture) {
		BY_ID.put(texture.id(), texture);
		String previousVersionKey = LATEST_VERSION_KEY.put(imageKey, versionKey);
		if (previousVersionKey == null || previousVersionKey.equals(versionKey)) {
			return;
		}
		Optional<Texture> stale = CACHE.remove(previousVersionKey);
		if (stale != null) {
			stale.ifPresent(PackageImageTextures::destroyTexture);
		}
	}

	private static void destroyTexture(Texture texture) {
		BY_ID.remove(texture.id());
		try {
			gpu.destroy(texture.id());
		} catch (RuntimeException e) {
			LOGGER.warn("Alte Paket-Textur '{}' liess sich nicht freigeben: {}", texture.id(), e.toString());
		}
	}

	/**
	 * Nur fuer Tests: registriert {@code texture} direkt im Cache, ohne
	 * NativeImage/GPU anzufassen, damit das Ablosen einer alten Version ohne
	 * laufenden Minecraft-Client geprueft werden kann (ROADMAP GATE118).
	 */
	static void putVersionForTesting(String imageKey, String versionKey, Texture texture) {
		CACHE.put(versionKey, Optional.of(texture));
		registerVersion(imageKey, versionKey, texture);
	}

	/** Nur fuer Tests: setzt den gesamten Cache-Zustand zurueck. */
	static void resetForTesting() {
		CACHE.clear();
		BY_ID.clear();
		LATEST_VERSION_KEY.clear();
		gpu = PRODUCTION_GPU;
	}

	/** Eine schon geladene Paket-Textur samt Maszen, z. B. fuer eine Kachel, die nur die Kennung kennt. */
	public static Optional<Texture> byId(Identifier id) {
		return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
	}

	/**
	 * Zeichnet {@code texture} in das Rechteck, ohne es zu verzerren: auf den
	 * Hintergrund eingepasst und mittig. Ein quadratisches Icon in einer
	 * 16:9-Kachel bekommt also Raender statt einer gestauchten Darstellung.
	 */
	public static void drawFitted(DrawContext context, Texture texture, int x, int y, int width, int height) {
		context.fill(x, y, x + width, y + height, ActiveThemeColors.surfaceSolid());
		double scale = Math.min((double) width / texture.width(), (double) height / texture.height());
		int drawW = Math.max(1, (int) Math.round(texture.width() * scale));
		int drawH = Math.max(1, (int) Math.round(texture.height() * scale));
		int dx = x + (width - drawW) / 2;
		int dy = y + (height - drawH) / 2;
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(texture.id(), dx, dy, drawW, drawH, 0.0F, 0.0F,
				texture.width(), texture.height(), texture.width(), texture.height());
	}

	/**
	 * Fuellt das Rechteck ganz aus, ohne zu verzerren: der ueberstehende Teil
	 * wird mittig abgeschnitten (fuer Vorschaubilder in Kachel und Hero).
	 */
	public static void drawCover(DrawContext context, Texture texture, int x, int y, int width, int height) {
		double target = (double) width / Math.max(1, height);
		double source = (double) texture.width() / Math.max(1, texture.height());
		int regionW = texture.width();
		int regionH = texture.height();
		if (source > target) {
			regionW = Math.max(1, (int) Math.round(texture.height() * target));
		} else {
			regionH = Math.max(1, (int) Math.round(texture.width() / target));
		}
		float u = (texture.width() - regionW) / 2.0F;
		float v = (texture.height() - regionH) / 2.0F;
		de.spassglas.vitrine.ui.theme.GuiBlend.on();
		context.drawTexture(texture.id(), x, y, width, height, u, v, regionW, regionH, texture.width(), texture.height());
	}
}
