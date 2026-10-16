package de.spassglas.vitrine.ui.tile;

import java.util.List;

/**
 * Fest verdrahtete Beispiel-Kacheln fuer die Start-Seite, solange es noch
 * keine echte Kachel-Quelle gibt (die kommt mit ROADMAP S2 "Lokaler Ordner"
 * bzw. M4 "Katalog"). {@code StoreScreen} zeigt daraus eine Reihe, damit
 * Aufbau und Verhalten aus S1 im laufenden Spiel sichtbar sind.
 *
 * <p>Die Reihe {@link #statusShowcaseRow()} enthaelt bewusst je eine Kachel
 * fuer <em>alle vier</em> Werte von {@link TileStatus} -- das ist das
 * Review-Kriterium von S1 ("Screenshot einer Reihe mit allen vier
 * Statuszeichen") -- plus weitere Kacheln, damit horizontales Scrollen und
 * die Chevrons an den Raendern etwas zu tun haben.</p>
 *
 * <p>Name und Autor sind Paket-eigene Freitexte (wie {@code Manifest.name()}),
 * keine Store-Chrome -- deshalb keine Uebersetzungs-Keys, analog zum
 * Klassenkommentar von {@link TileData}.</p>
 */
public final class DemoCatalog {

	private DemoCatalog() {
	}

	/**
	 * 3-5 kuratierte Eintraege fuer das Hero-Karussell auf der Start-Seite
	 * (ROADMAP S8; docs/UI-SPEC.md "3. Start": "3-5 kuratierte Eintraege aus
	 * {@code featured.json}"). {@code featured.json} und der Katalog kommen erst
	 * mit M4 -- bis dahin ist das ein ehrlich beschrifteter Platzhalter: die
	 * Oberflaeche kennzeichnet die Auswahl sichtbar ueber
	 * {@code vitrine.carousel.sample_note} (Charter-Regel 2). Es sind bewusst
	 * dieselben Beispieldaten wie in {@link #statusShowcaseRow()}, kein neuer
	 * erfundener Inhalt.
	 */
	public static List<TileData> heroCarouselRow() {
		List<TileData> all = statusShowcaseRow();
		return List.copyOf(all.subList(0, Math.min(4, all.size())));
	}

	public static List<TileData> statusShowcaseRow() {
		return List.of(
				TileData.withoutImage("demo.klarglas", "Klarglas", "spassglas",
						TilePackageType.THEME, TileStatus.INSTALLED),
				TileData.withoutImage("demo.aurora-hud", "Aurora HUD", "mira",
						TilePackageType.HUD, TileStatus.UPDATE_AVAILABLE),
				TileData.withoutImage("demo.smooth-stone-and-more", "Smooth Stone and a Very Long Name", "wolf",
						TilePackageType.TEXTUREN, TileStatus.NONE),
				// GATE16: "Neustart noetig" nur auf einem Klasse-B-Angebot (Java-Mod)
				// -- vorher stand es hier auf einem Sound-Paket, das hot wirkt.
				TileData.javaModOffer("demo.minimap-mod", "Minimap (Java-Mod)", "kai",
						TilePackageType.HUD, TileStatus.RESTART_REQUIRED),
				TileData.withoutImage("demo.forest-ambience", "Forest Ambience", "kai",
						TilePackageType.SOUND, TileStatus.NONE),
				TileData.withoutImage("demo.pastel-ui", "Pastel UI", "lena",
						TilePackageType.THEME, TileStatus.NONE),
				TileData.withoutImage("demo.compass-hud", "Compass HUD", "mira",
						TilePackageType.HUD, TileStatus.NONE),
				TileData.withoutImage("demo.crisp-lang-de", "Crisp German", "spassglas",
						TilePackageType.SPRACHE, TileStatus.NONE),
				TileData.withoutImage("demo.golden-shaders", "Golden Hour", "ora",
						TilePackageType.SHADER, TileStatus.NONE),
				TileData.withoutImage("demo.tiny-hats", "Tiny Hats", "bee",
						TilePackageType.COSMETICS, TileStatus.NONE),
				TileData.withoutImage("demo.starter-bundle", "Starter Bundle", "spassglas",
						TilePackageType.BUNDLE, TileStatus.NONE));
	}
}
