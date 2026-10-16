package de.spassglas.vitrine.catalog.github;

import java.util.Objects;
import java.util.Optional;

/**
 * Read-only-Kennzahlen eines Autoren-Repos von {@code api.github.com} (ROADMAP
 * GATE70, docs/UI-SPEC.md:118-120: "Sterne/Downloads aus der GitHub-API
 * (read-only)"). Beide Felder sind einzeln optional und unabhaengig
 * voneinander -- ein Fehlschlag beim Zaehlen der Downloads darf die (bereits
 * erfolgreich gelesene) Sternezahl nicht mit wegwerfen, und umgekehrt. Fehlt
 * ein Wert, zeigt die Detailseite ehrlich "keine Angaben" statt einer
 * erfundenen Zahl (Charter-Regel 2).
 *
 * @param stargazerCount Sternezahl des Repos ({@code stargazers_count} aus
 *                        {@code GET /repos/<owner>/<repo>}), oder leer, wenn
 *                        nicht ermittelbar.
 * @param downloadCount   Summe aus {@code download_count} ueber alle Release-
 *                        Assets der ersten Release-Seite ({@code GET
 *                        /repos/<owner>/<repo>/releases}), oder leer, wenn
 *                        nicht ermittelbar. {@code 0}, wenn das Repo bekannt,
 *                        aber (noch) ohne Releases ist -- das ist ein echter,
 *                        bekannter Wert, keine Unbekannte.
 */
public record GitHubRepoStats(Optional<Long> stargazerCount, Optional<Long> downloadCount) {

	/** Weder Sterne noch Downloads bekannt -- der Normalfall fuer ein Paket ohne (erkennbares) GitHub-Repo. */
	public static final GitHubRepoStats UNKNOWN = new GitHubRepoStats(Optional.empty(), Optional.empty());

	public GitHubRepoStats {
		Objects.requireNonNull(stargazerCount, "stargazerCount");
		Objects.requireNonNull(downloadCount, "downloadCount");
	}
}
