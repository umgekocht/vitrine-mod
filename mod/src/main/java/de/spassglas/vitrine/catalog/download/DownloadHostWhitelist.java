package de.spassglas.vitrine.catalog.download;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Host-Whitelist fuer den Paket-Download (ROADMAP K3, PLAN.md &sect;6: "Nur
 * HTTPS, feste Hosts, keine Weiterleitung auf andere Hosts"). Getrennt von
 * {@code de.spassglas.vitrine.catalog.HostWhitelist}: der Katalog-<em>Index</em>
 * liegt auf genau einem GitHub-Pages-Host, die Paket-<em>Artefakte</em> liegen
 * als GitHub-Release-Assets und werden von anderen Hosts ausgeliefert.
 *
 * <p><b>Warum genau diese drei Hosts:</b></p>
 * <ul>
 *   <li>{@code github.com} -- der kanonische Link, der im Registry-Index unter
 *       {@code versions[].vpkgUrl} steht
 *       ({@code https://github.com/<owner>/<repo>/releases/download/<tag>/<datei>.vpkg}).</li>
 *   <li>{@code objects.githubusercontent.com} -- der seit Jahren von GitHub
 *       genutzte, S3-gestuetzte Asset-Speicher. Ein GET auf den
 *       {@code github.com}-Link antwortet mit {@code 302} hierher (kurzlebige,
 *       signierte URL).</li>
 *   <li>{@code release-assets.githubusercontent.com} -- der 2024 von GitHub
 *       neu eingefuehrte Auslieferungshost fuer Release-Assets; dasselbe
 *       {@code 302}-Muster wie oben, nur anderer Hostname. Beide Ziele werden
 *       von GitHub betrieben; ein dritter, fremder Host ist bei einem
 *       Release-Asset-Download nie beteiligt.</li>
 * </ul>
 *
 * <p>Alles andere -- {@code http://}, ein fremder Host, eine Lookalike-Subdomain
 * wie {@code github.com.evil.example} -- wird abgelehnt. Der Vergleich ist ein
 * exakter, klein geschriebener Set-Abgleich, kein Suffix-Match.</p>
 */
public final class DownloadHostWhitelist {

	/** Die einzigen Hosts, von denen der Mod ein {@code .vpkg} laedt. */
	public static final Set<String> ALLOWED_HOSTS = Set.of(
			"github.com",
			"objects.githubusercontent.com",
			"release-assets.githubusercontent.com");

	/**
	 * Produktions-Regel: {@code https} <em>und</em> Host auf
	 * {@link #ALLOWED_HOSTS}. {@code PackageDownloader.production()} verdrahtet
	 * genau diese; Tests, die eine lokale {@code file://}-Quelle ueber den
	 * {@code DownloadTransport}-Seam laden, setzen bewusst eine andere Regel ein.
	 */
	public static final Predicate<URI> RELEASE_ASSETS = DownloadHostWhitelist::isAllowed;

	private DownloadHostWhitelist() {
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
