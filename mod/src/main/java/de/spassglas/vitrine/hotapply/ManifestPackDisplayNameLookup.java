package de.spassglas.vitrine.hotapply;

import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Produktions-Umsetzung von {@link PackDisplayNameLookup} (ROADMAP GATE81):
 * liest den Klartext-Anzeigenamen rein lesend aus dem Manifest der
 * installierten {@code .vpkg}-Datei -- derselbe Weg wie {@code
 * LibraryModel#readEntry}, nur auf den Namen beschraenkt.
 *
 * <p>Kein Treffer oder ein unlesbares Manifest wirft nicht, sondern faellt
 * auf die rohe Paket-ID zurueck und loggt die Ursache -- ein fehlender
 * Anzeigename darf nie den Rollback-/Fehlschlag-Pfad selbst zum Scheitern
 * bringen (Charter-Regel 2: kein erfundener Name, aber auch kein neuer
 * Fehler nur fuer die Anzeige).</p>
 */
public final class ManifestPackDisplayNameLookup implements PackDisplayNameLookup {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/HotApply");

	private final VitrinePackProvider provider;

	public ManifestPackDisplayNameLookup(VitrinePackProvider provider) {
		this.provider = Objects.requireNonNull(provider, "provider");
	}

	@Override
	public String displayNameFor(String packId) {
		Optional<Path> file = provider.findPackFile(packId);
		if (file.isEmpty()) {
			return packId;
		}
		try (VpkgArchive archive = VpkgArchive.open(file.get())) {
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			return manifest.name();
		} catch (IOException | ManifestParseException | RuntimeException e) {
			LOGGER.warn("Vitrine: Anzeigename fuer '{}' nicht ermittelbar ({}), zeige die Paket-ID.", packId,
					e.toString());
			return packId;
		}
	}
}
