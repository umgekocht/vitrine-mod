package de.spassglas.vitrine.hud;

import java.util.List;

/**
 * Alle HUD-Layouts eines bereits validierten, aktiven {@code .vpkg}-Pakets
 * (ROADMAP T5). Erzeugt von {@link HudPackageLoader}, der zuvor die
 * M1-Kette durchsetzt (Zip-Sicherheit + Fähigkeit {@code hud}). Ein Aufrufer
 * bekommt ein {@code HudPackage} nur in die Hand, wenn das Paket diese
 * Prüfungen bestanden hat -- roher Dateiinhalt wird nie direkt gerendert.
 *
 * @param packageId Id des Pakets aus dessen Manifest (nur für Logging/Diagnose)
 * @param layouts   je {@code hud/*.json}-Eintrag ein geparstes {@link HudLayout},
 *                  in alphabetischer Reihenfolge der Eintragsnamen
 */
public record HudPackage(String packageId, List<Layout> layouts) {

	public HudPackage {
		layouts = List.copyOf(layouts);
	}

	/** Ein einzelnes HUD-Layout samt seinem Herkunfts-Eintragsnamen im Archiv. */
	public record Layout(String entryName, HudLayout layout) {
	}

	/** Alle Widgets aller Layouts dieses Pakets, in Lade-/Zeichenreihenfolge. */
	public List<HudWidget> allWidgets() {
		return layouts.stream().flatMap(entry -> entry.layout().widgets().stream()).toList();
	}
}
