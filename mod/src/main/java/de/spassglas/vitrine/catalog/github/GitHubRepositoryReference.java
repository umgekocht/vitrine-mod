package de.spassglas.vitrine.catalog.github;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eigentuemer+Name eines GitHub-Repos, aus einer beliebigen Autoren-URL
 * herausgeloest (ROADMAP GATE70, docs/UI-SPEC.md:118-120). Quelle der rohen
 * URL ist je nach Angebot entweder der Registry-Eintrag ({@code
 * RegistryPackageEntry#repository()}, Pflichtfeld) oder -- fuer ein nur
 * lokal vorliegendes Paket ohne Registry-Metadaten -- das optionale
 * Manifest-Feld {@code homepage}. Beide sind laut Schema eine beliebige
 * {@code https}-URL, nicht zwingend GitHub; {@link #parse} liefert deshalb
 * ehrlich {@link Optional#empty()}, wenn die URL kein GitHub-Repo nennt --
 * nie eine erfundene Vermutung (Charter-Regel 2).
 */
public record GitHubRepositoryReference(String owner, String repo) {

	private static final Pattern REPO_PATH = Pattern.compile("^/([^/]+)/([^/]+?)(?:\\.git)?/?$");

	public GitHubRepositoryReference {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(repo, "repo");
	}

	/**
	 * @param repositoryUrl eine beliebige Zeichenkette (meist eine https-URL); {@code null}/leer/kein
	 *                      gueltiges GitHub-Repo liefert {@link Optional#empty()}
	 */
	public static Optional<GitHubRepositoryReference> parse(String repositoryUrl) {
		if (repositoryUrl == null || repositoryUrl.isBlank()) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = URI.create(repositoryUrl.trim());
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		if (uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())
				|| !"github.com".equalsIgnoreCase(uri.getHost())) {
			return Optional.empty();
		}
		String path = uri.getPath();
		if (path == null) {
			return Optional.empty();
		}
		Matcher matcher = REPO_PATH.matcher(path);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String owner = matcher.group(1);
		String repo = matcher.group(2);
		if (owner.isBlank() || repo.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new GitHubRepositoryReference(owner, repo));
	}
}
