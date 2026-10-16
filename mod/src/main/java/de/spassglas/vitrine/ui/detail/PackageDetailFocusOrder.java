package de.spassglas.vitrine.ui.detail;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Berechnet die Tab-Reihenfolge der Store-Detailseite in Leserichtung
 * (docs/UI-SPEC.md "Barrierefreiheit"), reine Funktion ohne
 * Minecraft-Abhaengigkeit -- {@code PackageDetailScreen} fuegt seine Widgets
 * in genau dieser Reihenfolge per {@code addDrawableChild} hinzu, damit
 * Minecrafts eingebauter Tab-Zyklus (Einfuegereihenfolge) damit
 * uebereinstimmt. Aufbau wie {@code StoreFocusOrder} fuer das Store-Geruest.
 *
 * <p>Nur tatsaechlich vorhandene, bedienbare Elemente kommen in die Liste:
 * die Screenshot-Galerie nur bei echten Screenshots, "Mehr anzeigen" nur bei
 * langer Beschreibung, das Changelog-Aufklappen nur wenn ein Changelog
 * vorliegt, die Autor-Reihe nur wenn es weitere Pakete gibt.</p>
 */
public final class PackageDetailFocusOrder {

	private PackageDetailFocusOrder() {
	}

	public static List<PackageDetailFocusTarget> compute(PackageDetailModel model) {
		Objects.requireNonNull(model, "model");

		List<PackageDetailFocusTarget> order = new ArrayList<>();
		order.add(PackageDetailFocusTarget.BACK);
		order.add(PackageDetailFocusTarget.ACTION_BUTTON);
		if (model.hasScreenshots()) {
			order.add(PackageDetailFocusTarget.SCREENSHOT_GALLERY);
		}
		if (model.descriptionNeedsCollapse()) {
			order.add(PackageDetailFocusTarget.DESCRIPTION_TOGGLE);
		}
		if (model.hasCopyableChecksum()) {
			order.add(PackageDetailFocusTarget.COPY_CHECKSUM);
		}
		if (model.hasChangelog()) {
			order.add(PackageDetailFocusTarget.CHANGELOG_TOGGLE);
		}
		if (model.hasMoreFromAuthor()) {
			order.add(PackageDetailFocusTarget.MORE_FROM_AUTHOR);
		}
		return List.copyOf(order);
	}
}
