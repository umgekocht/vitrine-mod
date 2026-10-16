package de.spassglas.vitrine.catalog.github;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Host-Whitelist fuer die GitHub-API (ROADMAP GATE70, docs/UI-SPEC.md:118-120:
 * "Sterne/Downloads aus der GitHub-API (read-only)"). Eigene, schmale
 * Whitelist statt Wiederverwendung von {@code de.spassglas.vitrine.catalog.HostWhitelist}
 * oder {@code de.spassglas.vitrine.catalog.download.DownloadHostWhitelist}:
 * beide gelten fuer einen anderen Zweck (Katalog-Index bzw. Release-Asset-
 * Download) -- wuerde {@code api.github.com} dort mit aufgenommen, duerften
 * auch jene Codepfade ploetzlich diesen Host ansprechen, ohne dass das je
 * gewollt war. Ein neuer externer Host bekommt eine neue, auf genau diesen
 * einen Verwendungszweck zugeschnittene Regel (Charter-Regel 3: Sicherheit vor
 * Bequemlichkeit).
 */
public final class GitHubRepoStatsHostWhitelist {

	/** Der einzige Host, von dem {@link GitHubRepoStatsClient} liest. */
	public static final Set<String> ALLOWED_HOSTS = Set.of("api.github.com");

	/** Produktions-Regel: {@code https} <em>und</em> Host auf {@link #ALLOWED_HOSTS}. */
	public static final Predicate<URI> PRODUCTION = GitHubRepoStatsHostWhitelist::isAllowed;

	private GitHubRepoStatsHostWhitelist() {
	}

	public static boolean isAllowed(URI uri) {
		if (uri == null || uri.getHost() == null) {
			return false;
		}
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			return false;
		}
		return ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
	}
}
