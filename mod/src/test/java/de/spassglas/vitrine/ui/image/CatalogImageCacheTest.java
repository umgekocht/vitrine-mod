package de.spassglas.vitrine.ui.image;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Katalog-Vorschaubilder: nur https-PNGs von GitHub werden ueberhaupt geladen. */
public class CatalogImageCacheTest {

	@Test
	public void acceptsGithubPngs() {
		assertTrue(CatalogImageCache.isAllowedUrl(
				"https://raw.githubusercontent.com/umgekocht/vitrine-packs-starter/main/kompakt-hud/icon.png"));
		assertTrue(CatalogImageCache.isAllowedUrl("https://umgekocht.github.io/vitrine-registry/x/preview.png"));
	}

	@Test
	public void rejectsEverythingElse() {
		assertFalse(CatalogImageCache.isAllowedUrl(null));
		assertFalse(CatalogImageCache.isAllowedUrl(""));
		assertFalse(CatalogImageCache.isAllowedUrl("http://raw.githubusercontent.com/a/icon.png"), "kein https");
		assertFalse(CatalogImageCache.isAllowedUrl("https://evil.example/icon.png"), "fremder Host");
		assertFalse(CatalogImageCache.isAllowedUrl("https://raw.githubusercontent.com.evil.example/icon.png"));
		assertFalse(CatalogImageCache.isAllowedUrl("https://raw.githubusercontent.com/a/script.js"), "kein PNG");
		assertFalse(CatalogImageCache.isAllowedUrl("file:///C:/icon.png"));
	}

	@Test
	public void rejectsNonPngBytes() {
		assertFalse(CatalogImageCache.validPng(new byte[] {1, 2, 3}));
	}
}
