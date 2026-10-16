package de.spassglas.vitrine.hud;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bildschirmecke/-kante, relativ zu der der Pixel-Versatz eines HUD-Widgets
 * gemessen wird (ROADMAP T5). Feste, geschlossene Aufzaehlung -- deckt sich
 * eins zu eins mit {@code $defs/anchor} in {@code registry/schema/hud.schema.json}
 * (Charter-Regel 3: "Klasse A fuehrt keinen Code aus" -- keine freien
 * Bildschirmkoordinaten, nur diese Enum plus ein Pixel-Offset).
 */
public enum HudAnchor {

	TOP_LEFT("top-left", HorizontalEdge.LEFT, VerticalEdge.TOP),
	TOP_RIGHT("top-right", HorizontalEdge.RIGHT, VerticalEdge.TOP),
	BOTTOM_LEFT("bottom-left", HorizontalEdge.LEFT, VerticalEdge.BOTTOM),
	BOTTOM_RIGHT("bottom-right", HorizontalEdge.RIGHT, VerticalEdge.BOTTOM),
	TOP_CENTER("top-center", HorizontalEdge.CENTER, VerticalEdge.TOP),
	BOTTOM_CENTER("bottom-center", HorizontalEdge.CENTER, VerticalEdge.BOTTOM),
	CENTER("center", HorizontalEdge.CENTER, VerticalEdge.CENTER);

	/** Waagerechte Bezugskante eines Ankers. */
	public enum HorizontalEdge { LEFT, CENTER, RIGHT }

	/** Senkrechte Bezugskante eines Ankers. */
	public enum VerticalEdge { TOP, CENTER, BOTTOM }

	private static final Map<String, HudAnchor> BY_ID = buildIndex();

	private final String id;
	private final HorizontalEdge horizontalEdge;
	private final VerticalEdge verticalEdge;

	HudAnchor(String id, HorizontalEdge horizontalEdge, VerticalEdge verticalEdge) {
		this.id = id;
		this.horizontalEdge = horizontalEdge;
		this.verticalEdge = verticalEdge;
	}

	/** Der Bezeichner wie im HUD-JSON ({@code "top-left"} usw.). */
	public String id() {
		return id;
	}

	public HorizontalEdge horizontalEdge() {
		return horizontalEdge;
	}

	public VerticalEdge verticalEdge() {
		return verticalEdge;
	}

	/** Loest einen JSON-Bezeichner auf, oder {@link Optional#empty()} wenn unbekannt. */
	public static Optional<HudAnchor> byId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	private static Map<String, HudAnchor> buildIndex() {
		Map<String, HudAnchor> index = new LinkedHashMap<>();
		for (HudAnchor anchor : values()) {
			index.put(anchor.id, anchor);
		}
		return index;
	}
}
