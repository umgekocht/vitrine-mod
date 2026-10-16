package de.spassglas.vitrine.pack;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParseException;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.state.PackState;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.EmbeddedSigningKey;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapabilityException;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgIntegrityException;
import de.spassglas.vitrine.vpkg.VpkgIntegritySidecar;
import de.spassglas.vitrine.vpkg.VpkgIntegrityValidator;
import de.spassglas.vitrine.vpkg.VpkgSecurityException;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Eigener {@link ResourcePackProvider} (ROADMAP H1, M2 "Hot-Apply"): liest
 * {@code config/vitrine/packs/} und meldet jedes dort liegende, gepruefte
 * {@code .vpkg} als Ressourcen-Pack-Profil an -- genau der Mechanismus, den
 * Vanillas eigener {@link net.minecraft.resource.ResourcePackManager} nutzt,
 * um Pakete in der Ressourcenpaket-Liste (Optionen -&gt; Ressourcenpakete)
 * anzuzeigen.
 *
 * <p><b>Sicherheit vor Bequemlichkeit (Charter-Regel 3), hart durchgesetzt:</b>
 * bevor auch nur ein {@link ResourcePackProfile} beim {@code profileAdder}
 * ankommt, muss ein {@code .vpkg} nacheinander bestehen:</p>
 * <ol>
 *   <li>{@link VpkgSecurityValidator#requireSafe} (P3) -- Zip-Slip, Zip-Bombe,
 *       verbotene Dateitypen, PNG-Massgrenzen.</li>
 *   <li>{@link ManifestParser#parse} -- gueltiges, schemakonformes {@code
 *       vitrine.json} (P1).</li>
 *   <li>{@link VpkgIntegrityValidator#requireValid} (P4) -- SHA-256-Pruefsumme
 *       aus der Begleitdatei {@link VpkgIntegritySidecar}, Ed25519-Signatur
 *       aber ausschliesslich gegen den im Jar eingebetteten, vertrauten
 *       Schluessel ({@link de.spassglas.vitrine.vpkg.EmbeddedSigningKey#load()}
 *       ) -- nie gegen einen vom Paket/Sidecar mitgelieferten Schluessel
 *       (GATE29).</li>
 *   <li>{@link VpkgCapabilityGrant#of(Manifest)} (P5) -- ein Manifest mit
 *       unbekannter Faehigkeit wird abgelehnt; die daraus gebaute Whitelist
 *       geht an {@link VpkgResourcePack}, das jeden Ressourcen-Eintrag erst
 *       gegen die tatsaechlich benoetigte Faehigkeit prueft, bevor er
 *       ausgeliefert wird (GATE32, Charter-Regel 4).</li>
 * </ol>
 * <p>Scheitert eine dieser Pruefungen, wird das jeweilige Paket uebersprungen
 * und die Ablehnung geloggt -- der ganze Ordner-Scan bricht dafuer nicht ab,
 * damit ein einzelnes kaputtes/boesartiges Paket nicht alle anderen mit sich
 * reisst. Es gibt bewusst keinen Pfad, auf dem ein ungeprueftes Paket
 * registriert wird ("kein 'vorlaeufig ungeprueft' zum Testen").</p>
 *
 * <p>Diese Klasse selbst aktiviert nichts und ruft nie
 * {@code MinecraftClient#reloadResources()} -- das eigentliche Anwenden zur
 * Laufzeit (Aktivieren, Reload, Auto-Rollback) ist H2/H3. H1 sorgt nur dafuer,
 * dass ein sicheres, gueltiges Paket ueberhaupt als Profil *existiert* und in
 * der Vanilla-Pack-Liste auftaucht (siehe H1-Fertig-Kriterium).</p>
 *
 * <p><b>Anmelde-Reihenfolge (ROADMAP S5, "Abschnitt Reihenfolge"):</b> ist
 * eine {@code state.json} (H4) vorhanden, meldet dieser Provider die dort
 * installierten Pakete in <em>umgekehrter</em> {@link PackState}-Listenreihenfolge
 * an -- der in {@link PackState#packs} <b>zuerst</b> stehende Eintrag (oben in
 * der Bibliotheks-Reihenfolge, "weiter oben gewinnt bei gleicher Textur") wird
 * damit als <b>letzter</b> beim {@code profileAdder} angemeldet. Das folgt der
 * Vanilla-Semantik: {@code NamespaceResourceManager} loest eine Ressource
 * gegen die zuletzt angewendete Pack-Ebene auf, das zuletzt einsortierte Pack
 * gewinnt also bei gleichem Ressourcenpfad. Andert der Nutzer die Reihenfolge
 * in der Bibliothek (schreibt {@link PackState} um), meldet der naechste Scan
 * die Profile in der neuen Reihenfolge an -- ein anderes Pack gewinnt, ohne
 * Neustart. Pakete, die (noch) nicht in {@code state.json} stehen, werden
 * unveraendert in Dateinamen-Reihenfolge und vor den zustandsgefuehrten
 * Paketen angemeldet (niedrigste Prioritaet), damit ein frisch hineinkopiertes
 * {@code .vpkg} weiterhin in der Liste auftaucht.</p>
 */
public final class VitrinePackProvider implements ResourcePackProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/PackProvider");

	private static final String VPKG_EXTENSION = ".vpkg";
	private static final String PACKS_SUBDIR = "packs";

	/** Vorangestellt an jede Paket-ID (aus {@code vitrine.json}), damit Vitrine-Profil-IDs nie mit Vanilla-/anderen Mod-IDs kollidieren. */
	private static final String PACK_ID_PREFIX = "vitrine/";

	/**
	 * Die Ressourcen-Pack-Profil-ID, unter der dieses Paket bei Vanillas
	 * {@code ResourcePackManager} angemeldet wird -- {@link #PACK_ID_PREFIX} +
	 * Manifest-ID. Derselbe Wert steht in {@code state.json} (H4) und wird an
	 * {@code HotApplyService}/{@code PackUninstallService} übergeben.
	 */
	public static String profileId(String manifestId) {
		return PACK_ID_PREFIX + Objects.requireNonNull(manifestId, "manifestId");
	}

	/**
	 * Haelt den einen Provider fuer den echten Client, ueber Fabrics
	 * Konfigurationsordner ermittelt (siehe {@code
	 * de.spassglas.vitrine.mixin.client.VanillaResourcePackProviderMixin},
	 * das diesen Provider bei Vanillas eigenem Scan der Ressourcenpakete
	 * mitlaufen laesst). Absichtlich eine eigene, verschachtelte Klasse
	 * ("initialization-on-demand holder", JLS 12.4.1) statt eines direkten
	 * statischen Feldes auf {@link VitrinePackProvider} selbst: ein
	 * statisches Feld hier wuerde bei *jeder* Klasseninitialisierung von
	 * {@link VitrinePackProvider} ausgewertet -- also auch dann, wenn nur
	 * {@link #forPacksDir(Path)} in einem Unit-Test aufgerufen wird, ganz
	 * ohne laufenden Fabric-Loader. {@link FabricLoader#getInstance()}
	 * wuerde dort mit einem kryptischen Fehler in der Klasseninitialisierung
	 * scheitern, statt dass der Test einfach nur einen anderen Ordner
	 * benutzt.
	 */
	private static final String STATE_FILE_NAME = "state.json";

	private static final class ClientInstanceHolder {
		static final VitrinePackProvider INSTANCE =
				forConfigDir(FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID));
	}

	/**
	 * Der eine Provider fuer den echten Client (siehe {@link
	 * ClientInstanceHolder}). Wird erst beim ersten Aufruf ausgewertet --
	 * unabhaengig von der Reihenfolge, in der Mod-Entrypoints laufen,
	 * Mixins koennen frueher feuern als {@link Vitrine#onInitializeClient()}.
	 */
	public static VitrinePackProvider clientInstance() {
		return ClientInstanceHolder.INSTANCE;
	}

	private static final class DataInstanceHolder {
		static final VitrinePackProvider INSTANCE = new VitrinePackProvider(
				FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID).resolve(PACKS_SUBDIR),
				FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID), ResourceType.SERVER_DATA);
	}

	/**
	 * ROADMAP AD1: derselbe Paketordner, aber fuer die Datenseite des
	 * integrierten Servers -- meldet nur Verhaltens-Addons (Faehigkeit
	 * {@code verhalten}) als Datenpakete an.
	 */
	public static VitrinePackProvider dataInstance() {
		return DataInstanceHolder.INSTANCE;
	}

	private static final ResourcePackSource SOURCE = new ResourcePackSource() {
		@Override
		public boolean canBeEnabledLater() {
			return true;
		}

		@Override
		public Text decorate(Text packName) {
			return Text.translatable("pack.nameAndSource", packName, Text.translatable("vitrine.pack.source"));
		}
	};

	private final Path packsDir;

	/**
	 * GATE39: Pruefergebnis je Paketdatei, gebunden an Groesse und
	 * Aenderungszeit von Paket <em>und</em> Integritaets-Beleg. Vorher lief bei
	 * jedem Reload und jedem Start fuer jede Datei die volle Kette -- ZIP
	 * oeffnen, Sicherheitspruefung, Manifest, die ganze Datei hashen, Signatur
	 * pruefen --, auf dem Client-Thread: 5000 Pakete ergaben rund 7,7 s
	 * Standbild. Unveraenderte Dateien werden jetzt nicht erneut geprueft;
	 * jede Aenderung an Paket oder Beleg verwirft das gespeicherte Urteil.
	 */
	private final ConcurrentHashMap<Path, ScanVerdict> scanCache = new ConcurrentHashMap<>();
	private final AtomicInteger fullValidations = new AtomicInteger();
	/**
	 * Vitrine-Konfigurationsordner, in dem die {@code state.json} (H4) mit der
	 * massgeblichen Paket-Reihenfolge liegt -- oder {@code null}, wenn dieser
	 * Provider ohne Zustandsbezug arbeitet (Tests ueber {@link #forPacksDir}).
	 */
	private final Path stateConfigDir;

	/** Client-Ressourcen oder (ROADMAP AD1) Server-Daten. */
	private final ResourceType packType;

	private VitrinePackProvider(Path packsDir, Path stateConfigDir) {
		this(packsDir, stateConfigDir, ResourceType.CLIENT_RESOURCES);
	}

	private VitrinePackProvider(Path packsDir, Path stateConfigDir, ResourceType packType) {
		this.packsDir = packsDir;
		this.stateConfigDir = stateConfigDir;
		this.packType = packType;
	}

	/** Test-Zugang: Provider fuer die Datenseite eines beliebigen Konfigurationsordners. */
	static VitrinePackProvider dataForConfigDir(Path vitrineConfigDir) {
		return new VitrinePackProvider(vitrineConfigDir.resolve(PACKS_SUBDIR), vitrineConfigDir,
				ResourceType.SERVER_DATA);
	}

	/** Provider fuer {@code vitrineConfigDir/packs} (im echten Spiel {@code config/vitrine/packs}). */
	public static VitrinePackProvider forConfigDir(Path vitrineConfigDir) {
		return new VitrinePackProvider(vitrineConfigDir.resolve(PACKS_SUBDIR), vitrineConfigDir);
	}

	/**
	 * Provider fuer einen beliebigen Ordner -- gedacht fuer Tests, die keinen
	 * echten Konfigurationsordner brauchen. Ohne {@code state.json}-Bezug: die
	 * Pakete werden in Dateinamen-Reihenfolge angemeldet.
	 */
	public static VitrinePackProvider forPacksDir(Path packsDir) {
		return new VitrinePackProvider(packsDir, null);
	}

	/** Der Ordner, den dieser Provider durchsucht. */
	public Path packsDir() {
		return packsDir;
	}

	@Override
	public void register(Consumer<ResourcePackProfile> profileAdder) {
		if (!Files.isDirectory(packsDir)) {
			LOGGER.debug("Vitrine-Paketordner '{}' existiert nicht, keine Pakete zu registrieren.", packsDir);
			return;
		}

		List<Path> candidates;
		try (Stream<Path> stream = Files.list(packsDir)) {
			candidates = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VPKG_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			LOGGER.error("Vitrine-Paketordner '{}' liess sich nicht auflisten: {}", packsDir, e.toString(), e);
			return;
		}

		// Verschwundene Dateien aus dem Cache werfen, damit er nicht waechst.
		scanCache.keySet().retainAll(new HashSet<>(candidates));

		PackState consentState = loadStateIfPresent();
		List<PendingRegistration> pending = new ArrayList<>();
		for (Path vpkgFile : candidates) {
			ScanVerdict verdict = verdictFor(vpkgFile);
			if (verdict.accepted() && hasConsent(vpkgFile, verdict.manifest(), consentState)
					&& (packType != ResourceType.SERVER_DATA
							|| verdict.grant().isGranted(de.spassglas.vitrine.vpkg.VpkgCapability.VERHALTEN))) {
				pending.add(new PendingRegistration(vpkgFile, verdict.manifest(), verdict.grant()));
			}
		}

		List<String> stateOrder = loadInstalledOrderIfPresent();
		if (stateOrder != null) {
			// Stabil sortieren (Dateinamen-Reihenfolge bleibt bei gleichem Rang
			// erhalten): unbekannte Pakete zuerst, danach die zustandsgefuehrten
			// in UMGEKEHRTER PackState-Reihenfolge -- siehe Klassendoku
			// "Anmelde-Reihenfolge" (weiter oben in der Bibliothek = gewinnt).
			pending.sort(Comparator.comparingInt(p -> registrationRank(p.manifest().id(), stateOrder)));
		}

		for (PendingRegistration p : pending) {
			registerOne(p.file(), p.manifest(), p.grant(), profileAdder);
		}
	}

	/**
	 * Ein Paket-Kandidat, dessen Zip-Sicherheit (P3), Manifest (P1),
	 * Pruefsumme/Signatur (GATE29) und Faehigkeiten (GATE32) bereits geprueft sind.
	 */
	private record PendingRegistration(Path file, Manifest manifest, VpkgCapabilityGrant grant) {
	}

	/** GATE39: woran ein gespeichertes Urteil gebunden ist. */
	private record ScanKey(long size, FileTime modified, long sidecarSize, FileTime sidecarModified) {
	}

	/** GATE39: gespeichertes Urteil; {@code manifest == null} heisst abgelehnt. */
	private record ScanVerdict(ScanKey key, Manifest manifest, VpkgCapabilityGrant grant) {
		boolean accepted() {
			return manifest != null;
		}
	}

	/**
	 * Liefert das Urteil fuer {@code vpkgFile} -- aus dem Cache, wenn Paket und
	 * Beleg seit der letzten Pruefung unveraendert sind, sonst nach voller
	 * Pruefung. Laesst sich eine Datei nicht einmal ansehen (stat scheitert),
	 * wird voll geprueft und nichts gespeichert.
	 */
	private ScanVerdict verdictFor(Path vpkgFile) {
		ScanKey key = scanKeyOf(vpkgFile);
		if (key != null) {
			ScanVerdict cached = scanCache.get(vpkgFile);
			if (cached != null && cached.key().equals(key)) {
				return cached;
			}
		}
		fullValidations.incrementAndGet();
		Manifest manifest = openAndValidate(vpkgFile);
		VpkgCapabilityGrant grant = manifest == null ? null : verifyIntegrityAndCapabilities(vpkgFile, manifest);
		ScanVerdict verdict = new ScanVerdict(key, grant == null ? null : manifest, grant);
		if (key != null) {
			scanCache.put(vpkgFile, verdict);
		}
		return verdict;
	}

	private static ScanKey scanKeyOf(Path vpkgFile) {
		try {
			BasicFileAttributes pkg = Files.readAttributes(vpkgFile, BasicFileAttributes.class);
			Path sidecar = VpkgIntegritySidecar.sidecarPathFor(vpkgFile);
			long sidecarSize = -1;
			FileTime sidecarModified = FileTime.fromMillis(0);
			if (Files.isRegularFile(sidecar)) {
				BasicFileAttributes side = Files.readAttributes(sidecar, BasicFileAttributes.class);
				sidecarSize = side.size();
				sidecarModified = side.lastModifiedTime();
			}
			return new ScanKey(pkg.size(), pkg.lastModifiedTime(), sidecarSize, sidecarModified);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Wie oft seit dem Erzeugen dieses Providers ein Paket <em>voll</em> geprueft
	 * wurde (ZIP, Manifest, Hash, Signatur). Fuer den GATE39-Nachweis, dass ein
	 * zweiter Scan ohne Aenderung keine dieser Pruefungen wiederholt.
	 */
	int fullValidationCount() {
		return fullValidations.get();
	}

	/**
	 * Sortierrang eines Pakets fuer die Anmelde-Reihenfolge: {@link
	 * Integer#MIN_VALUE} fuer ein Paket, das (noch) nicht in {@code state.json}
	 * steht (kommt damit zuerst, niedrigste Prioritaet), sonst {@code -index},
	 * sodass {@link PackState#packs}-Index 0 als letztes angemeldet wird und
	 * bei gleichem Ressourcenpfad gewinnt.
	 */
	private static int registrationRank(String manifestId, List<String> stateOrder) {
		int index = stateOrder.indexOf(PACK_ID_PREFIX + manifestId);
		return index < 0 ? Integer.MIN_VALUE : -index;
	}

	/**
	 * Liest die Paket-Reihenfolge aus {@code state.json} (H4), <em>ohne</em>
	 * Seiteneffekt: nur wenn die Datei tatsaechlich existiert -- ein fehlendes
	 * {@code state.json} wuerde {@link PackStateStore#load} sonst dazu bringen,
	 * einen leeren Default anzulegen und zu schreiben. Liefert {@code null},
	 * wenn dieser Provider ohne Zustandsbezug arbeitet ({@link #forPacksDir})
	 * oder noch keine {@code state.json} geschrieben wurde.
	 */
	/** Wie {@link #loadInstalledOrderIfPresent()}, aber der ganze Zustand; {@code null} ohne Zustandsbezug/Datei. */
	private PackState loadStateIfPresent() {
		if (stateConfigDir == null || !Files.isRegularFile(stateConfigDir.resolve(STATE_FILE_NAME))) {
			return null;
		}
		return PackStateStore.load(stateConfigDir);
	}

	/**
	 * GATE60: ein Paket, das Faehigkeiten anmeldet, wird nur ausgeliefert, wenn
	 * der Nutzer genau diesen Faehigkeiten zugestimmt hat -- wie auf dem
	 * regulaeren Installationsweg. Sonst koennte ein signiertes Paket, das
	 * jemand direkt in {@code packs/} legt, den Faehigkeiten-Dialog umgehen.
	 * Ohne Zustandsbezug ({@link #forPacksDir}, nur Tests) gilt die Pruefung nicht.
	 */
	private boolean hasConsent(Path vpkgFile, Manifest manifest, PackState state) {
		if (stateConfigDir == null) {
			return true;
		}
		List<String> capabilityIds;
		try {
			capabilityIds = PackInstaller.capabilityIdsOf(manifest);
		} catch (PackInstallException e) {
			LOGGER.warn("Vitrine-Paket '{}': Faehigkeiten nicht lesbar, wird nicht angemeldet: {}",
					vpkgFile, e.getMessage());
			return false;
		}
		if (capabilityIds.isEmpty()) {
			return true;
		}
		if (state != null && state.hasCapabilityConsent(manifest.id(), capabilityIds)) {
			return true;
		}
		LOGGER.warn("Vitrine-Paket '{}' ('{}') meldet Faehigkeiten {} an, denen nie zugestimmt wurde -- "
				+ "wird nicht angemeldet. Ueber den Store oder die Bibliothek installieren.",
				vpkgFile, manifest.id(), capabilityIds);
		return false;
	}

	private List<String> loadInstalledOrderIfPresent() {
		if (stateConfigDir == null) {
			return null;
		}
		if (!Files.isRegularFile(stateConfigDir.resolve(STATE_FILE_NAME))) {
			return null;
		}
		return PackStateStore.load(stateConfigDir).installedPackIdsInOrder();
	}

	/**
	 * Pruefsumme, Signatur (GATE29) und Faehigkeiten (GATE32). Liefert die
	 * Freigabe oder {@code null}, wenn das Paket abgelehnt wird (geloggt).
	 */
	private static VpkgCapabilityGrant verifyIntegrityAndCapabilities(Path vpkgFile, Manifest manifest) {
		if (!hasValidIntegrity(vpkgFile)) {
			return null;
		}

		// GATE32: die Faehigkeiten-Whitelist (P5, Charter-Regel 4) steuert, welche
		// assets/**-Eintraege dieses Pakets spaeter ueberhaupt ausgeliefert werden
		// duerfen (siehe VpkgResourcePack) -- ein Manifest mit unbekannter
		// Faehigkeit wird, wie bei jeder anderen Pruefung hier, gar nicht erst
		// angemeldet.
		try {
			return VpkgCapabilityGrant.of(manifest);
		} catch (VpkgCapabilityException e) {
			LOGGER.warn("Vitrine-Paket '{}' meldet ungueltige Faehigkeiten an, wird nicht angemeldet: {}",
					vpkgFile, e.getMessage());
			return null;
		}
	}

	/**
	 * Pruefsumme (SHA-256) + Ed25519-Signatur (GATE29), ausschliesslich gegen
	 * den im Jar eingebetteten, vertrauten Schluessel ({@link
	 * EmbeddedSigningKey#load()}) -- nie gegen einen vom Paket/Sidecar
	 * mitgelieferten Schluessel. <b>Oeffentlich</b>, damit die Theme-
	 * ({@link ThemePackCoordinator}, GATE103) und HUD-Aktivierung ({@code
	 * HudActivation}, GATE104) dieselbe Pruefung aufrufen wie das Anmelden als
	 * Ressourcen-Pack -- statt einen zweiten, schwaecheren Weg zu bauen.
	 */
	public static boolean hasValidIntegrity(Path vpkgFile) {
		VpkgIntegritySidecar.Content integrity = VpkgIntegritySidecar.read(vpkgFile);
		try {
			VpkgIntegrityValidator.requireValid(vpkgFile, integrity.sha256(), integrity.signature(),
					EmbeddedSigningKey.load());
			return true;
		} catch (VpkgIntegrityException e) {
			LOGGER.warn("Vitrine-Paket '{}' hat die Pruefsummen-/Signaturpruefung nicht bestanden: {}",
					vpkgFile, e.getMessage());
			return false;
		}
	}

	/**
	 * GATE60/GATE105: oeffentliche Fassung von {@link #hasConsent}, damit die
	 * Theme- ({@link ThemePackCoordinator}) und HUD-Aktivierung ({@code
	 * HudActivation}) dieselbe Faehigkeiten-Zustimmungspruefung nutzen wie das
	 * Anmelden als Ressourcen-Pack -- statt einen zweiten, schwaecheren Weg zu
	 * bauen. Ohne Zustandsbezug ({@link #forPacksDir}, nur Tests) gilt die
	 * Pruefung nicht (liefert {@code true}).
	 */
	public boolean hasCapabilityConsent(Path vpkgFile, Manifest manifest) {
		return hasConsent(vpkgFile, manifest, loadStateIfPresent());
	}

	private void registerOne(Path vpkgFile, Manifest manifest, VpkgCapabilityGrant grant,
			Consumer<ResourcePackProfile> profileAdder) {
		String packId = PACK_ID_PREFIX + manifest.id();
		ResourcePackInfo info = new ResourcePackInfo(packId, Text.literal(manifest.name()), SOURCE, Optional.empty());
		ResourcePackProfile.PackFactory factory = new VpkgPackFactory(vpkgFile, manifest.name(), grant, packType);
		// Nicht erforderlich, nicht fixiert: der Nutzer entscheidet in der
		// Bibliothek (H4/S5) ueber Aktivierung und Reihenfolge -- H1 sorgt nur
		// dafuer, dass das Profil ueberhaupt existiert und auswaehlbar ist.
		ResourcePackPosition position =
				new ResourcePackPosition(false, ResourcePackProfile.InsertionPosition.TOP, false);

		ResourcePackProfile profile;
		try {
			profile = ResourcePackProfile.create(info, factory, packType, position);
		} catch (RuntimeException e) {
			LOGGER.error("Vitrine-Paket '{}' liess sich nicht als Ressourcen-Pack-Profil aufbauen: {}",
					vpkgFile, e.toString(), e);
			return;
		}
		if (profile == null) {
			LOGGER.warn("Vitrine-Paket '{}' ergab kein Ressourcen-Pack-Profil (Metadaten nicht lesbar).", vpkgFile);
			return;
		}

		LOGGER.info("Vitrine-Paket '{}' (Datei '{}') als Ressourcen-Pack angemeldet.", manifest.id(), vpkgFile);
		profileAdder.accept(profile);
	}

	/**
	 * Sucht in {@link #packsDir} nach der einzigen {@code .vpkg}-Datei, deren
	 * Manifest-ID (mit {@link #PACK_ID_PREFIX} versehen) zu {@code packId}
	 * passt -- ROADMAP H5 ("Deinstallieren"): weder {@code state.json} noch
	 * die Ressourcen-Pack-Profil-ID kennen den frei waehlbaren Dateinamen (die
	 * Vitrine-CLI laesst den beim {@code pack}-Befehl frei waehlen), deshalb
	 * muss die zugehoerige Datei ueber ihr Manifest wiedergefunden werden, um
	 * sie in den Papierkorb verschieben zu koennen.
	 *
	 * <p>Liefert {@link Optional#empty()}, wenn der Ordner fehlt oder kein
	 * gueltiges Paket mit dieser ID mehr darin liegt (z. B. schon verschoben)
	 * -- das ist kein Fehler, nur ein Zeichen, dass es nichts mehr zu
	 * verschieben gibt.</p>
	 */
	public Optional<Path> findPackFile(String packId) {
		Objects.requireNonNull(packId, "packId");
		if (!Files.isDirectory(packsDir)) {
			return Optional.empty();
		}

		List<Path> candidates;
		try (Stream<Path> stream = Files.list(packsDir)) {
			candidates = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(VPKG_EXTENSION))
					.sorted()
					.toList();
		} catch (IOException e) {
			LOGGER.error("Vitrine-Paketordner '{}' liess sich beim Suchen nach '{}' nicht auflisten: {}",
					packsDir, packId, e.toString(), e);
			return Optional.empty();
		}

		for (Path candidate : candidates) {
			Manifest manifest = openAndValidate(candidate);
			if (manifest != null && (PACK_ID_PREFIX + manifest.id()).equals(packId)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	/** Oeffnet {@code vpkgFile}, prueft Sicherheit (P3) und Manifest (P1); liefert {@code null} und loggt bei jeder Ablehnung. */
	private static Manifest openAndValidate(Path vpkgFile) {
		try (VpkgArchive archive = VpkgArchive.open(vpkgFile)) {
			VpkgSecurityValidator.requireSafe(archive);
			return ManifestParser.parse(archive.readManifestBytes());
		} catch (IOException e) {
			LOGGER.warn("Vitrine-Paket '{}' liess sich nicht als ZIP-Archiv oeffnen oder lesen: {}",
					vpkgFile, e.toString());
			return null;
		} catch (VpkgSecurityException e) {
			LOGGER.warn("Vitrine-Paket '{}' hat die Sicherheitspruefung nicht bestanden, wird nicht angemeldet: {}",
					vpkgFile, e.getMessage());
			return null;
		} catch (ManifestParseException e) {
			LOGGER.warn("Vitrine-Paket '{}' hat ein ungueltiges Manifest, wird nicht angemeldet: {}",
					vpkgFile, e.getMessage());
			return null;
		}
	}

	/**
	 * Oeffnet fuer jeden Aufruf ein frisches {@link VpkgArchive} -- {@link
	 * ResourcePack}s sind laut Vanilla-Vertrag Einwegobjekte (siehe
	 * {@link VpkgResourcePack}s Klassendoku); {@link ResourcePackProfile}
	 * ruft die Fabrik sowohl beim Anlegen (Metadaten lesen, sofort wieder
	 * geschlossen) als auch bei jedem spaeteren Reload erneut auf.
	 */
	private static final class VpkgPackFactory implements ResourcePackProfile.PackFactory {

		private final Path vpkgFile;
		private final String displayName;
		private final VpkgCapabilityGrant grant;
		private final ResourceType packType;

		private VpkgPackFactory(Path vpkgFile, String displayName, VpkgCapabilityGrant grant, ResourceType packType) {
			this.vpkgFile = vpkgFile;
			this.displayName = displayName;
			this.grant = grant;
			this.packType = packType;
		}

		@Override
		public ResourcePack open(ResourcePackInfo info) {
			return openArchive(info);
		}

		@Override
		public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
			// .vpkg kennt keine Overlays (kein Feld dafuer im Manifest-Schema,
			// P1) -- dieselbe einfache Oeffnung wie open(...) reicht.
			return openArchive(info);
		}

		private ResourcePack openArchive(ResourcePackInfo info) {
			try {
				VpkgArchive archive = VpkgArchive.open(vpkgFile);
				return new VpkgResourcePack(info, archive, displayName, grant, packType);
			} catch (IOException e) {
				// PackFactory#open darf laut Vanilla-Schnittstelle keine
				// gepruefte Exception werfen -- geloggt, dann als
				// RuntimeException weitergereicht, die registerOne() bereits
				// auffaengt (kein verschluckter Fehler).
				LOGGER.error("Vitrine-Paket '{}' liess sich beim erneuten Oeffnen nicht lesen: {}",
						vpkgFile, e.toString(), e);
				throw new UncheckedIOException(
						"Vitrine-Paket '" + vpkgFile + "' liess sich nicht erneut oeffnen", e);
			}
		}
	}
}
