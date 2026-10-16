package de.spassglas.vitrine.hud;

/**
 * Ein einzelnes, rein deklaratives HUD-Widget (ROADMAP T5). Geschlossene
 * Menge von vier Typen -- deckt sich mit {@code $defs/widget} in
 * {@code registry/schema/hud.schema.json}. Reines Datenmodell: kein Rendering,
 * kein Minecraft-Bezug (das leistet
 * {@code de.spassglas.vitrine.hud.client.HudWidgetRenderer}).
 *
 * <p>Gemeinsame Felder ({@link #id()}, {@link #anchor()}, {@link #offset()},
 * {@link #scale()}, {@link #visibility()}) mit den im Schema-Kopf
 * dokumentierten Defaults: fehlendes {@code offset} = {@link HudOffset#ZERO},
 * fehlendes {@code scale} = {@code 1.0}, fehlendes {@code visibility} =
 * {@link HudVisibility.Always}.</p>
 */
public sealed interface HudWidget
		permits HudWidget.Text, HudWidget.Icon, HudWidget.Bar, HudWidget.List {

	/** Dokumentierter Default-Skalierungsfaktor, wenn ein Widget kein {@code scale} angibt. */
	double DEFAULT_SCALE = 1.0;

	String id();

	HudAnchor anchor();

	HudOffset offset();

	double scale();

	HudVisibility visibility();

	/** Der {@code type}-Bezeichner im JSON ({@code "text"}, {@code "icon"}, {@code "bar"}, {@code "list"}). */
	String typeId();

	/**
	 * Eine Kopie dieses Widgets mit ersetztem Anker, Versatz und ersetzter
	 * Sichtbarkeit -- alle typ-eigenen Felder bleiben gleich. Damit legt
	 * {@link HudOverrideResolver} eine {@link HudWidgetOverride} ueber ein
	 * Paket-Widget, ohne das Paket-{@link HudLayout} zu mutieren (ROADMAP T6).
	 */
	HudWidget withPlacement(HudAnchor anchor, HudOffset offset, HudVisibility visibility);

	/** Textzeile, statisch oder dynamisch (z. B. FPS-Anzeige). */
	record Text(String id, HudAnchor anchor, HudOffset offset, double scale,
			HudVisibility visibility, HudTextContent content) implements HudWidget {
		@Override
		public String typeId() {
			return "text";
		}

		@Override
		public HudWidget withPlacement(HudAnchor newAnchor, HudOffset newOffset, HudVisibility newVisibility) {
			return new Text(id, newAnchor, newOffset, scale, newVisibility, content);
		}
	}

	/**
	 * Referenz auf eine PNG-Textur aus dem Paket (relativer Pfad).
	 *
	 * @param width  Zielbreite in unskalierten Pixeln, oder {@code null} wenn
	 *               im JSON weggelassen (Renderer nimmt die native Bildbreite).
	 * @param height wie {@code width} fuer die Hoehe.
	 */
	record Icon(String id, HudAnchor anchor, HudOffset offset, double scale,
			HudVisibility visibility, String texture, Integer width, Integer height) implements HudWidget {
		@Override
		public String typeId() {
			return "icon";
		}

		@Override
		public HudWidget withPlacement(HudAnchor newAnchor, HudOffset newOffset, HudVisibility newVisibility) {
			return new Icon(id, newAnchor, newOffset, scale, newVisibility, texture, width, height);
		}
	}

	/**
	 * Balken/Fortschrittsanzeige fuer einen dynamischen Wert, normiert ueber
	 * {@code min}/{@code max}.
	 *
	 * @param min   Wert eines leeren Balkens; Default {@code 0.0} wenn weggelassen.
	 * @param max   Wert eines vollen Balkens; Default {@code 1.0} wenn weggelassen.
	 * @param width Balkenlaenge in unskalierten Pixeln, oder {@code null}.
	 * @param height Balkenhoehe in unskalierten Pixeln, oder {@code null}.
	 * @param fillColorArgb Fuellfarbe als ARGB-int (aus dem {@code #rrggbb} des
	 *               Pakets, Alpha auf voll gesetzt), oder {@code null} wenn das
	 *               Paket keine Farbe angibt -- dann nimmt der Renderer einen
	 *               Theme-Token.
	 */
	record Bar(String id, HudAnchor anchor, HudOffset offset, double scale,
			HudVisibility visibility, HudDataSource source, double min, double max,
			Integer width, Integer height, Integer fillColorArgb) implements HudWidget {

		/** Dokumentierter Default fuer {@code min}, wenn im JSON weggelassen. */
		public static final double DEFAULT_MIN = 0.0;
		/** Dokumentierter Default fuer {@code max}, wenn im JSON weggelassen. */
		public static final double DEFAULT_MAX = 1.0;

		@Override
		public String typeId() {
			return "bar";
		}

		@Override
		public HudWidget withPlacement(HudAnchor newAnchor, HudOffset newOffset, HudVisibility newVisibility) {
			return new Bar(id, newAnchor, newOffset, scale, newVisibility, source, min, max, width, height,
					fillColorArgb);
		}

		/** Fuellgrad 0..1 fuer {@code value}, an den {@code [min,max]}-Bereich geklemmt. */
		public double fillFraction(double value) {
			if (max == min) {
				return 0.0;
			}
			double raw = (value - min) / (max - min);
			return Math.max(0.0, Math.min(1.0, raw));
		}
	}

	/** Mehrere {@link HudTextContent}-Eintraege untereinander (z. B. Koordinatenliste). */
	record List(String id, HudAnchor anchor, HudOffset offset, double scale,
			HudVisibility visibility, java.util.List<HudTextContent> entries) implements HudWidget {
		public List {
			entries = java.util.List.copyOf(entries);
		}

		@Override
		public String typeId() {
			return "list";
		}

		@Override
		public HudWidget withPlacement(HudAnchor newAnchor, HudOffset newOffset, HudVisibility newVisibility) {
			return new List(id, newAnchor, newOffset, scale, newVisibility, entries);
		}
	}
}
