package de.spassglas.vitrine.catalog;

import java.nio.charset.StandardCharsets;

/**
 * Gemeinsame Testdaten fuer das {@code catalog}-Package. Kein {@code @Test} --
 * nur Konstanten.
 */
final class CatalogTestSupport {

	private CatalogTestSupport() {
	}

	/** Eine schema-gueltige Index-Seite mit zwei Paketen. */
	static final String VALID_INDEX_PAGE_JSON = """
			{
			  "schemaVersion": 1,
			  "generatedAt": "2026-09-10T12:00:00Z",
			  "page": 1,
			  "pageSize": 50,
			  "totalPages": 1,
			  "totalCount": 2,
			  "nextPage": null,
			  "previousPage": null,
			  "packages": [
			    {
			      "id": "umgekocht.klarglas",
			      "name": "Klarglas",
			      "author": "Umgekocht",
			      "category": "theme",
			      "license": "CC-BY-4.0",
			      "latestVersion": "1.0.0",
			      "icon": "https://umgekocht.github.io/vitrine-registry/icons/klarglas.png",
			      "shortDescription": "Cleanes Graustufen-Theme",
			      "updatedAt": "2026-09-01T08:30:00Z"
			    },
			    {
			      "id": "umgekocht.pixelscharf",
			      "name": "Pixelscharf",
			      "author": "Umgekocht",
			      "category": "texturen",
			      "license": "CC-BY-4.0",
			      "latestVersion": "2.1.0",
			      "updatedAt": "2026-08-20T10:00:00Z"
			    }
			  ]
			}
			""";

