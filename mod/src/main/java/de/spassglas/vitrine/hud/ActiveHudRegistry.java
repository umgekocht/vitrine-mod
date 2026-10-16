package de.spassglas.vitrine.hud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hält die HUD-Layouts der gerade aktiven Pakete (ROADMAP T5). Der
 * HUD-Renderer zeichnet ausschließlich aus dieser Registry -- und hier landet
 * ein Paket nur über {@link #activate(HudPackage)}, das der
 * Aktivierungs-/Hot-Apply-Weg aufruft, nachdem {@link HudPackageLoader} die
 * M1-Kette (Zip-Sicherheit, Fähigkeit {@code hud}) durchgesetzt hat. Es gibt
 * keinen Pfad, über den roher Dateiinhalt hier hineinkommt.
 *
 * <p>Reihenfolge = Aktivierungsreihenfolge (späteres Paket zeichnet über
 * früherem, analog zur Ressourcen-Pack-Reihenfolge in S5/H4).</p>
 */
public final class ActiveHudRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Hud");

	private static final ActiveHudRegistry INSTANCE = new ActiveHudRegistry();

	private final Map<String, HudPackage> activeByPackageId = new LinkedHashMap<>();

	/**
	 * Nutzer-Anpassungen aus dem In-Game-Editor (ROADMAP T6), die beim
	 * Aufloesen ueber das Paket-Layout gelegt werden. Default: keine
	 * Anpassungen -- dann verhaelt sich die Registry exakt wie unter T5.
	 */
	private HudLayoutOverrides overrides = HudLayoutOverrides.empty();

	private ActiveHudRegistry() {
	}

	/** Die eine Registry für den laufenden Client. */
	public static ActiveHudRegistry instance() {
		return INSTANCE;
	}

	/** Nimmt ein geladenes, geprüftes HUD-Paket auf (bzw. ersetzt eine ältere Fassung derselben Id). */
	public synchronized void activate(HudPackage hudPackage) {
		activeByPackageId.put(hudPackage.packageId(), hudPackage);
		LOGGER.info("HUD-Paket '{}' aktiv ({} Widget(s) insgesamt jetzt aktiv).",
				hudPackage.packageId(), totalWidgetCount());
	}

	/** Entfernt das HUD-Paket mit dieser Id wieder (Deaktivieren/Deinstallieren, H5). */
	public synchronized void deactivate(String packageId) {
		if (activeByPackageId.remove(packageId) != null) {
			LOGGER.info("HUD-Paket '{}' deaktiviert.", packageId);
		}
	}

	/**
	 * Alle Widgets aller aktiven Pakete, in Aktivierungs- und
	 * Zeichenreihenfolge, mit den Nutzer-Overrides (ROADMAP T6) bereits
	 * daruebergelegt. Ohne gesetzte Overrides kommt exakt das Paket-Layout
	 * zurueck (T5-Verhalten).
	 */
	public synchronized List<HudWidget> activeWidgets() {
		List<HudWidget> widgets = new ArrayList<>();
		for (HudPackage hudPackage : activeByPackageId.values()) {
			for (HudWidget widget : hudPackage.allWidgets()) {
				widgets.add(overrides.apply(hudPackage.packageId(), widget));
			}
		}
		return widgets;
	}

	/**
	 * Alle aktiven Widgets als {@link HudWidgetRef} (Paket-Id + unveraendertes
	 * Paket-Widget, <em>ohne</em> Overrides) -- die Eingabe fuer den
	 * In-Game-Editor (ROADMAP T6).
	 */
	public synchronized List<HudWidgetRef> activeWidgetRefs() {
		List<HudWidgetRef> refs = new ArrayList<>();
		for (HudPackage hudPackage : activeByPackageId.values()) {
			for (HudWidget widget : hudPackage.allWidgets()) {
				refs.add(new HudWidgetRef(hudPackage.packageId(), widget));
			}
		}
		return refs;
	}

	/** Setzt die Nutzer-Overrides, die {@link #activeWidgets()} ueber das Paket-Layout legt. */
	public synchronized void setOverrides(HudLayoutOverrides newOverrides) {
		this.overrides = newOverrides != null ? newOverrides : HudLayoutOverrides.empty();
		LOGGER.info("HUD-Overrides aktualisiert: {} Eintrag/Eintraege.", this.overrides.asMap().size());
	}

	/** Die aktuell wirksamen Nutzer-Overrides. */
	public synchronized HudLayoutOverrides overrides() {
		return overrides;
	}

	/** Ob überhaupt ein HUD-Paket aktiv ist (der Renderer kann sonst früh aussteigen). */
	public synchronized boolean isEmpty() {
		return activeByPackageId.isEmpty();
	}

	private int totalWidgetCount() {
		return activeByPackageId.values().stream().mapToInt(pkg -> pkg.allWidgets().size()).sum();
	}
}
