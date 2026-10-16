package de.spassglas.vitrine.ui.tile;

import java.util.Objects;

/**
 * Ein einzelner Kachel-Datensatz, unabhaengig von seiner Quelle (ROADMAP S1
 * Auftrag: "baue die Kachel-/Reihen-Logik so, dass sie eine Liste von
 * Kachel-Datensaetzen ... unabhaengig von der Quelle entgegennimmt"). Heute
 * (vor S2 "Lokaler Ordner") gibt es noch keinen echten Erzeuger -- Tests und
 * die Demo-Reihe in {@code StoreScreen} verdrahten Instanzen fest.
 *
 * <p>{@code name} und {@code author} sind Paket-eigene, freie Inhalte (wie
 * {@code Manifest.name()}/{@code Manifest.author()}) -- keine Store-Chrome-
 * Texte, deshalb keine Uebersetzungs-Keys noetig (der F3-Scanner prueft nur
 * literale Text-Fabrik-Aufrufe mit woertlichem String-Argument, keine aus
 * Variablen gebauten Texte).</p>
 *
 * <p>{@code javaMod} markiert ein Klasse-B-Angebot (ROADMAP J1): ein
 * {@code .jar}-Mod, der einen Neustart braucht und nie sofort aktiv wird
 * (Charter-Regel 2). {@code de.spassglas.vitrine.ui.classb.JavaModOfferGate}
 * blendet solche Eintraege ohne den Opt-in-Schalter ueberall aus; sichtbare
 * Klasse-B-Kacheln tragen {@link TileStatus#RESTART_REQUIRED} als deutliches
 * "Neustart noetig"-Zeichen. Fuer Klasse-A-Angebote ist {@code javaMod} immer
 * {@code false}.</p>
 */
public record TileData(String id, String name, String author, TilePackageType type, TileStatus status,
		String imagePath, boolean javaMod) {

	public TileData {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(author, "author");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(status, "status");
		// GATE16 / Charter-Regel 2: ein Klasse-A-Paket wird ohne Neustart
		// wirksam -- es gibt keinen ehrlichen Weg, dass eine solche Kachel
		// "Neustart noetig" zeigt. Vorher tat es die Demo-Reihe auf einem
		// Sound-Paket. Jetzt kann diese Kachel gar nicht erst entstehen.
		if (status == TileStatus.RESTART_REQUIRED && !javaMod) {
			throw new IllegalArgumentException("Kachel '" + id + "': 'Neustart noetig' gibt es nur fuer "
					+ "Klasse-B-Angebote (Java-Mods), nicht fuer Klasse-A-Pakete.");
		}
	}

	/** Bequemlichkeits-Konstruktor fuer Klasse-A-Kacheln ohne eigenes Vorschaubild (Platzhalter, siehe UI-SPEC "4. Kachel"). */
	public static TileData withoutImage(String id, String name, String author, TilePackageType type,
			TileStatus status) {
		return new TileData(id, name, author, type, status, null, false);
	}

	/**
	 * Klasse-B-Angebot (ROADMAP J1) ohne eigenes Vorschaubild: {@code javaMod}
	 * ist gesetzt. {@code status} sollte {@link TileStatus#RESTART_REQUIRED}
	 * sein, damit eine sichtbare Kachel ehrlich als "Neustart noetig"
	 * gekennzeichnet ist (Charter-Regel 2).
	 */
	public static TileData javaModOffer(String id, String name, String author, TilePackageType type,
			TileStatus status) {
		return new TileData(id, name, author, type, status, null, true);
	}

	/** Ob ein eigenes Vorschaubild vorhanden ist, oder der Platzhalter mit Typ-Symbol gezeichnet werden muss. */
	public boolean hasImage() {
		return imagePath != null && !imagePath.isBlank();
	}
}
