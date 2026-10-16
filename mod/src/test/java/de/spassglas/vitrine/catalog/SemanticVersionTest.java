package de.spassglas.vitrine.catalog;

import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP K5, SemVer-Vergleicher: Praezedenz nach semver.org 2.0.0 und
 * wurffreies {@code tryParse}.
 */
public final class SemanticVersionTest {

	private static SemanticVersion parse(String raw) {
		return SemanticVersion.tryParse(raw).orElseThrow(() -> new AssertionError("sollte parsen: " + raw));
	}

	private static void assertOrder(String smaller, String larger) {
		SemanticVersion a = parse(smaller);
		SemanticVersion b = parse(larger);
		assertTrue(a.compareTo(b) < 0, smaller + " < " + larger);
		assertTrue(b.compareTo(a) > 0, larger + " > " + smaller);
		assertTrue(b.isNewerThan(a), larger + ".isNewerThan(" + smaller + ")");
		assertFalse(a.isNewerThan(b), "!" + smaller + ".isNewerThan(" + larger + ")");
	}

	@Test
	public void majorMinorPatchComparedNumerically() {
		assertOrder("1.0.0", "2.0.0");
		assertOrder("1.2.0", "1.10.0"); // numerisch, nicht lexikalisch
		assertOrder("1.2.3", "1.2.10");
		assertOrder("1.9.9", "2.0.0");
	}

	@Test
	public void prereleaseIsLowerThanRelease() {
		assertOrder("1.0.0-alpha", "1.0.0");
	}

	@Test
	public void prereleaseIdentifiersComparedFieldByField() {
		assertOrder("1.0.0-alpha.1", "1.0.0-alpha.2");
		assertOrder("1.0.0-alpha", "1.0.0-alpha.1");   // kuerzerer Satz < laengerer
		assertOrder("1.0.0-alpha.1", "1.0.0-alpha.beta"); // numerisch < alphanumerisch
		assertOrder("1.0.0-alpha", "1.0.0-beta");
		assertOrder("1.0.0-beta.2", "1.0.0-beta.11");  // numerische Felder numerisch
		assertOrder("1.0.0-rc.1", "1.0.0");
	}

	@Test
	public void buildMetadataHasNoInfluenceOnPrecedence() {
		SemanticVersion withBuild = parse("1.0.0+20130313144700");
		SemanticVersion withoutBuild = parse("1.0.0");
		SemanticVersion otherBuild = parse("1.0.0+exp.sha.5114f85");

		assertEquals(withBuild.compareTo(withoutBuild), 0);
		assertEquals(withBuild.compareTo(otherBuild), 0);
		assertFalse(withBuild.isNewerThan(withoutBuild));
		assertFalse(withoutBuild.isNewerThan(withBuild));
		assertEquals(withBuild, withoutBuild);
		assertEquals(withBuild.hashCode(), withoutBuild.hashCode());
	}

	@Test
	public void prereleaseWithBuildStillLowerThanRelease() {
		assertOrder("1.0.0-alpha+001", "1.0.0+build");
	}

	@Test
	public void tryParseReturnsEmptyForGarbage() {
		for (String bad : new String[] {
				null, "", "   ", "1", "1.0", "1.0.0.0", "v1.0.0", "1.0.0-", "1.0.0+",
				"01.0.0", "1.02.0", "1.0.0-01", "1.0.0-alpha..1", "1.2.x", "1.0.0-alpha_1",
				"latest", "1 .0.0", "-1.0.0", "1.-0.0" }) {
			assertTrue(SemanticVersion.tryParse(bad).isEmpty(), "sollte NICHT parsen: " + bad);
		}
	}

	@Test
	public void tryParseAcceptsTypicalReleaseAndPrereleaseForms() {
		assertTrue(SemanticVersion.tryParse("0.0.1").isPresent());
		assertTrue(SemanticVersion.tryParse("10.20.30").isPresent());
		assertTrue(SemanticVersion.tryParse("1.0.0-alpha.beta.1").isPresent());
		assertTrue(SemanticVersion.tryParse("1.0.0-x-y-z.--").isPresent());
		Optional<SemanticVersion> v = SemanticVersion.tryParse("1.2.3-rc.1+build.9");
		assertTrue(v.isPresent());
		assertTrue(v.get().isPrerelease());
		assertEquals(v.get().major(), 1L);
		assertEquals(v.get().minor(), 2L);
		assertEquals(v.get().patch(), 3L);
	}

	@Test
	public void equalReleaseVersionsCompareEqual() {
		assertEquals(parse("1.2.3").compareTo(parse("1.2.3")), 0);
		assertEquals(parse("1.2.3"), parse("1.2.3"));
	}
}
