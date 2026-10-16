package de.spassglas.vitrine.catalog;

import java.util.Objects;
import java.util.Optional;

/**
 * Typisiertes Ergebnis eines Katalog-Abrufs -- nie eine Exception nach aussen
 * (ROADMAP K2). Die drei {@link Availability}-Werte bedienen direkt die S7-Seam
 * {@code CatalogAvailability}: {@code ONLINE} traegt die geladene Nutzlast,
 * {@code NO_NETWORK} und {@code CATALOG_BROKEN} tragen eine Klartext-Meldung
 * (bereits geloggt).
 *
 * @param <T> Typ der Nutzlast bei {@code ONLINE} (z. B. {@link IndexPage}).
 */
public final class CatalogResult<T> {

	/** Deckungsgleich mit {@code de.spassglas.vitrine.ui.state.CatalogAvailability.State} (S7). */
	public enum Availability {
		ONLINE,
		NO_NETWORK,
		CATALOG_BROKEN
	}

	private final Availability availability;
	private final T value;
	private final String message;

	private CatalogResult(Availability availability, T value, String message) {
		this.availability = availability;
		this.value = value;
		this.message = message;
	}

	public static <T> CatalogResult<T> online(T value) {
		return new CatalogResult<>(Availability.ONLINE, Objects.requireNonNull(value, "value"), null);
	}

	public static <T> CatalogResult<T> noNetwork(String message) {
		return new CatalogResult<>(Availability.NO_NETWORK, null, Objects.requireNonNull(message, "message"));
	}

	public static <T> CatalogResult<T> catalogBroken(String message) {
		return new CatalogResult<>(Availability.CATALOG_BROKEN, null, Objects.requireNonNull(message, "message"));
	}

	public Availability availability() {
		return availability;
	}

	public boolean isOnline() {
		return availability == Availability.ONLINE;
	}

	/** Nutzlast bei {@code ONLINE}, sonst leer. */
	public Optional<T> value() {
		return Optional.ofNullable(value);
	}

	/** Klartext-Meldung bei einem Fehlzustand, sonst leer. */
	public Optional<String> message() {
		return Optional.ofNullable(message);
	}

	@Override
	public String toString() {
		return "CatalogResult[" + availability + (value != null ? ", value=" + value : "")
				+ (message != null ? ", message=" + message : "") + "]";
	}
}
