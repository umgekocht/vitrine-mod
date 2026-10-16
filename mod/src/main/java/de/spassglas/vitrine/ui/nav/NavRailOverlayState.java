package de.spassglas.vitrine.ui.nav;

/**
 * Ob die Nav-Rail-Ueberlagerung (schmales Fenster, {@link NavRailLayout#OVERLAY})
 * gerade eingeblendet ist. Reines Zustandsmodell ohne Minecraft-Abhaengigkeit,
 * damit es ohne laufenden Client testbar ist.
 */
public final class NavRailOverlayState {

	private boolean open;

	public boolean isOpen() {
		return open;
	}

	public void open() {
		open = true;
	}

	public void close() {
		open = false;
	}

	public void toggle() {
		open = !open;
	}
}