	static byte[] validIndexPageBytes() {
		return VALID_INDEX_PAGE_JSON.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Eine schema-gueltige {@code featured.json} mit zwei Eintraegen je Liste,
	 * absichtlich nicht nach {@code order} sortiert -- {@code id}-Werte passen
	 * zu den Paketen aus {@link #VALID_INDEX_PAGE_JSON} (ROADMAP X2).
	 * {@code items} (Featured) und {@code popular} (Beliebt) sind bewusst
	 * nicht deckungsgleich: {@code umgekocht.pixelscharf} steht nur in
	 * {@code items}, {@code umgekocht.klarglas} in beiden -- genau wie bei den
	 * echten Registry-Beispielpaketen (spassglas.mitternacht-theme,
	 * spassglas.kompakt-hud).
	 */
	static final String VALID_FEATURED_JSON = """
			{
			  "schemaVersion": 1,
			  "generatedAt": "2026-09-10T12:00:00Z",
			  "items": [
			    {
			      "id": "umgekocht.pixelscharf",
			      "order": 1,
			      "license": "CC-BY-4.0"
			    },
			    {
			      "id": "umgekocht.klarglas",
			      "order": 0,
			      "license": "CC-BY-4.0",
			      "note": "Cleanes Standard-Theme"
			    }
			  ],
			  "popular": [
			    {
			      "id": "umgekocht.klarglas",
			      "order": 0,
			      "license": "CC-BY-4.0",
			      "note": "Meistgenutzt"
			    }
			  ]
			}
			""";

	static byte[] validFeaturedBytes() {
		return VALID_FEATURED_JSON.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Eine schema-gueltige {@code search.json} (ROADMAP K1/GATE72) mit zwei
	 * Eintraegen -- bewusst mit einem Paket, das in keiner Index-Seite dieses
	 * Testgerueststeht ({@code umgekocht.nurimsuchindex}), damit Tests beweisen
	 * koennen, dass ein Treffer tatsaechlich aus {@code search.json} stammt und
	 * nicht aus dem lokal gecachten Index.
	 */
	static final String VALID_SEARCH_INDEX_JSON = """
			{
			  "schemaVersion": 1,
			  "generatedAt": "2026-09-10T12:00:00Z",
			  "totalCount": 2,
			  "entries": [
			    {
			      "id": "umgekocht.klarglas",
			      "name": "Klarglas",
			      "author": "Umgekocht",
			      "category": "theme",
			      "license": "CC-BY-4.0",
			      "tags": ["clean", "glas"],
			      "keywords": ["klarglas", "umgekocht", "theme", "clean", "glas"]
			    },
			    {
			      "id": "umgekocht.nurimsuchindex",
			      "name": "Nur im Suchindex",
			      "author": "Umgekocht",
			      "category": "texturen",
			      "license": "CC-BY-4.0",
			      "keywords": ["nurimsuchindex", "umgekocht", "texturen"]
			    }
			  ]
			}
			""";

	static byte[] validSearchIndexBytes() {
		return VALID_SEARCH_INDEX_JSON.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Ein schema-gueltiger voller Registry-Paketeintrag (ROADMAP G3,
	 * {@code packages/&lt;id&gt;.json}) mit genau einer Version. Pruefsumme/
	 * Signatur sind hier reine Platzhalter (nur formal gueltig, nicht
	 * kryptographisch echt) -- fuer echte Signaturpruefungen baut
	 * {@code CatalogPackInstallerEndToEndTest} eigene, echt signierte Fixtures.
	 */
	static final String VALID_REGISTRY_PACKAGE_JSON = """
			{
			  "schemaVersion": 1,
			  "id": "umgekocht.klarglas",
			  "name": "Klarglas",
			  "category": "theme",
			  "author": {
			    "name": "Umgekocht",
			    "url": "https://github.com/umgekocht"
			  },
			  "description": "Cleanes Graustufen-Theme.",
			  "homepage": "https://github.com/umgekocht/vitrine-packs-starter",
			  "license": "CC-BY-4.0",
			  "repository": "https://github.com/umgekocht/vitrine-packs-starter",
			  "versions": [
			    {
			      "version": "1.0.0",
			      "vpkgUrl": "https://github.com/umgekocht/vitrine-packs-starter/releases/download/klarglas-v1.0.0/klarglas.vpkg",
			      "sha256": "%s",
			      "signature": "%s",
			      "size": 20480,
			      "publishedAt": "2026-08-25T09:00:00Z"
			    }
			  ],
			  "latestVersion": "1.0.0",
			  "updatedAt": "2026-08-25T09:00:00Z"
			}
			""".formatted("a".repeat(64), "A".repeat(44));

	static byte[] validRegistryPackageBytes() {
		return VALID_REGISTRY_PACKAGE_JSON.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * GATE70-FOLLOW2: wie {@link #VALID_REGISTRY_PACKAGE_JSON}, zusaetzlich mit
	 * dem optionalen Paket-Feld {@code minecraftVersions} (registry-package.schema.json,
	 * Beispiel aus {@code registry/packages/spassglas.steinbrocken-textur.json}).
	 */
	static final String VALID_REGISTRY_PACKAGE_JSON_WITH_MINECRAFT_VERSIONS = """
			{
			  "schemaVersion": 1,
			  "id": "spassglas.steinbrocken-textur",
			  "name": "Steinbrocken-Textur",
			  "category": "texturen",
			  "author": {
			    "name": "Spassglas",
			    "url": "https://github.com/spassglas"
			  },
			  "description": "Grobkoernige Steinbrocken-Textur.",
			  "homepage": "https://github.com/spassglas/vitrine-packs-starter",
			  "license": "CC-BY-4.0",
			  "repository": "https://github.com/spassglas/vitrine-packs-starter",
			  "minecraftVersions": ["1.21.1", "1.21.4"],
			  "versions": [
			    {
			      "version": "1.0.0",
			      "vpkgUrl": "https://github.com/spassglas/vitrine-packs-starter/releases/download/steinbrocken-textur-v1.0.0/steinbrocken-textur.vpkg",
			      "sha256": "%s",
			      "signature": "%s",
			      "size": 20480,
			      "publishedAt": "2026-08-25T09:00:00Z"
			    }
			  ],
			  "latestVersion": "1.0.0",
			  "updatedAt": "2026-08-25T09:00:00Z"
			}
			""".formatted("a".repeat(64), "A".repeat(44));

	/**
	 * GATE90-FOLLOW: wie {@link #VALID_REGISTRY_PACKAGE_JSON}, zusaetzlich mit
	 * dem optionalen Paket-Feld {@code capabilities} (registry-package.schema.json,
	 * Beispiel aus {@code registry/packages/spassglas.kompakt-hud.json}) --
	 * fuer den Nachweis, dass {@link CatalogPackageDetailSource} echte,
	 * vor dem Download bekannte Faehigkeiten durchreicht ({@code
	 * capabilitiesKnown=true}, GATE90).
	 */
	static final String VALID_REGISTRY_PACKAGE_JSON_WITH_CAPABILITIES = """
			{
			  "schemaVersion": 1,
			  "id": "spassglas.kompakt-hud",
			  "name": "Kompakt-HUD",
			  "category": "hud",
			  "author": {
			    "name": "Spassglas",
			    "url": "https://github.com/spassglas"
			  },
			  "description": "Platzsparendes HUD-Layout.",
			  "homepage": "https://github.com/spassglas/vitrine-packs-starter",
			  "license": "CC-BY-4.0",
			  "repository": "https://github.com/spassglas/vitrine-packs-starter",
			  "capabilities": ["hud"],
			  "versions": [
			    {
			      "version": "1.0.0",
			      "vpkgUrl": "https://github.com/spassglas/vitrine-packs-starter/releases/download/kompakt-hud-v1.0.0/kompakt-hud.vpkg",
			      "sha256": "%s",
			      "signature": "%s",
			      "size": 20480,
			      "publishedAt": "2026-08-25T09:00:00Z"
			    }
			  ],
			  "latestVersion": "1.0.0",
			  "updatedAt": "2026-08-25T09:00:00Z"
			}
			""".formatted("a".repeat(64), "A".repeat(44));

	/**
	 * GATE77: ein schema-gueltiger Klasse-B-Registry-Paketeintrag
	 * ({@code contentClass: "javamod"}) mit genau einer {@code .jar}-Version
	 * -- fuer den Nachweis, dass {@link CatalogPackageDetailSource} ein
	 * solches Angebot ueberhaupt in ein {@code PackageDetailModel}
	 * uebersetzt (statt an der leeren {@code versions[]} zu scheitern) und
	 * dabei {@code runsNoProgramCode() == false} liefert.
	 */
	static final String VALID_REGISTRY_PACKAGE_JSON_JAVAMOD = """
			{
			  "schemaVersion": 1,
			  "id": "umgekocht.testmod",
			  "name": "Testmod",
			  "category": "hud",
			  "contentClass": "javamod",
			  "author": {
			    "name": "Umgekocht",
			    "url": "https://github.com/umgekocht"
			  },
			  "description": "Ein Java-Mod-Angebot fuer den GATE77-Nachweis.",
			  "license": "MIT",
			  "repository": "https://github.com/umgekocht/vitrine-testmod",
			  "versions": [
			    {
			      "version": "1.0.0",
			      "jarUrl": "https://github.com/umgekocht/vitrine-testmod/releases/download/v1.0.0/testmod.jar",
			      "fileName": "testmod.jar",
			      "sha256": "%s",
			      "signature": "%s",
			      "size": 20480,
			      "publishedAt": "2026-08-25T09:00:00Z",
			      "dependencies": ["fabric-api"]
			    }
			  ],
			  "latestVersion": "1.0.0",
			  "updatedAt": "2026-08-25T09:00:00Z"
			}
			""".formatted("a".repeat(64), "A".repeat(44));
}
