package de.spassglas.vitrine.hud;

/**
 * Sichtbarkeitsbedingung eines HUD-Widgets (Schema: {@code $defs/visibility}).
 * Feste, aufzaehlbare Menge -- keine Ausdruckssprache. Fehlt {@code visibility}
 * an einem Widget, gilt {@link Always} (im Schema-Kopf dokumentierter Default).
 *
 * <p>{@link Hidden} ist die einzige Bedingung, die <em>nicht</em> aus dem
 * Schema stammt: {@link HudLayoutParser} akzeptiert sie nie aus Paket-JSON.
 * Sie entsteht ausschliesslich, wenn ein Nutzer ein Widget im In-Game-Editor
 * (ROADMAP T6) abschaltet -- die Anpassung liegt in {@link HudLayoutOverrides},
 * das Paket-Layout bleibt unberuehrt.</p>
 */
public sealed interface HudVisibility
		permits HudVisibility.Always, HudVisibility.InDimension,
		HudVisibility.DebugScreen, HudVisibility.OutsideMenu, HudVisibility.Hidden {

	/** Ob das Widget im gegebenen Spielzustand gezeichnet werden soll. */
	boolean isVisible(HudGameState state);

	/** Der {@code type}-Bezeichner dieser Bedingung im JSON. */
	String typeId();

	/** {@code {"type":"always"}} -- immer sichtbar. */
	record Always() implements HudVisibility {
		public static final Always INSTANCE = new Always();

		@Override
		public boolean isVisible(HudGameState state) {
			return true;
		}

		@Override
		public String typeId() {
			return "always";
		}
	}

	/** {@code {"type":"dimension","dimension":"minecraft:overworld"}}. */
	record InDimension(String dimensionId) implements HudVisibility {
		@Override
		public boolean isVisible(HudGameState state) {
			return dimensionId.equals(state.dimensionId());
		}

		@Override
		public String typeId() {
			return "dimension";
		}
	}

	/** {@code {"type":"debugScreen","debugScreen":"on"|"off"}}. */
	record DebugScreen(boolean requiredShown) implements HudVisibility {
		@Override
		public boolean isVisible(HudGameState state) {
			return state.debugScreenShown() == requiredShown;
		}

		@Override
		public String typeId() {
			return "debugScreen";
		}
	}

	/** {@code {"type":"outsideMenu"}} -- nur sichtbar, wenn kein Screen offen ist. */
	record OutsideMenu() implements HudVisibility {
		public static final OutsideMenu INSTANCE = new OutsideMenu();

		@Override
		public boolean isVisible(HudGameState state) {
			return !state.anyScreenOpen();
		}

		@Override
		public String typeId() {
			return "outsideMenu";
		}
	}

	/**
	 * Nie sichtbar. Kein Schema-Wert -- entsteht nur, wenn ein Nutzer ein
	 * Widget im In-Game-Editor (ROADMAP T6) abschaltet (siehe
	 * {@link HudLayoutOverrides}).
	 */
	record Hidden() implements HudVisibility {
		public static final Hidden INSTANCE = new Hidden();

		@Override
		public boolean isVisible(HudGameState state) {
			return false;
		}

		@Override
		public String typeId() {
			return "hidden";
		}
	}
}
