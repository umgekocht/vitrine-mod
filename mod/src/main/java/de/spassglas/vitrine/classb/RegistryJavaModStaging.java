package de.spassglas.vitrine.classb;

import de.spassglas.vitrine.catalog.RegistryJavaModVersion;
import de.spassglas.vitrine.catalog.RegistryPackageEntry;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * GATE6: der eine Produktionsweg vom Katalog-Eintrag zu einem gestagten
 * Java-Mod. Vorher hatte {@link JavaModStager#stage} keinen einzigen
 * Aufrufer -- Klasse B existierte nur in Tests.
 *
 * <p>Client-frei, damit die Entscheidungen ohne laufendes Spiel pruefbar
 * sind; die Detailseite ruft das auf einem Hintergrund-Thread auf.</p>
 *
 * <p>Drei Riegel, in dieser Reihenfolge, jeder mit eigener, lesbarer Meldung:</p>
 * <ol>
 *   <li>der Opt-in-Schalter "Java-Mods anzeigen" ist an (ROADMAP J1, Standard
 *       aus) -- die Katalog-Schleuse blendet die Angebote sonst zwar schon
 *       aus, aber ein Aufruf hier darf sich nicht darauf verlassen;</li>
 *   <li>der Registry-Eintrag ist wirklich Klasse B ({@code contentClass}
 *       {@code javamod});</li>
 *   <li>zu {@code latestVersion} gibt es einen Klasse-B-Versionseintrag.</li>
 * </ol>
 * Erst dann entscheidet {@link JavaModStager#stage}: Pruefsumme, Signatur
 * gegen den eingebetteten Schluessel, Konflikte -- alles vor dem Kopieren.
 */
public final class RegistryJavaModStaging {

	private RegistryJavaModStaging() {
	}

	public static StagedMod stage(RegistryPackageEntry entry, JavaModStager stager, BooleanSupplier optIn)
			throws JavaModStagingException {
		Objects.requireNonNull(entry, "entry");
		Objects.requireNonNull(stager, "stager");
		Objects.requireNonNull(optIn, "optIn");

		if (!optIn.getAsBoolean()) {
			throw new JavaModStagingException("Java-Mods sind in den Einstellungen ausgeschaltet -- '"
					+ entry.id() + "' wird nicht heruntergeladen.");
		}
		if (!entry.isJavaMod()) {
			throw new JavaModStagingException("'" + entry.id() + "' ist kein Java-Mod-Angebot (Klasse A) -- "
					+ "es wird ohne Neustart ueber den normalen Weg installiert.");
		}
		RegistryJavaModVersion latest = entry.latestJavaModVersion().orElseThrow(() ->
				new JavaModStagingException("'" + entry.id() + "' nennt Version '" + entry.latestVersion()
						+ "', fuer die der Katalog keine Jar fuehrt."));

		JavaModOffer offer;
		try {
			offer = latest.toOffer(entry.id(), entry.name(), entry.authorName());
		} catch (IllegalArgumentException e) {
			throw new JavaModStagingException("Der Katalog-Eintrag von '" + entry.id()
					+ "' ist unbrauchbar: " + e.getMessage(), e);
		}
		return stager.stage(offer);
	}
}
