package de.spassglas.vitrine.catalog.github;

import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP GATE70: {@link GitHubRepositoryReference#parse} muss ein echtes
 * GitHub-Repo erkennen und jede andere Angabe ehrlich als "kein Repo"
 * behandeln (Charter-Regel 2: keine erfundene Vermutung).
 */
public final class GitHubRepositoryReferenceTest {

	@Test
	public void parsesOwnerAndRepoFromAPlainGitHubUrl() {
		Optional<GitHubRepositoryReference> ref =
				GitHubRepositoryReference.parse("https://github.com/umgekocht/vitrine-packs-starter");
		GitHubRepositoryReference value = ref.orElseThrow();
		assertEquals(value.owner(), "umgekocht");
		assertEquals(value.repo(), "vitrine-packs-starter");
	}

	@Test
	public void stripsGitSuffixAndTrailingSlash() {
		assertEquals(GitHubRepositoryReference.parse("https://github.com/mira/aurora-hud.git/").orElseThrow(),
				new GitHubRepositoryReference("mira", "aurora-hud"));
		assertEquals(GitHubRepositoryReference.parse("https://github.com/mira/aurora-hud/").orElseThrow(),
				new GitHubRepositoryReference("mira", "aurora-hud"));
	}

	@Test
	public void rejectsNonGitHubHost() {
		assertTrue(GitHubRepositoryReference.parse("https://gitlab.com/mira/aurora-hud").isEmpty());
		assertTrue(GitHubRepositoryReference.parse("https://example.invalid/mira/aurora-hud").isEmpty());
	}

	@Test
	public void rejectsPlainHttp() {
		assertTrue(GitHubRepositoryReference.parse("http://github.com/mira/aurora-hud").isEmpty());
	}

	@Test
	public void rejectsPathsWithoutExactlyOwnerAndRepo() {
		assertTrue(GitHubRepositoryReference.parse("https://github.com/mira").isEmpty());
		assertTrue(GitHubRepositoryReference.parse("https://github.com/mira/aurora-hud/releases").isEmpty());
		assertTrue(GitHubRepositoryReference.parse("https://github.com/").isEmpty());
	}

	@Test
	public void rejectsNullBlankAndMalformedInput() {
		assertTrue(GitHubRepositoryReference.parse(null).isEmpty());
		assertTrue(GitHubRepositoryReference.parse("   ").isEmpty());
		assertTrue(GitHubRepositoryReference.parse("not a url at all").isEmpty());
	}
}
