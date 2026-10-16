package de.spassglas.vitrine.classb;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * ROADMAP J3 -- die produktive {@link InstalledModConflictCheck}: lehnt ein
 * {@link JavaModOffer} ab, dessen Mod-ID bereits von einem geladenen oder
 * vorgemerkten ("pending", noch nicht neugestarteten) Mod belegt ist, oder dem
 * eine harte {@link JavaModOffer#dependencies()}-Id fehlt.
 *
 * <p>"bereits installiert" heisst die Vereinigung aus zwei Quellen:</p>
 * <ul>
 *   <li>den vom {@code net.fabricmc.loader.api.FabricLoader} tatsaechlich
 *       geladenen Mods ({@link ModContainer#getMetadata()}, Id <b>und</b>
 *       {@code provides}-Aliase) -- ueber den injizierbaren Seam
 *       {@code installedModIds}, damit Tests ohne echten Loader headless
 *       laufen;</li>
 *   <li>den bereits in {@code staged-mods.json} vorgemerkten Jars (noch nicht
 *       geladen, aber schon "belegt") -- ueber {@code pendingModIds}.</li>
 * </ul>
 *
 * <p>Die Abhaengigkeitspruefung bleibt bewusst nah am Fabric-{@code depends}-
 * Modell, aber ohne Versions-Range-Parser: {@link JavaModOffer} traegt keine
 * Versionsnummer, also zaehlt nur, ob die Id ueberhaupt (installiert, pending,
 * oder im selben Angebot) vorhanden waere.</p>
 */
public final class FabricInstalledModConflictCheck implements InstalledModConflictCheck {

	private final Supplier<Set<String>> installedModIds;
	private final Supplier<Set<String>> pendingModIds;

	public FabricInstalledModConflictCheck(Supplier<Set<String>> installedModIds,
			Supplier<Set<String>> pendingModIds) {
		this.installedModIds = Objects.requireNonNull(installedModIds, "installedModIds");
		this.pendingModIds = Objects.requireNonNull(pendingModIds, "pendingModIds");
	}

	/**
	 * Produktions-Aufbau: installierte Ids ueber den echten
	 * {@code FabricLoader}, vorgemerkte Ids aus
	 * {@code stagingStateDir/staged-mods.json} (derselbe Ordner, den
	 * {@link JavaModStager#forGameDir} fuer den Staging-Zustand benutzt).
	 */
	public static FabricInstalledModConflictCheck production(Path stagingStateDir) {
		Objects.requireNonNull(stagingStateDir, "stagingStateDir");
		return new FabricInstalledModConflictCheck(
				FabricInstalledModConflictCheck::loadedModIdsFromFabricLoader,
				() -> pendingModIdsFrom(stagingStateDir));
	}

	@Override
	public void check(JavaModOffer offer) throws JavaModStagingException {
		Objects.requireNonNull(offer, "offer");

		Set<String> installed = new HashSet<>(orEmpty(installedModIds.get()));
		Set<String> pending = new HashSet<>(orEmpty(pendingModIds.get()));

		if (installed.contains(offer.id())) {
			throw new JavaModStagingException("Die Mod-ID '" + offer.id() + "' ist bereits von einem installierten "
					+ "Mod belegt -- das Jar wird nicht nach 'mods/' kopiert. Entferne erst den vorhandenen Mod "
					+ "oder waehle ein anderes Angebot.");
		}
		if (pending.contains(offer.id())) {
			throw new JavaModStagingException("Die Mod-ID '" + offer.id() + "' ist bereits durch ein vorgemerktes "
					+ "Jar in 'staged-mods.json' belegt -- erst zuruecknehmen oder neu starten, bevor ein weiteres "
					+ "Jar mit derselben ID gestaged wird.");
		}

		if (!offer.dependencies().isEmpty()) {
			Set<String> known = new HashSet<>(installed);
			known.addAll(pending);
			known.add(offer.id());
			List<String> missing = offer.dependencies().stream().filter(dep -> !known.contains(dep)).toList();
			if (!missing.isEmpty()) {
				throw new JavaModStagingException("Dem Jar '" + offer.id() + "' fehlen benoetigte Mods: "
						+ String.join(", ", missing) + " -- installiere sie zuerst, das Jar wird nicht nach "
						+ "'mods/' kopiert.");
			}
		}
	}

	private static Set<String> orEmpty(Set<String> maybeNull) {
		return maybeNull == null ? Set.of() : maybeNull;
	}

	private static Set<String> loadedModIdsFromFabricLoader() {
		Set<String> ids = new TreeSet<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			ids.add(container.getMetadata().getId());
			ids.addAll(container.getMetadata().getProvides());
		}
		return ids;
	}

	private static Set<String> pendingModIdsFrom(Path stagingStateDir) {
		Set<String> ids = new TreeSet<>();
		for (StagedMod mod : StagedModsStore.load(stagingStateDir)) {
			ids.add(mod.id());
		}
		return ids;
	}
}
