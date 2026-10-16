package de.spassglas.vitrine.catalog;

import java.net.URI;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Oeffentliche Bruecke zu den paket-privaten Testbausteinen dieses Pakets
 * ({@link CatalogTestSupport}, {@link HttpCatalogClient}, {@link EtagCache},
 * {@link FileUriTransport}) fuer Tests in anderen Paketen (ROADMAP G3:
 * {@code CatalogPackageDetailSourceTest} in {@code ui.detail}). Kein
 * {@code @Test} -- nur Fabrikmethoden.
 */
public final class CatalogTestSupportBridge {

	private static final Predicate<URI> ALLOW_ANY = uri -> true;

	private CatalogTestSupportBridge() {
	}

	/** Siehe {@link CatalogTestSupport#VALID_REGISTRY_PACKAGE_JSON}. */
	public static String validRegistryPackageJson() {
		return CatalogTestSupport.VALID_REGISTRY_PACKAGE_JSON;
	}

	/** Siehe {@link CatalogTestSupport#VALID_REGISTRY_PACKAGE_JSON_WITH_MINECRAFT_VERSIONS}. */
	public static String validRegistryPackageJsonWithMinecraftVersions() {
		return CatalogTestSupport.VALID_REGISTRY_PACKAGE_JSON_WITH_MINECRAFT_VERSIONS;
	}

	/** Siehe {@link CatalogTestSupport#VALID_REGISTRY_PACKAGE_JSON_JAVAMOD} (GATE77). */
	public static String validRegistryPackageJsonJavaMod() {
		return CatalogTestSupport.VALID_REGISTRY_PACKAGE_JSON_JAVAMOD;
	}

	/**
	 * Ein {@link RegistryPackageClient}, der {@code packages/&lt;id&gt;.json}
	 * ueber {@code file://} aus {@code packagesDir} liest -- fuer Tests ohne
	 * echtes Netz, wie {@code CatalogClientTest}s {@code fileClient}.
	 */
	public static RegistryPackageClient fileRegistryPackageClient(Path packagesDir) {
		HttpCatalogClient http = new HttpCatalogClient(new FileUriTransport(),
				new EtagCache(packagesDir.resolveSibling("cache")), ALLOW_ANY);
		return new RegistryPackageClient(http, packagesDir.toUri());
	}
}
