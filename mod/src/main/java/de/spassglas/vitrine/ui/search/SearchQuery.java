package de.spassglas.vitrine.ui.search;

import de.spassglas.vitrine.ui.tile.TilePackageType;

import java.util.Objects;
import java.util.Set;

/**
 * Eine vollstaendige Suchanfrage an das {@link SearchModel} (ROADMAP S6):
 * Freitext, Kategorie-Einschraenkung, der Filter „ohne Neustart" und die
 * gewuenschte Sortierung. Reiner Wert, client-frei, unveraenderlich.
 *
 * <ul>
 *   <li>{@code text} -- Freitext; leer/blank heisst „keine Textsuche".</li>
 *   <li>{@code categories} -- leere Menge heisst „alle Kategorien" (Nav-Rail
 *       „Start"); sonst bleiben nur Kacheln dieser Typen.</li>
 *   <li>{@code hideRestartRequired} -- {@code true} blendet Klasse-B-Angebote
 *       ({@code TileStatus.RESTART_REQUIRED}) aus. Default ist {@code false}
 *       (alles sichtbar, ehrlich beschriftet).</li>
 *   <li>{@code sort} -- nie {@code null}.</li>
 * </ul>
 */
public record SearchQuery(String text, Set<TilePackageType> categories, boolean hideRestartRequired, SearchSort sort) {

	public SearchQuery {
		text = text == null ? "" : text;
		categories = categories == null ? Set.of() : Set.copyOf(categories);
		Objects.requireNonNull(sort, "sort");
	}

	/** Anfrage ohne jede Einschraenkung, Name-A-Z sortiert. */
	public static SearchQuery all() {
		return new SearchQuery("", Set.of(), false, SearchSort.NAME_ASC);
	}

	/** Ob ein nicht-leerer Freitext gesetzt ist. */
	public boolean hasText() {
		return !text.isBlank();
	}

	public SearchQuery withText(String newText) {
		return new SearchQuery(newText, categories, hideRestartRequired, sort);
	}

	public SearchQuery withCategories(Set<TilePackageType> newCategories) {
		return new SearchQuery(text, newCategories, hideRestartRequired, sort);
	}

	public SearchQuery withHideRestartRequired(boolean newHideRestartRequired) {
		return new SearchQuery(text, categories, newHideRestartRequired, sort);
	}

	public SearchQuery withSort(SearchSort newSort) {
		return new SearchQuery(text, categories, hideRestartRequired, newSort);
	}
}
