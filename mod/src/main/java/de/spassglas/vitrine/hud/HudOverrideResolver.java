package de.spassglas.vitrine.hud;

/**
 * Legt eine {@link HudWidgetOverride} (Nutzer-Anpassung aus dem In-Game-Editor,
 * ROADMAP T6) <em>ueber</em> ein Paket-Widget und liefert das effektive
 * Widget zurueck. Reine Geometrie/Datenumformung, kein Minecraft-Bezug --
 * damit ohne laufenden Client pruefbar.
 *
 * <p>Ohne Anpassung (oder mit einer leeren) kommt exakt dasselbe Widget
 * zurueck -- das bisherige T5-Verhalten bleibt unberuehrt. Das Paket-
 * {@link HudLayout} wird nie mutiert: {@link HudWidget#withPlacement} erzeugt
 * eine Kopie.</p>
 */
public final class HudOverrideResolver {

	private HudOverrideResolver() {
	}

	/**
	 * @param widget   das unveraenderte Paket-Widget
	 * @param override die Nutzer-Anpassung, oder {@code null} wenn es keine gibt
	 * @return das effektive Widget (Paket-Layout + Anpassung)
	 */
	public static HudWidget resolve(HudWidget widget, HudWidgetOverride override) {
		if (override == null || override.isEmpty()) {
			return widget;
		}
		HudAnchor anchor = override.anchor() != null ? override.anchor() : widget.anchor();
		HudOffset offset = override.offset() != null ? override.offset() : widget.offset();
		HudVisibility visibility = widget.visibility();
		if (override.visible() != null) {
			visibility = override.visible() ? HudVisibility.Always.INSTANCE : HudVisibility.Hidden.INSTANCE;
		}
		return widget.withPlacement(anchor, offset, visibility);
	}
}
