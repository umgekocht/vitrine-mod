package de.spassglas.vitrine.ui.image;

import net.minecraft.util.Identifier;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * GATE118: Ersetzt ein Paket sein Vorschaubild (gleiches logisches Bild,
 * neuer mtime-Schluessel), muss die alte GPU-Textur abgemeldet werden --
 * sonst haeuft sich pro Aktualisierung eine liegenbleibende Textur an.
 *
 * <p>Das Hoch- und Abladen selbst braucht einen laufenden Minecraft-Client
 * (OpenGL-Kontext); hier geht es nur um die Buchhaltung des Caches, deshalb
 * laeuft der Test ueber {@link PackageImageTextures.GpuTextures}, ein
 * zaehlendes Test-Double, ohne jede NativeImage-/GL-Beruehrung.</p>
 */
public class PackageImageTexturesTest {

	private CountingGpu gpu;

	@BeforeMethod
	public void setUp() {
		gpu = new CountingGpu();
		PackageImageTextures.resetForTesting();
		PackageImageTextures.useGpuForTesting(gpu);
	}

	@AfterMethod
	public void tearDown() {
		PackageImageTextures.resetForTesting();
	}

	@Test
	public void replacingAPackageImageDestroysTheOldTexture() {
		String imageKey = "irgendein-paket.vpkg|preview/card.png";
		Identifier oldId = Identifier.of("vitrine", "package_image/alt");
		Identifier newId = Identifier.of("vitrine", "package_image/neu");
		PackageImageTextures.Texture oldTexture = new PackageImageTextures.Texture(oldId, 512, 288);
		PackageImageTextures.Texture newTexture = new PackageImageTextures.Texture(newId, 512, 288);

		// erste Version (aelterer mtime-Schluessel) wird bekannt gemacht
		PackageImageTextures.putVersionForTesting(imageKey, imageKey + "|1000", oldTexture);
		assertTrue(PackageImageTextures.byId(oldId).isPresent(), "alte Textur zunaechst im Cache");
		assertEquals(gpu.destroyed.size(), 0, "beim ersten Mal gibt es noch nichts abzumelden");

		// Paket aktualisiert sich -- neuer mtime-Schluessel, dieselbe logische Datei
		PackageImageTextures.putVersionForTesting(imageKey, imageKey + "|2000", newTexture);

		assertEquals(gpu.destroyed, List.of(oldId), "die alte Textur-Kennung wurde genau einmal abgemeldet");
		assertTrue(PackageImageTextures.byId(newId).isPresent(), "neue Textur ist jetzt bekannt");
		assertTrue(PackageImageTextures.byId(oldId).isEmpty(), "alte Textur ist nicht mehr auffindbar");
	}

	@Test
	public void sameVersionKeyIsNeverDestroyedAgain() {
		String imageKey = "irgendein-paket.vpkg|preview/card.png";
		Identifier id = Identifier.of("vitrine", "package_image/einzig");
		PackageImageTextures.Texture texture = new PackageImageTextures.Texture(id, 16, 16);
		String versionKey = imageKey + "|1000";

		PackageImageTextures.putVersionForTesting(imageKey, versionKey, texture);
		PackageImageTextures.putVersionForTesting(imageKey, versionKey, texture);

		assertEquals(gpu.destroyed.size(), 0, "dieselbe Version abzumelden waere falsch");
	}

	@Test
	public void unrelatedPackagesDoNotDestroyEachOthersTextures() {
		Identifier idA = Identifier.of("vitrine", "package_image/a");
		Identifier idB = Identifier.of("vitrine", "package_image/b");
		PackageImageTextures.putVersionForTesting("paketA|card.png", "paketA|card.png|1000",
				new PackageImageTextures.Texture(idA, 16, 16));
		PackageImageTextures.putVersionForTesting("paketB|card.png", "paketB|card.png|1000",
				new PackageImageTextures.Texture(idB, 16, 16));

		assertEquals(gpu.destroyed.size(), 0, "verschiedene Pakete betreffen sich nicht");
		assertTrue(PackageImageTextures.byId(idA).isPresent());
		assertTrue(PackageImageTextures.byId(idB).isPresent());
	}

	/** Zaehlt register/destroy-Aufrufe, ohne je eine echte GPU-Textur zu beruehren. */
	private static final class CountingGpu implements PackageImageTextures.GpuTextures {
		final List<Identifier> destroyed = new ArrayList<>();

		@Override
		public void register(Identifier id, net.minecraft.client.texture.NativeImage image) {
			// im Test nie aufgerufen: putVersionForTesting geht am Laden vorbei.
		}

		@Override
		public void destroy(Identifier id) {
			destroyed.add(id);
		}
	}
}
