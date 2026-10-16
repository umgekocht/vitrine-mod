package de.spassglas.vitrine.catalog;

import java.net.URI;
import java.time.Duration;

/**
 * Einkompilierte, versionierte Zugangsdaten des Vitrine-Katalogs (ROADMAP K2,
 * "Technische Festlegungen": die Standard-Index-URL ist einkompiliert und
 * versioniert). Kein Secret, kein Token, kein absoluter Dateipfad -- nur eine
 * oeffentliche {@code https}-URL auf GitHub Pages und ein paar Sicherheits-
 * Obergrenzen. Charter-Regel 12: ohne jede Konfiguration nutzbar.
 */
public final class CatalogEndpoints {

	/**
	 * Erste Index-Seite des Live-Katalogs (K7). Versioniert ({@code index/v1/})
	 * -- ein kaputter Index-Bau einer spaeteren Version legt bestehende
	 * Installationen nicht lahm.
	 */
	public static final URI DEFAULT_INDEX_URL =
			URI.create("https://umgekocht.github.io/vitrine-registry/index/v1/index.json");

	/**
	 * Kuratierte Auswahl (ROADMAP K1/X2, "featured.json") -- Geschwisterdatei von
	 * {@link #DEFAULT_INDEX_URL} unter demselben versionierten Pfad. Ebenfalls
	 * fest einkompiliert; {@link CatalogClient#fetchFeatured()} leitet dieselbe
	 * URL zusaetzlich per {@code indexUrl.resolve("featured.json")} her (wie bei
	 * den paginierten Index-Seiten) -- diese Konstante dient der Doku und den
	 * Tests, die beide Wege auf Gleichheit pruefen.
	 */
	public static final URI DEFAULT_FEATURED_URL =
			URI.create("https://umgekocht.github.io/vitrine-registry/index/v1/featured.json");

	/**
	 * Flacher Suchindex (ROADMAP K1/GATE72, {@code search.json}) -- Geschwisterdatei
	 * von {@link #DEFAULT_INDEX_URL} unter demselben versionierten Pfad, genauso
	 * hergeleitet wie {@link #DEFAULT_FEATURED_URL}. {@link CatalogClient#fetchSearchIndex()}
	 * leitet dieselbe URL zusaetzlich per {@code indexUrl.resolve("search.json")} her.
	 */
	public static final URI DEFAULT_SEARCH_URL =
			URI.create("https://umgekocht.github.io/vitrine-registry/index/v1/search.json");

	/**
	 * Basis-URL der vollen Registry-Paketeintraege (ROADMAP G3/K5,
	 * {@code packages/&lt;id&gt;.json}) -- Geschwisterordner von
	 * {@code index/v1/} auf demselben Host, nicht darunter versioniert (das
	 * Registry-Repo legt {@code packages/} auf Wurzelebene, siehe
	 * {@code registry/build-index.js}). {@link RegistryPackageClient#fetchPackage}
	 * haengt {@code &lt;id&gt;.json} per {@link URI#resolve(String)} an.
	 */
	public static final URI DEFAULT_PACKAGES_BASE_URL =
			URI.create("https://umgekocht.github.io/vitrine-registry/packages/");

	/** Verbindungsaufbau-Timeout -- kurz gehalten, der Store soll nicht haengen. */
	public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);

	/** Gesamt-Timeout je Anfrage (Verbindung + Antwort). */
	public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	/**
	 * Harte Obergrenze fuer den Antwortkoerper einer einzelnen Katalog-Datei
	 * (Index-Seite). Wird beim Lesen erzwungen; eine Ueberschreitung bricht ab,
	 * es wird nie unbegrenzt gepuffert. 8 MiB ist grosszuegig fuer eine Seite
	 * mit hoechstens 1000 Kurzeintraegen und deckelt zugleich eine "riesige
	 * Antwort"-Attacke.
	 */
	public static final long MAX_BODY_BYTES = 8L * 1024 * 1024;

	/**
	 * Hoechstzahl an Weiterleitungen, denen der Client folgt -- und zwar nur,
	 * solange jedes Ziel wieder auf der Host-Whitelist steht (siehe
	 * {@link HostWhitelist}). Eine Weiterleitung auf einen fremden Host wird
	 * hart abgelehnt, nie verfolgt.
	 */
	public static final int MAX_REDIRECTS = 5;

	/**
	 * Sicherheitsobergrenze, wie viele Index-Seiten {@code StoreCatalogController}
	 * in einem einzigen Refresh nachlaedt (ROADMAP GATE72). Das Schema erlaubt
	 * fuer {@code totalPages} jede Zahl &gt;= 1 -- ohne Obergrenze koennte ein
	 * kompromittierter oder kaputter Index-Bau den Client zu hunderttausenden
	 * Anfragen verleiten. 200 Seiten decken bei {@code pageSize: 24} ueber 4800
	 * Pakete ab, weit mehr als der Live-Katalog heute hat.
	 */
	public static final int MAX_INDEX_PAGES_PER_REFRESH = 200;

	/** Unterordner im Vitrine-Konfigordner fuer den ETag-Cache ({@code config/vitrine/cache/}). */
	public static final String CACHE_DIR_NAME = "cache";

	private CatalogEndpoints() {
	}
}
