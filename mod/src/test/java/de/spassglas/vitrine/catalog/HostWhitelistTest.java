package de.spassglas.vitrine.catalog;

import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Host-Whitelist des Katalog-Clients (ROADMAP K2): nur {@code https} auf genau
 * einen Host. {@code http://}, {@code file://} und fremde Hosts werden bei der
 * Produktions-Regel abgelehnt.
 */
public final class HostWhitelistTest {

	@Test
	public void compiledInIndexUrlPassesTheWhitelist() {
		assertTrue(HostWhitelist.isProductionAllowed(CatalogEndpoints.DEFAULT_INDEX_URL));
		assertTrue(CatalogEndpoints.DEFAULT_INDEX_URL.toString()
				.endsWith("/index/v1/index.json"), "Index-URL muss versioniert sein");
	}

	@Test
	public void httpSchemeIsRejected() {
		assertFalse(HostWhitelist.isProductionAllowed(
				URI.create("http://umgekocht.github.io/vitrine-registry/index/v1/index.json")));
	}

	@Test
	public void fileSchemeIsRejected() {
		assertFalse(HostWhitelist.isProductionAllowed(URI.create("file:///tmp/index.json")));
	}

	@Test
	public void foreignHostIsRejected() {
		assertFalse(HostWhitelist.isProductionAllowed(URI.create("https://evil.example/index.json")));
	}

	@Test
	public void lookalikeSubdomainIsRejected() {
		assertFalse(HostWhitelist.isProductionAllowed(
				URI.create("https://umgekocht.github.io.evil.example/index.json")));
	}
}
