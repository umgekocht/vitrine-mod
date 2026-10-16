package de.spassglas.vitrine.catalog;

import java.util.Objects;

/**
 * Ein einzelner kuratierter Eintrag aus {@code featured.json}
 * (ROADMAP K1/X2, {@code registry-featured.schema.json}): verweist per
 * {@code id} auf ein Paket aus der Index-Seite, {@code order} bestimmt die
 * Sortierung (aufsteigend, kleinere Zahl zuerst), {@code note} ist ein
 * optionaler Kurationstext.
 */
public record FeaturedItem(String id, int order, String note) {

	public FeaturedItem {
		Objects.requireNonNull(id, "id");
	}
}
