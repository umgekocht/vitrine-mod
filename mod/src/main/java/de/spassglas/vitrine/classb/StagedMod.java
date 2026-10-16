package de.spassglas.vitrine.classb;

import java.util.Objects;

/**
 * ROADMAP J2: ein Eintrag der absturzsicheren {@code staged-mods.json} -- eine
 * Klasse-B-Jar, die {@link JavaModStager} nach {@code mods/} gelegt hat und die
 * auf einen Neustart wartet.
 *
 * <p>{@code pendingRestart} modelliert ehrlich, ob die Ruecknahme noch moeglich
 * ist: solange {@code true}, ist die Jar zwar in {@code mods/}, aber noch nicht
 * von der JVM geladen -- {@link JavaModStager#unstage} darf sie herausnehmen.
 * Nach einem echten Neustart meldet der Client-Einstieg die Jars ueber
 * {@link JavaModStager#markAllPendingAsLoaded()} als geladen; danach ist
 * {@code pendingRestart} {@code false} und eine Ruecknahme waere gelogen.</p>
 */
public record StagedMod(
		String id,
		String fileName,
		String sha256,
		String author,
		String displayName,
		String stagedAt,
		boolean pendingRestart) {

	public StagedMod {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(fileName, "fileName");
		// GATE61: nicht allein auf den Aufrufer verlassen -- der Name landet
		// als Dateiname in mods/ und darf dort nirgendwo anders hinzeigen.
		JavaModOffer.requireSafeJarFileName(fileName);
		Objects.requireNonNull(sha256, "sha256");
		Objects.requireNonNull(author, "author");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(stagedAt, "stagedAt");
	}

	/** Dieselbe Jar, aber als "nach dem Neustart geladen" markiert (Ruecknahme nicht mehr moeglich). */
	public StagedMod asLoaded() {
		return new StagedMod(id, fileName, sha256, author, displayName, stagedAt, false);
	}
}
