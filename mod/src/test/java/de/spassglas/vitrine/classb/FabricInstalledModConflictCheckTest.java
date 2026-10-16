package de.spassglas.vitrine.classb;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP J3 -- {@link FabricInstalledModConflictCheck} isoliert von
 * {@link JavaModStager}: doppelte Mod-ID (installiert und pending) und fehlende
 * bzw. erfuellte harte Abhaengigkeit, jeweils ohne echten {@code FabricLoader}.
 */
public final class FabricInstalledModConflictCheckTest {

	@Test
	public void throwsWhenModIdIsAlreadyLoaded() {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				() -> Set.of("de.autor.schon-da"), Set::of);

		JavaModStagingException ex = expectThrows(JavaModStagingException.class,
				() -> check.check(offer("de.autor.schon-da")));
		assertTrue(ex.getMessage().contains("de.autor.schon-da"));
	}

	@Test
	public void throwsWhenModIdIsAlreadyPending() {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				Set::of, () -> Set.of("de.autor.pending"));

		JavaModStagingException ex = expectThrows(JavaModStagingException.class,
				() -> check.check(offer("de.autor.pending")));
		assertTrue(ex.getMessage().contains("de.autor.pending"));
	}

	@Test
	public void passesWhenModIdIsFree() throws Exception {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				() -> Set.of("irgendwas.anderes"), Set::of);

		check.check(offer("de.autor.frei"));
	}

	@Test
	public void throwsWhenHardDependencyIsMissing() {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(Set::of, Set::of);

		JavaModStagingException ex = expectThrows(JavaModStagingException.class,
				() -> check.check(offerWithDependency("de.autor.consumer", "de.autor.fehlt")));
		assertTrue(ex.getMessage().contains("de.autor.fehlt"));
	}

	@Test
	public void passesWhenDependencyIsAlreadyInstalled() throws Exception {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				() -> Set.of("de.autor.lib"), Set::of);

		check.check(offerWithDependency("de.autor.consumer", "de.autor.lib"));
	}

	@Test
	public void passesWhenDependencyIsAlreadyPending() throws Exception {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(
				Set::of, () -> Set.of("de.autor.lib"));

		check.check(offerWithDependency("de.autor.consumer", "de.autor.lib"));
	}

	@Test
	public void passesWhenDependencyIsSatisfiedByTheOfferItself() throws Exception {
		FabricInstalledModConflictCheck check = new FabricInstalledModConflictCheck(Set::of, Set::of);

		check.check(offerWithDependency("de.autor.selbst", "de.autor.selbst"));
	}

	private static JavaModOffer offer(String id) {
		return JavaModOffer.of(id, id, "Autorin", id + ".jar", "0".repeat(64));
	}

	private static JavaModOffer offerWithDependency(String id, String dependency) {
		return JavaModOffer.of(id, id, "Autorin", id + ".jar", "0".repeat(64), List.of(dependency));
	}
}
