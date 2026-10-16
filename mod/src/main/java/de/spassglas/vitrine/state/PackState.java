package de.spassglas.vitrine.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Inhalt von {@code config/vitrine/state.json} (ROADMAP H4): welche
 * Vitrine-Paket-IDs installiert sind, welche davon aktiv sind, und in
 * welcher Reihenfolge -- die Reihenfolge der {@link #packs}-Liste selbst ist
 * die massgebliche Reihenfolge (wichtig fuer die spaetere Konfliktaufloesung
 * bei "wer gewinnt, wenn zwei Pakete dieselbe Textur ueberschreiben", siehe
 * H5/S5).
 *
 * <p>Neue Felder kommen als zusaetzliche, mit sinnvollem Default versehene
 * Member dazu. Aendert sich die Bedeutung eines bestehenden Feldes so, dass
 * alte Dateien nicht mehr direkt lesbar sind, steigt
 * {@link #CURRENT_SCHEMA_VERSION} und in {@link PackStateSchemaMigrations#STEPS}
 * kommt ein Umwandlungsschritt fuer die alte Version dazu -- siehe dort.</p>
 */
public final class PackState {

	/** Aktuelle Schema-Version. Muss zu {@link PackStateSchemaMigrations#STEPS} passen. */
	public static final int CURRENT_SCHEMA_VERSION = 1;

	/**
	 * Wert von {@link PackEntry#source}, den {@link
	 * de.spassglas.vitrine.pack.PackInstaller} und {@link
	 * de.spassglas.vitrine.pack.BundleInstaller} setzen -- der heute einzige
	 * verdrahtete Installationsweg, ROADMAP X3: "Quelle" fuer den
	 * Diagnose-Bildschirm.
	 */
	public static final String SOURCE_LOCAL_LIBRARY = "library";

	/**
	 * Praefix von {@link PackEntry#source}, gefolgt vom Host, von dem {@link
	 * de.spassglas.vitrine.pack.PackUpdater} die zuletzt eingespielte Version
	 * tatsaechlich heruntergeladen hat (ROADMAP K5/X3). Solange der
	 * Produktions-{@code PackageVersionSource} noch nichts liefert
	 * (Minimalpfad), kommt dieser Fall real noch nicht vor -- der Code ist
	 * trotzdem schon ehrlich verdrahtet, statt einen erfundenen Wert
	 * anzuzeigen.
	 */
	public static final String SOURCE_CATALOG_PREFIX = "catalog:";

	public int schemaVersion = CURRENT_SCHEMA_VERSION;

	/**
	 * Installierte Pakete in ihrer massgeblichen Reihenfolge. Ein Eintrag in
	 * dieser Liste bedeutet "installiert"; {@link PackEntry#active} sagt,
	 * ob es zusaetzlich aktiv ist.
	 */
	public List<PackEntry> packs = new ArrayList<>();

	/**
	 * Merkt sich, welchen Fähigkeiten der Nutzer je Paket-ID schon einmal
	 * zugestimmt hat (ROADMAP S4, Charter-Regel 4: "Default ist Verbot"). Der
	 * Fähigkeiten-Dialog erscheint nur vor der <em>ersten</em> Installation
	 * eines Pakets -- und erneut, sobald sich die im Manifest angemeldete
	 * Fähigkeitsmenge gegenüber der zuletzt zugestimmten unterscheidet.
	 *
	 * <p>Bewusst getrennt von {@link #packs} geführt: eine erteilte Zustimmung
	 * überlebt ein Deinstallieren ({@link #remove}) -- wer ein Paket erneut aus
	 * derselben Bibliothek installiert, dessen angemeldete Fähigkeiten sich
	 * nicht geändert haben, wird nicht abermals gefragt.</p>
	 */
	public List<CapabilityConsent> capabilityConsents = new ArrayList<>();

	public PackState() {
		// Gson braucht einen No-Arg-Konstruktor zum Deserialisieren.
	}

	public static PackState createDefault() {
		return new PackState();
	}

	/** {@code true}, wenn {@code packId} in {@link #packs} vorkommt (unabhaengig vom aktiven Zustand). */
	public boolean isInstalled(String packId) {
		return findEntry(packId) != null;
	}

	/** {@code true}, wenn {@code packId} installiert und aktiv ist. */
	public boolean isActive(String packId) {
		PackEntry entry = findEntry(packId);
		return entry != null && entry.active;
	}

	/** Installierte Paket-IDs in ihrer massgeblichen Reihenfolge. */
	public List<String> installedPackIdsInOrder() {
		List<String> ids = new ArrayList<>(packs.size());
		for (PackEntry entry : packs) {
			ids.add(entry.packId);
		}
		return ids;
	}

	/** Aktive Paket-IDs, in derselben Reihenfolge wie {@link #packs}. */
	public List<String> activePackIdsInOrder() {
		List<String> ids = new ArrayList<>();
		for (PackEntry entry : packs) {
			if (entry.active) {
				ids.add(entry.packId);
			}
		}
		return ids;
	}

	/**
	 * Merkt {@code packId} als installiert vor (haengt es ans Ende der
	 * Reihenfolge an, falls noch nicht vorhanden) und setzt seinen aktiven
	 * Zustand auf {@code active}. Bereits vorhandene Eintraege behalten ihre
	 * Position in der Reihenfolge, nur der aktive Zustand wird aktualisiert.
	 */
	public void addOrUpdate(String packId, boolean active) {
		addOrUpdate(packId, active, null);
	}

	/**
	 * Wie {@link #addOrUpdate(String, boolean)}, merkt sich zusaetzlich
	 * {@code source} (ROADMAP X3: {@link PackEntry#source}, "woher kam die
	 * Datei" fuer den Diagnose-Bildschirm) -- {@link #SOURCE_LOCAL_LIBRARY}
	 * oder {@link #SOURCE_CATALOG_PREFIX} plus Host.
	 *
	 * <p>Bei einem bereits vorhandenen Eintrag wird {@code source} nur
	 * uebernommen, wenn es nicht {@code null} ist -- ein blosser An-/Aus-Wechsel
	 * ({@link #addOrUpdate(String, boolean)}) darf die zuletzt bekannte Quelle
	 * nicht auf "unbekannt" zuruecksetzen.</p>
	 */
	public void addOrUpdate(String packId, boolean active, String source) {
		Objects.requireNonNull(packId, "packId");
		PackEntry existing = findEntry(packId);
		if (existing != null) {
			existing.active = active;
			if (source != null) {
				existing.source = source;
			}
			return;
		}
		PackEntry entry = new PackEntry(packId, active);
		entry.source = source;
		packs.add(entry);
	}

	/**
	 * ROADMAP K5 ("Rückfall auf die alte Version"): markiert {@code packId} als
	 * "Update fehlgeschlagen, alte Version läuft weiter" ({@code true}) oder
	 * loescht die Markierung ({@code false} -- nach einem erfolgreichen Update).
	 * Ein unbekanntes {@code packId} wird stillschweigend ignoriert.
	 */
	public void setUpdateFailed(String packId, boolean failed) {
		Objects.requireNonNull(packId, "packId");
		PackEntry entry = findEntry(packId);
		if (entry != null) {
			entry.updateFailed = failed;
		}
	}

	/** {@code true}, wenn fuer {@code packId} zuletzt ein Update fehlgeschlagen ist (ROADMAP K5). */
	public boolean hasFailedUpdate(String packId) {
		PackEntry entry = findEntry(packId);
		return entry != null && entry.updateFailed;
	}

	/** Installierte Paket-IDs, fuer die zuletzt ein Update fehlgeschlagen ist (Reihenfolge wie {@link #packs}). */
	public List<String> packIdsWithFailedUpdate() {
		List<String> ids = new ArrayList<>();
		for (PackEntry entry : packs) {
			if (entry.updateFailed) {
				ids.add(entry.packId);
			}
		}
		return ids;
	}

	/**
	 * Entfernt {@code packId} vollstaendig aus dem Zustand (Deinstallieren,
	 * ROADMAP H5).
	 *
	 * @return {@code true}, wenn ein Eintrag entfernt wurde.
	 */
	public boolean remove(String packId) {
		return packs.removeIf(entry -> entry.packId.equals(packId));
	}

	/**
	 * {@code true}, wenn der Nutzer {@code packId} bereits <em>exakt</em> für
	 * die übergebene Fähigkeitsmenge zugestimmt hat. Reihenfolge und Dubletten
	 * sind egal -- verglichen wird die Menge. Eine leere angeforderte Menge
	 * (Paket meldet keine Fähigkeit an) gilt als zustimmungsfrei und liefert
	 * immer {@code true}.
	 */
	public boolean hasCapabilityConsent(String packId, Collection<String> requestedCapabilityIds) {
		Objects.requireNonNull(packId, "packId");
		Objects.requireNonNull(requestedCapabilityIds, "requestedCapabilityIds");
		Set<String> requested = new HashSet<>(requestedCapabilityIds);
		if (requested.isEmpty()) {
			return true;
		}
		for (CapabilityConsent consent : capabilityConsents) {
			if (consent != null && packId.equals(consent.packId)
					&& new HashSet<>(consent.capabilities()).equals(requested)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Hält fest, dass der Nutzer {@code packId} für genau {@code
	 * grantedCapabilityIds} zugestimmt hat. Ein vorhandener Eintrag für dieselbe
	 * Paket-ID wird ersetzt (die zuletzt zugestimmte Menge gilt).
	 */
	public void recordCapabilityConsent(String packId, Collection<String> grantedCapabilityIds) {
		Objects.requireNonNull(packId, "packId");
		Objects.requireNonNull(grantedCapabilityIds, "grantedCapabilityIds");
		capabilityConsents.removeIf(consent -> consent == null || packId.equals(consent.packId));
		capabilityConsents.add(new CapabilityConsent(packId, new ArrayList<>(grantedCapabilityIds)));
	}

	private PackEntry findEntry(String packId) {
		for (PackEntry entry : packs) {
			if (entry.packId.equals(packId)) {
				return entry;
			}
		}
		return null;
	}

	/** Prueft, dass keine Paket-ID doppelt vorkommt (Datei koennte sonst von aussen manipuliert worden sein). */
	boolean hasDuplicatePackIds() {
		Set<String> seen = new LinkedHashSet<>();
		for (PackEntry entry : packs) {
			if (entry.packId == null || !seen.add(entry.packId)) {
				return true;
			}
		}
		return false;
	}

	/** Ein einzelner installierter Eintrag: Paket-ID und ob er gerade aktiv ist. */
	public static final class PackEntry {

		public String packId;
		public boolean active;
		/**
		 * ROADMAP K5: {@code true}, wenn zuletzt ein Update dieses Pakets
		 * fehlgeschlagen ist und die alte Version weiterlaeuft. Additiv
		 * eingefuehrtes Feld -- eine vor K5 geschriebene {@code state.json} hat
		 * es nicht, Gson setzt es dann auf den Default {@code false}.
		 */
		public boolean updateFailed;
		/**
		 * ROADMAP X3 (Diagnose-Bildschirm, "welche Datei kam von wo"):
		 * {@link #SOURCE_LOCAL_LIBRARY} oder {@link #SOURCE_CATALOG_PREFIX} plus
		 * Host, oder {@code null}, wenn die Quelle nicht bekannt ist (z. B. ein
		 * Eintrag aus einer vor X3 geschriebenen {@code state.json}). Additiv
		 * eingefuehrtes Feld, wie {@link #updateFailed}.
		 */
		public String source;

		public PackEntry() {
			// Gson braucht einen No-Arg-Konstruktor zum Deserialisieren.
		}

		public PackEntry(String packId, boolean active) {
			this.packId = packId;
			this.active = active;
		}
	}

	/** Eine erteilte Fähigkeiten-Zustimmung: Paket-ID und die Menge der zugestimmten Fähigkeits-Bezeichner. */
	public static final class CapabilityConsent {

		public String packId;
		public List<String> capabilities = new ArrayList<>();

		public CapabilityConsent() {
			// Gson braucht einen No-Arg-Konstruktor zum Deserialisieren.
		}

		public CapabilityConsent(String packId, List<String> capabilities) {
			this.packId = packId;
			this.capabilities = capabilities;
		}

		/** Nie {@code null} -- ein fehlendes Feld aus einer alten Datei wird als leere Liste behandelt. */
		public List<String> capabilities() {
			return capabilities == null ? List.of() : capabilities;
		}
	}
}
