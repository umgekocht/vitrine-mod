package de.spassglas.vitrine.manifest;

import java.util.List;

/**
 * Geparste, gegen {@code vitrine.schema.json} bereits validierte Fassung
 * einer {@code vitrine.json}-Manifestdatei (P1). Reines Datenobjekt --
 * keinerlei Zip-Handling (P2), keine Sicherheitspruefung (P3), keine
 * Signaturpruefung (P4).
 *
 * <p>{@link #capabilities()} und {@link #screenshots()} sind nie {@code null}
 * (leere Liste statt fehlendem Feld), alle uebrigen optionalen Felder sind
 * {@code null}, wenn sie im Manifest fehlten.</p>
 */
public record Manifest(
		int schemaVersion,
		String id,
		String name,
		String version,
		String category,
		String author,
		String description,
		List<String> capabilities,
		String icon,
		List<String> screenshots,
		String homepage,
		String license) {
}
