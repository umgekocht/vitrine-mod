package de.spassglas.vitrine.ui.nav;

/**
 * Verbindet die Breakpoint-Entscheidung ({@link NavRailLayout}) mit dem
 * Auf/Zu-Zustand der Ueberlagerung ({@link NavRailOverlayState}) zu einem
 * einzigen, pro {@code StoreScreen}-Instanz langlebigen Modell.
 *
 * <p>Reine Logik, keine Minecraft-Klassen -- {@code StoreScreen} ruft
 * {@link #onWidthChanged(int)} bei jedem {@code resize}/{@code init} auf und
 * liest danach {@link #layout()} und {@link #isRailVisible()} fuer den
 * tatsaechlichen Aufbau der Widgets.</p>
 */
public final class NavRailController {

	private NavRailLayout layout;
	private final NavRailOverlayState overlay = new NavRailOverlayState();

	public NavRailController() {
		this(NavRailLayout.EXPANDED);
	}

	public NavRailController(NavRailLayout initialLayout) {
		this.layout = initialLayout;
	}

	public NavRailLayout layout() {
		return layout;
	}

	/** Ob die Ueberlagerung (nur relevant bei {@link NavRailLayout#OVERLAY}) gerade offen ist. */
	public boolean isOverlayOpen() {
		return layout == NavRailLayout.OVERLAY && overlay.isOpen();
	}

	/** Ob die Nav-Rail (dauerhaft oder als geoeffnete Ueberlagerung) gerade sichtbar ist. */
	public boolean isRailVisible() {
		return layout != NavRailLayout.OVERLAY || overlay.isOpen();
	}

	/** Ob die Nav-Rail neben dem Inhalt eingebettet ist (statt darueber zu schweben). */
	public boolean isDocked() {
		return layout != NavRailLayout.OVERLAY;
	}

	/**
	 * Neu bewertet den Breakpoint fuer eine neue Fensterbreite. Verlaesst das
	 * Layout dabei den Ueberlagerungs-Zustand, wird die Ueberlagerung
	 * automatisch geschlossen -- sie ergibt bei ausgeklappter/kompakter
	 * Nav-Rail keinen Sinn mehr.
	 */
	public void onWidthChanged(int windowWidth) {
		NavRailLayout next = NavRailLayout.forWidth(windowWidth);
		if (next != layout) {
			layout = next;
			if (layout != NavRailLayout.OVERLAY) {
				overlay.close();
			}
		}
	}

	/** Oeffnet/schliesst die Ueberlagerung; wirkungslos ausserhalb von {@link NavRailLayout#OVERLAY}. */
	public void toggleOverlay() {
		if (layout == NavRailLayout.OVERLAY) {
			overlay.toggle();
		}
	}

	/** Schliesst die Ueberlagerung, z.B. wenn ESC gedrueckt wird. */
	public void closeOverlay() {
		overlay.close();
	}
}
