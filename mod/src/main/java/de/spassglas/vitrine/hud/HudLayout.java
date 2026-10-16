package de.spassglas.vitrine.hud;

import java.util.List;

/**
 * Ein vollstaendiges, bereits gegen {@code registry/schema/hud.schema.json}
 * validiertes HUD-Datenmodell -- der Inhalt genau einer {@code hud/*.json}
 * eines HUD-Pakets (ROADMAP T5). Erzeugt von {@link HudLayoutParser}.
 *
 * <p>{@code widgets} ist in Zeichenreihenfolge (spaeter gezeichnete Widgets
 * liegen ueber frueheren) und enthaelt keine doppelten
 * {@link HudWidget#id() Ids} -- das prueft {@link HudLayoutParser} als
 * Laufzeitregel, die JSON Schema fuer ein Unterfeld eines Array-Elements
 * nicht ausdruecken kann.</p>
 */
public record HudLayout(int schemaVersion, List<HudWidget> widgets) {

	public HudLayout {
		widgets = List.copyOf(widgets);
	}
}
