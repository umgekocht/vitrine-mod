package de.spassglas.vitrine.catalog;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Host-Whitelist fuer den Katalog-Client (ROADMAP K2, PLAN &sect;6: "Nur HTTPS,
 * feste Hosts, keine Weiterleitung auf andere Hosts"). Der Katalog liegt auf
 * GitHub Pages unter genau einem Host; nur der ist erlaubt. Alles andere --
 * fremder Host, {@code http://}, {@code file://} bei der Produktions-URL --
 * wird abgelehnt.
 */
public final class HostWhitelist {

	/** Der einzige Host, von dem der Mod Katalog-Metadaten liest (K7-Live-URL). */
	public static final Set<String> ALLOWED_HOSTS = Set.of("umgekocht.github.io");

	/**
	 * Produktions-Regel: {@code https} <em>und</em> Host auf {@link #ALLOWED_HOSTS}.
	 * {@link CatalogClient#production} verdrahtet genau diese; Tests, die eine
	 * lokale {@code file://}-Quelle ueber den {@link Transport}-Seam laden,
	 * setzen bewusst eine andere Regel ein.
	 */
	public static final Predicate<URI> PRODUCTION = HostWhitelist::isProductionAllowed;

	private HostWhitelist() {
	}

	public static boolean isProductionAllowed(URI uri) {
		if (uri == null || uri.getHost() == null) {
			return false;
		}
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			return false;
		}
		return ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
	}
}
