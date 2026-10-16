package de.spassglas.vitrine.catalog;

/**
 * Ein Paket-Kurzeintrag einer Index-Seite (Feld {@code packages[]} aus
 * {@code registry-index-page.schema.json}). Genug fuer die Kachelansicht des
 * Stores; die Vollansicht laedt spaeter {@code registry-package.schema.json}.
 *
 * <p>{@code icon} und {@code shortDescription} sind im Schema optional und
 * koennen {@code null} sein.</p>
 *
 * <p>{@code javaMod} markiert ein Klasse-B-Angebot (ROADMAP J1): ein
 * {@code .jar}-Mod, der einen Neustart braucht. Das aktuelle Registry-Schema
 * kennt nur die sieben Klasse-A-Kategorien und hat noch <em>kein</em> Feld
 * dafuer -- die passende Schema-Erweiterung (z. B. ein {@code contentClass}-
 * Feld) ist eine spaetere {@code coder-tooling}-Zeile. Bis dahin liest
 * {@link IndexPage#fromJson} ein optionales {@code javaMod}-Flag als bewusst
 * konservativen Seam: fehlt die Angabe, gilt das Angebot als Klasse A
 * ({@code false}).</p>
 */
public record IndexPackage(
		String id,
		String name,
		String author,
		String category,
		String latestVersion,
		String icon,
		String shortDescription,
		String updatedAt,
		boolean javaMod) {
}
