package de.spassglas.vitrine.hud;

/**
 * Rechnet Anker + Pixel-Versatz + Skalierung eines HUD-Widgets in die
 * konkrete linke obere Bildschirmecke um, an der der (bereits skaliert
 * gedachte) Inhalt beginnt (ROADMAP T5: "Anker + Pixel-Offset + Skalierung
 * anwenden"). Reine Geometrie, kein Minecraft-Bezug -- damit unter
 * {@code HudPlacementTest} ohne laufenden Client pruefbar.
 *
 * <p>Vorzeichen-Konvention (deckt sich mit den Beispielpaketen unter
 * {@code registry/schema/examples/hud/valid/}): der Versatz wird in
 * Bildschirmkoordinaten addiert. Bei einem rechten/unteren Anker ergibt ein
 * negativer Versatz damit einen Innenabstand zur Kante (z. B.
 * {@code top-right}, {@code offset.x = -4} = 4 px links der rechten Kante).</p>
 */
public final class HudPlacement {

	private HudPlacement() {
	}

	/** Linke obere Bildschirmecke, an der der skalierte Widget-Inhalt beginnt. */
	public record Position(int x, int y) {
	}

	/**
	 * @param anchor        Bezugsecke/-kante
	 * @param offset        Pixel-Versatz (unskaliert)
	 * @param contentWidth  unskalierte Inhaltsbreite in Pixeln
	 * @param contentHeight unskalierte Inhaltshoehe in Pixeln
	 * @param scale         Skalierungsfaktor (1.0 = Originalgroesse)
	 * @param screenWidth   Breite der (ggf. GUI-skalierten) Zeichenflaeche
	 * @param screenHeight  Hoehe der (ggf. GUI-skalierten) Zeichenflaeche
	 */
	public static Position resolve(HudAnchor anchor, HudOffset offset, int contentWidth, int contentHeight,
			double scale, int screenWidth, int screenHeight) {
		double scaledWidth = contentWidth * scale;
		double scaledHeight = contentHeight * scale;

		int x = switch (anchor.horizontalEdge()) {
			case LEFT -> offset.x();
			case CENTER -> (int) Math.round((screenWidth - scaledWidth) / 2.0) + offset.x();
			case RIGHT -> (int) Math.round(screenWidth - scaledWidth) + offset.x();
		};
		int y = switch (anchor.verticalEdge()) {
			case TOP -> offset.y();
			case CENTER -> (int) Math.round((screenHeight - scaledHeight) / 2.0) + offset.y();
			case BOTTOM -> (int) Math.round(screenHeight - scaledHeight) + offset.y();
		};
		return new Position(x, y);
	}
}
