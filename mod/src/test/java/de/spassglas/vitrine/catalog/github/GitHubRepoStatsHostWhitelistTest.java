package de.spassglas.vitrine.catalog.github;

import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE70: nur {@code https://api.github.com/...} darf angefragt
 * werden -- weder ein fremder Host noch {@code http} (Charter-Regel 3:
 * Sicherheit vor Bequemlichkeit).
 */
public final class GitHubRepoStatsHostWhitelistTest {

	@Test
	public void allowsHttpsApiGithub() {
		assertTrue(GitHubRepoStatsHostWhitelist.isAllowed(URI.create("https://api.github.com/repos/a/b")));
	}

	@Test
	public void rejectsPlainHttp() {
		assertFalse(GitHubRepoStatsHostWhitelist.isAllowed(URI.create("http://api.github.com/repos/a/b")));
	}

	@Test
	public void rejectsForeignHost() {
		assertFalse(GitHubRepoStatsHostWhitelist.isAllowed(URI.create("https://evil.example/repos/a/b")));
		assertFalse(GitHubRepoStatsHostWhitelist.isAllowed(URI.create("https://github.com/a/b")));
	}

	@Test
	public void rejectsUriWithoutHost() {
		assertFalse(GitHubRepoStatsHostWhitelist.isAllowed(URI.create("relative/path")));
	}
}
