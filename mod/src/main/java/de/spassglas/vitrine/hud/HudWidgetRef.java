package de.spassglas.vitrine.hud;

import java.util.Objects;

/**
 * Ein aktives HUD-Widget zusammen mit der Id des Pakets, aus dem es stammt
 * (ROADMAP T6). Der In-Game-Editor braucht beides: das Widget selbst, um es
 * darzustellen, und die Paket-Id, um eine {@link HudWidgetOverride} unter dem
 * richtigen {@code (packId, widgetId)}-Schluessel abzulegen.
 *
 * @param packId Manifest-Id des Pakets (Schluessel in {@link ActiveHudRegistry})
 * @param widget das unveraenderte Paket-Widget (Paket-Layout, ohne Overrides)
 */
public record HudWidgetRef(String packId, HudWidget widget) {

	public HudWidgetRef {
		Objects.requireNonNull(packId, "packId");
		Objects.requireNonNull(widget, "widget");
	}
}
