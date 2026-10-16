package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgEntry;
import net.minecraft.SharedConstants;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.PackResourceMetadata;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bruecke zwischen einem {@link VpkgArchive} (P2, liest ein {@code .vpkg}-ZIP
 * direkt, ohne zu entpacken) und Vanillas {@link ResourcePack}-Schnittstelle
 * (H1, ROADMAP M2). Ein {@code .vpkg} legt seine Ressourcen laut PLAN.md §2
 * bereits im Vanilla-Resource-Pack-Layout {@code assets/<namespace>/...} im
 * Archiv ab -- diese Klasse bildet genau diesen Unterbaum eins zu eins auf
 * {@link ResourcePack#open}/{@link ResourcePack#findResources}/{@link
 * ResourcePack#getNamespaces} ab, ohne je einen Eintrag auf die Platte zu
 * entpacken (jede Ressource kommt als {@link InputSupplier}, der bei Bedarf
 * direkt aus dem ZIP dekomprimiert).
 *
 * <p><b>Kein {@code pack.mcmeta} im Paket:</b> Das {@code .vpkg}-Format
 * (registry/schema/vitrine.schema.json) kennt kein {@code pack.mcmeta} -- das
 * Manifest heisst {@code vitrine.json} und hat ein eigenes Schema (P1). Damit
 * Vanillas {@link net.minecraft.resource.ResourcePackProfile#create} trotzdem
 * eine gueltige {@link PackResourceMetadata} bekommt (Format-Kompatibilitaet,
 * Anzeigetext), synthetisiert {@link #parseMetadata} sie direkt aus dem schon
 * gegen das Schema geprueften Manifest -- niemals aus ungeprueften Zip-Bytes.</p>
 *
 * <p><b>Sound- und Sprachpakete (ROADMAP T7) brauchen hier keine
 * Sonderbehandlung.</b> Ein Sprachpaket legt {@code assets/minecraft/lang/<locale>.json}
 * ab, ein Soundpaket {@code assets/minecraft/sounds.json} plus
 * {@code assets/minecraft/sounds/<pfad>.ogg} -- beides ist gewoehnlicher
 * {@code assets/<namespace>/...}-Inhalt, den {@link #open}/{@link #findResources}
 * unveraendert ausliefern. Das Zusammenfuehren mehrerer Pack-Ebenen erledigt
 * Vanilla selbst: {@code NamespaceResourceManager} sammelt alle {@code lang/*.json}
 * ueber saemtliche aktiven Packs und wendet sie in Anmeldereihenfolge an
 * (spaeteres Pack ueberschreibt gleiche Uebersetzungs-Keys), und
 * {@code SoundManager} fuehrt die {@code sounds.json} aller Packs genauso
 * zusammen (spaeteres Pack ueberschreibt ein Sound-Event). Ein
 * {@code pack.mcmeta}-{@code language}-Block waere nur fuer eine voellig neue
 * Locale noetig; das blosse Ueberschreiben einer bereits vorhandenen Locale
 * (T7-Fertig-Kriterium) kommt ohne aus. Damit greift ein Sound-/Sprachpaket
 * ueber denselben generischen {@link VitrinePackProvider}-Pfad hot wie ein
 * Texturpaket -- ohne {@code sounds.json}-Merge im Mod, ohne {@code
 * pack.mcmeta}-Ergaenzung, client-frei.</p>
 *
 * <p>Einweg-Objekt wie jedes {@link ResourcePack} (Klassendoku dort): pro
 * Aufruf von {@code ResourcePackProfile.PackFactory#open}/{@code
 * openWithOverlays} entsteht eine frische Instanz mit einem frisch
 * geoeffneten {@link VpkgArchive}; {@link #close()} schliesst es wieder.</p>
 *
 * <p><b>Faehigkeiten-Whitelist (GATE32, Charter-Regel 4):</b> {@code
 * assets/&lt;namespace&gt;/...} enthaelt je nach Unterordner ganz
 * unterschiedliche Faehigkeiten -- Texturen/Modelle, {@code sounds.json}/
 * {@code sounds/}, {@code lang/}, {@code shaders/}. Bevor {@link #open}/{@link
 * #findResources} auch nur einen Eintrag ausliefert, ermittelt {@link
 * #capabilityFor(String)}, welche {@link VpkgCapability} der jeweilige Pfad
 * tatsaechlich braucht, und {@code grant} (aus {@code manifest.capabilities()}
 * aufgebaut, siehe {@link VpkgCapabilityGrant}) muss genau diese Faehigkeit
 * angemeldet haben -- sonst liefert {@link #open} {@code null} bzw. laesst
 * {@link #findResources} den Eintrag aus, exakt wie ein fehlender Eintrag. Ein
 * Paket, dessen Manifest z. B. nur {@code hud} anmeldet, aber zusaetzlich eine
 * GUI-Textur im ZIP mitliefert, bekommt diese Textur damit nie angewendet
 * (Fertig-Kriterium GATE32).</p>
 */
final class VpkgResourcePack implements ResourcePack {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/VpkgResourcePack");

	private static final String ASSETS_PREFIX = "assets/";
	/** ROADMAP AD1: Datenpaket-Inhalte eines Verhaltens-Addons. */
	private static final String DATA_PREFIX = "data/";
	private static final String SOUNDS_JSON = "sounds.json";
	private static final String SOUNDS_PREFIX = "sounds/";
	private static final String LANG_PREFIX = "lang/";
	private static final String SHADERS_PREFIX = "shaders/";

	private final ResourcePackInfo info;
	private final VpkgArchive archive;
	private final String description;
	private final VpkgCapabilityGrant grant;
	/**
	 * ROADMAP AD1: welche Seite dieses Paket bedient -- Client-Ressourcen
	 * ({@code assets/**}) oder, fuer Verhaltens-Addons, Server-Daten
	 * ({@code data/**}) des integrierten Servers.
	 */
	private final ResourceType packType;

	/** Einzige Wurzel-Dateien, die {@link #openRoot} ausliefert (GATE58). */
	static final java.util.Set<String> ROOT_FILES = java.util.Set.of("pack.png", "pack.mcmeta");

	VpkgResourcePack(ResourcePackInfo info, VpkgArchive archive, String description, VpkgCapabilityGrant grant) {
		this(info, archive, description, grant, ResourceType.CLIENT_RESOURCES);
	}

	VpkgResourcePack(ResourcePackInfo info, VpkgArchive archive, String description, VpkgCapabilityGrant grant,
			ResourceType packType) {
		this.packType = Objects.requireNonNull(packType, "packType");
		this.info = info;
		this.archive = archive;
		this.description = description;
		this.grant = Objects.requireNonNull(grant, "grant");
	}

	/**
	 * Welche {@link VpkgCapability} ein Eintrag unter {@code assets/<namespace>/<relativePath>}
	 * tatsaechlich braucht (GATE32). {@code relativePath} ist der Teil hinter
	 * {@code assets/<namespace>/}, also z. B. {@code "lang/de_de.json"} oder
	 * {@code "textures/gui/widgets.png"}.
	 *
	 * <p>{@link VpkgCapability#COSMETICS} hat heute noch keinen eigenen
	 * Vanilla-Ressourcen-Pfad (PLAN.md: "eigener Renderpfad", noch nicht
	 * gebaut) -- alles, was hier ankommt und keiner der spezifischeren
	 * Kategorien entspricht, ist gewoehnlicher Textur-/Modell-/Bild-Inhalt und
	 * braucht deshalb {@link VpkgCapability#TEXTUREN}.</p>
	 */
	/** Wurzel der Inhalte fuer {@link #packType}: {@code assets/} oder {@code data/}. */
	private String prefix() {
		return packType == ResourceType.SERVER_DATA ? DATA_PREFIX : ASSETS_PREFIX;
	}

	/** Benoetigte Faehigkeit: Datenpaket-Inhalte brauchen immer {@link VpkgCapability#VERHALTEN}. */
	private VpkgCapability requiredFor(String relativePath) {
		return packType == ResourceType.SERVER_DATA ? VpkgCapability.VERHALTEN : capabilityFor(relativePath);
	}

	private static VpkgCapability capabilityFor(String relativePath) {
		String lower = relativePath.toLowerCase(Locale.ROOT);
		if (lower.equals(SOUNDS_JSON) || lower.startsWith(SOUNDS_PREFIX)) {
			return VpkgCapability.SOUND;
		}
		if (lower.startsWith(LANG_PREFIX)) {
			return VpkgCapability.SPRACHE;
		}
		if (lower.startsWith(SHADERS_PREFIX)) {
			return VpkgCapability.SHADER;
		}
		return VpkgCapability.TEXTUREN;
	}

	@Override
	public ResourcePackInfo getInfo() {
		return info;
	}

	@Override
	public Set<String> getNamespaces(ResourceType type) {
		if (type != packType) {
			return Set.of();
		}
		Set<String> namespaces = new LinkedHashSet<>();
		for (VpkgEntry entry : archive.entries()) {
			if (entry.directory()) {
				continue;
			}
			String name = entry.name();
			if (!name.startsWith(prefix())) {
				continue;
			}
			String rest = name.substring(prefix().length());
			int slash = rest.indexOf('/');
			if (slash <= 0) {
				continue;
			}
			namespaces.add(rest.substring(0, slash));
		}
		return namespaces;
	}

	@Override
	public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
		if (type != packType) {
			return null;
		}
		String path = prefix() + id.getNamespace() + "/" + id.getPath();
		if (!archive.hasEntry(path)) {
			return null;
		}
		VpkgCapability required = requiredFor(id.getPath());
		if (!grant.isGranted(required)) {
			LOGGER.debug("Vitrine-Paket '{}' liefert '{}' nicht aus -- Faehigkeit '{}' nicht im Manifest "
					+ "angemeldet (Charter-Regel 4).", grant.packageId(), path, required.id());
			return null;
		}
		return () -> archive.openEntry(path);
	}

	@Override
	public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
		if (type != packType) {
			return;
		}
		String base = prefix() + namespace + "/";
		String dirPrefix = prefix.isEmpty() ? base : base + prefix + "/";
		for (VpkgEntry entry : archive.entries()) {
			if (entry.directory()) {
				continue;
			}
			String name = entry.name();
			if (!name.startsWith(dirPrefix)) {
				continue;
			}
			String relativePath = name.substring(base.length());
			VpkgCapability required = requiredFor(relativePath);
			if (!grant.isGranted(required)) {
				LOGGER.debug("Vitrine-Paket '{}' laesst '{}' bei findResources aus -- Faehigkeit '{}' nicht im "
						+ "Manifest angemeldet (Charter-Regel 4).", grant.packageId(), name, required.id());
				continue;
			}
			Identifier id;
			try {
				id = Identifier.of(namespace, relativePath);
			} catch (RuntimeException e) {
				LOGGER.warn("Vitrine-Paket-Eintrag '{}' ergibt keinen gueltigen Identifier, wird uebersprungen: {}",
						name, e.toString());
				continue;
			}
			consumer.accept(id, () -> archive.openEntry(name));
		}
	}

	@Override
	public InputSupplier<InputStream> openRoot(String... segments) {
		String path = String.join("/", segments);
		// GATE58: an der Paketwurzel liefert Vitrine nur die beiden Dateien aus,
		// die Vanilla dort ueberhaupt kennt. Alles andere ginge an der
		// Faehigkeiten-Pruefung von open/findResources vorbei.
		if (!ROOT_FILES.contains(path)) {
			return null;
		}
		if (!archive.hasEntry(path)) {
			return null;
		}
		return () -> archive.openEntry(path);
	}

	@Override
	public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) {
		if (!PackResourceMetadata.SERIALIZER.getKey().equals(metaReader.getKey())) {
			return null;
		}
		PackResourceMetadata metadata = new PackResourceMetadata(
				Text.literal(description), SharedConstants.getGameVersion().getResourceVersion(packType), Optional.empty());
		@SuppressWarnings("unchecked")
		T value = (T) metadata;
		return value;
	}

	private static int currentClientPackFormat() {
		return SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES);
	}

	@Override
	public void close() {
		try {
			archive.close();
		} catch (IOException e) {
			LOGGER.warn("Vitrine-Paket-Archiv '{}' liess sich beim Schliessen nicht sauber freigeben: {}",
					archive.path(), e.toString());
		}
	}

	@Override
	public String toString() {
		return "VpkgResourcePack[" + info.id().toLowerCase(Locale.ROOT) + "]";
	}
}
