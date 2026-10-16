package de.spassglas.vitrine.hud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Alle Nutzer-Anpassungen an den aktiven HUD-Widgets (ROADMAP T6,
 * In-Game-HUD-Editor), getrennt vom schreibgeschuetzten Paket-Layout. Pro
 * {@code (packId, widgetId)} genau eine {@link HudWidgetOverride}.
 *
 * <p>Client-frei: kein Minecraft-Typ, damit "verschieben ueberlebt Neustart"
 * headless pruefbar ist. Persistenz laeuft ueber {@link HudOverridesStore}
 * (atomar nach {@code config/vitrine/hud/overrides.json}, gleiches Muster wie
 * {@code SettingsManager}/{@code PackStateStore}). Eine kaputte Datei fuehrt zu
 * leeren Overrides + Backup, nie zu einem Absturz.</p>
 *
 * <p>Aenderungen ({@code setOffset}/{@code setAnchor}/{@code setVisible}/
 * {@code clear}) wirken zunaechst nur im Speicher; erst {@link #save()}
 * schreibt sie (Editor-Knopf "Speichern"). Eine ueber {@link #empty()}
 * erzeugte, an keinen Ordner gebundene Instanz ignoriert {@link #save()}.</p>
 */
public final class HudLayoutOverrides {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");

	/** Schluessel einer Anpassung: Paket-Manifest-Id + Widget-Id. */
	public record Key(String packId, String widgetId) {
		public Key {
			Objects.requireNonNull(packId, "packId");
			Objects.requireNonNull(widgetId, "widgetId");
		}
	}

	private final Map<Key, HudWidgetOverride> byKey;
	private final Path vitrineConfigDir;

	HudLayoutOverrides(Path vitrineConfigDir, Map<Key, HudWidgetOverride> initial) {
		this.vitrineConfigDir = vitrineConfigDir;
		this.byKey = new LinkedHashMap<>(initial);
	}

	/** Leere, an keinen Konfigordner gebundene Overrides (Default in {@link ActiveHudRegistry}). */
	public static HudLayoutOverrides empty() {
		return new HudLayoutOverrides(null, Map.of());
	}

	/**
	 * Laedt die Overrides aus {@code vitrineConfigDir/hud/overrides.json}. Wirft
	 * nie -- jeder Fehlerfall faellt auf leere Overrides + Backup zurueck.
	 *
	 * @param vitrineConfigDir der Vitrine-eigene Konfigordner (z. B.
	 *                          {@code config/vitrine}), nicht die Datei selbst.
	 */
	public static HudLayoutOverrides forConfigDir(Path vitrineConfigDir) {
		return HudOverridesStore.load(Objects.requireNonNull(vitrineConfigDir, "vitrineConfigDir"));
	}

	/** Die Anpassung fuer {@code (packId, widgetId)}, falls es eine gibt. */
	public Optional<HudWidgetOverride> find(String packId, String widgetId) {
		return Optional.ofNullable(byKey.get(new Key(packId, widgetId)));
	}

	public boolean isEmpty() {
		return byKey.isEmpty();
	}

	/** Unveraenderliche Sicht auf alle Anpassungen (fuer {@link HudOverridesStore} und Tests). */
	public Map<Key, HudWidgetOverride> asMap() {
		return Map.copyOf(byKey);
	}

	/**
	 * Das effektive Widget: Paket-Layout {@code widget} plus die Anpassung fuer
	 * {@code (packId, widget.id())}, falls vorhanden. Ohne Anpassung kommt
	 * {@code widget} unveraendert zurueck.
	 */
	public HudWidget apply(String packId, HudWidget widget) {
		return HudOverrideResolver.resolve(widget, byKey.get(new Key(packId, widget.id())));
	}

	/** Verschiebt: setzt den Versatz-Override. */
	public void setOffset(String packId, String widgetId, HudOffset offset) {
		Objects.requireNonNull(offset, "offset");
		mutate(packId, widgetId, o -> o.withOffset(offset));
	}

	/** Richtet aus: setzt den Anker-Override. */
	public void setAnchor(String packId, String widgetId, HudAnchor anchor) {
		Objects.requireNonNull(anchor, "anchor");
		mutate(packId, widgetId, o -> o.withAnchor(anchor));
	}

	/**
	 * Schaltet an/aus: {@code TRUE} = immer zeigen, {@code FALSE} = nie zeigen,
	 * {@code null} = zurueck auf die Paket-Sichtbarkeit.
	 */
	public void setVisible(String packId, String widgetId, Boolean visible) {
		mutate(packId, widgetId, o -> o.withVisible(visible));
	}

	/** Verwirft alle Anpassungen fuer ein Widget (zurueck auf das reine Paket-Layout). */
	public void clear(String packId, String widgetId) {
		byKey.remove(new Key(packId, widgetId));
	}

	private void mutate(String packId, String widgetId, UnaryOperator<HudWidgetOverride> op) {
		Key key = new Key(requireText(packId, "packId"), requireText(widgetId, "widgetId"));
		HudWidgetOverride updated = op.apply(byKey.getOrDefault(key, HudWidgetOverride.EMPTY));
		if (updated.isEmpty()) {
			byKey.remove(key);
		} else {
			byKey.put(key, updated);
		}
	}

	/**
	 * Schreibt die Overrides atomar nach {@code config/vitrine/hud/overrides.json}.
	 * Eine ueber {@link #empty()} erzeugte Instanz hat keinen Ordner -- dann
	 * passiert nichts (geloggt).
	 */
	public void save() {
		if (vitrineConfigDir == null) {
			LOGGER.warn("HudLayoutOverrides.save() ohne Konfigordner -- nichts geschrieben (empty()-Instanz).");
			return;
		}
		HudOverridesStore.save(vitrineConfigDir, this);
	}

	Path vitrineConfigDir() {
		return vitrineConfigDir;
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " darf nicht leer sein");
		}
		return value;
	}
}
