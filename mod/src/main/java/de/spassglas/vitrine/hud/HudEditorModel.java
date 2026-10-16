package de.spassglas.vitrine.hud;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-freie Logik des In-Game-HUD-Editors (ROADMAP T6): haelt die Liste der
 * anpassbaren Widgets, welches gerade ausgewaehlt ist, und schreibt jede
 * Aenderung in eine {@link HudLayoutOverrides}. Kein Minecraft-Typ -- damit
 * "verschieben / ausrichten / an-aus ueberlebt Neustart" ohne laufenden
 * Client pruefbar ist. {@code de.spassglas.vitrine.hud.client.HudEditorScreen}
 * bindet nur Maus, Tastatur und Knoepfe daran.
 *
 * <p>Die Paket-{@link HudLayout}s bleiben unberuehrt: der Editor liest die
 * unveraenderten Paket-Widgets ({@link HudWidgetRef}) und legt seine
 * Aenderungen getrennt in den Overrides ab.</p>
 */
public final class HudEditorModel {

	private final List<HudWidgetRef> widgets;
	private final HudLayoutOverrides overrides;
	private int selectedIndex;

	public HudEditorModel(List<HudWidgetRef> widgets, HudLayoutOverrides overrides) {
		this.widgets = List.copyOf(widgets);
		this.overrides = Objects.requireNonNull(overrides, "overrides");
		this.selectedIndex = this.widgets.isEmpty() ? -1 : 0;
	}

	/** Editor-Modell auf {@code config/vitrine/hud/overrides.json} im angegebenen Vitrine-Konfigordner. */
	public static HudEditorModel forConfigDir(List<HudWidgetRef> widgets, Path vitrineConfigDir) {
		return new HudEditorModel(widgets, HudLayoutOverrides.forConfigDir(vitrineConfigDir));
	}

	public boolean hasWidgets() {
		return !widgets.isEmpty();
	}

	public int widgetCount() {
		return widgets.size();
	}

	public int selectedIndex() {
		return selectedIndex;
	}

	public List<HudWidgetRef> widgets() {
		return widgets;
	}

	public HudLayoutOverrides overrides() {
		return overrides;
	}

	public Optional<HudWidgetRef> selected() {
		return selectedIndex < 0 ? Optional.empty() : Optional.of(widgets.get(selectedIndex));
	}

	/** Das unveraenderte Paket-Widget an {@code index}. */
	public HudWidget baseWidget(int index) {
		return widgets.get(index).widget();
	}

	/** Das effektive Widget an {@code index} (Paket-Layout + Nutzer-Overrides). */
	public HudWidget effectiveWidget(int index) {
		HudWidgetRef ref = widgets.get(index);
		return overrides.apply(ref.packId(), ref.widget());
	}

	/** Das effektive, gerade ausgewaehlte Widget. */
	public Optional<HudWidget> effectiveSelected() {
		return selectedIndex < 0 ? Optional.empty() : Optional.of(effectiveWidget(selectedIndex));
	}

	public void select(int index) {
		if (index >= 0 && index < widgets.size()) {
			selectedIndex = index;
		}
	}

	/** Waehlt das naechste Widget in Zeichenreihenfolge (mit Umlauf). */
	public void selectNext() {
		if (!widgets.isEmpty()) {
			selectedIndex = (selectedIndex + 1) % widgets.size();
		}
	}

	/**
	 * Verschiebt das gewaehlte Widget um {@code (dx, dy)} Bildschirmpixel --
	 * Maus-Ziehen ebenso wie Tasten-Feinverschiebung. Der Versatz wird in
	 * Bildschirmkoordinaten addiert (siehe {@link HudPlacement}), deshalb gilt
	 * das Delta unabhaengig vom Anker.
	 */
	public void moveBy(int dx, int dy) {
		if (selectedIndex < 0 || (dx == 0 && dy == 0)) {
			return;
		}
		HudOffset current = effectiveWidget(selectedIndex).offset();
		setOffset(new HudOffset(current.x() + dx, current.y() + dy));
	}

	/** Setzt den Versatz des gewaehlten Widgets absolut. */
	public void setOffset(HudOffset offset) {
		if (selectedIndex < 0) {
			return;
		}
		HudWidgetRef ref = widgets.get(selectedIndex);
		HudOffset packageOffset = ref.widget().offset();
		if (offset.equals(packageOffset)) {
			// Zurueck auf den Paket-Wert -> Versatz-Override faellt weg.
			HudWidgetOverride existing = overrides.find(ref.packId(), ref.widget().id())
					.orElse(HudWidgetOverride.EMPTY);
			applyOverride(ref, existing.withOffset(null));
		} else {
			overrides.setOffset(ref.packId(), ref.widget().id(), offset);
		}
	}

	/** Schaltet den Anker des gewaehlten Widgets auf den naechsten aus {@link HudAnchor#values()}. */
	public void cycleAnchor() {
		if (selectedIndex < 0) {
			return;
		}
		HudWidgetRef ref = widgets.get(selectedIndex);
		HudAnchor[] all = HudAnchor.values();
		HudAnchor next = all[(effectiveWidget(selectedIndex).anchor().ordinal() + 1) % all.length];
		if (next == ref.widget().anchor()) {
			HudWidgetOverride existing = overrides.find(ref.packId(), ref.widget().id())
					.orElse(HudWidgetOverride.EMPTY);
			applyOverride(ref, existing.withAnchor(null));
		} else {
			overrides.setAnchor(ref.packId(), ref.widget().id(), next);
		}
	}

	/**
	 * Schaltet die Sichtbarkeit des gewaehlten Widgets in drei Stufen weiter:
	 * Paket-Vorgabe &rarr; immer an &rarr; immer aus &rarr; Paket-Vorgabe.
	 */
	public void cycleVisibility() {
		if (selectedIndex < 0) {
			return;
		}
		HudWidgetRef ref = widgets.get(selectedIndex);
		Boolean current = overrides.find(ref.packId(), ref.widget().id())
				.map(HudWidgetOverride::visible).orElse(null);
		Boolean next;
		if (current == null) {
			next = Boolean.TRUE;
		} else if (current) {
			next = Boolean.FALSE;
		} else {
			next = null;
		}
		overrides.setVisible(ref.packId(), ref.widget().id(), next);
	}

	/** Der aktuelle Sichtbarkeits-Override des gewaehlten Widgets ({@code null} = Paket-Vorgabe). */
	public Optional<Boolean> selectedVisibilityOverride() {
		return selected().flatMap(ref -> overrides.find(ref.packId(), ref.widget().id()))
				.map(HudWidgetOverride::visible);
	}

	/** Verwirft alle Anpassungen des gewaehlten Widgets (zurueck auf das reine Paket-Layout). */
	public void resetSelected() {
		selected().ifPresent(ref -> overrides.clear(ref.packId(), ref.widget().id()));
	}

	/** Schreibt die Overrides atomar nach {@code config/vitrine/hud/overrides.json}. */
	public void save() {
		overrides.save();
	}

	private void applyOverride(HudWidgetRef ref, HudWidgetOverride override) {
		overrides.clear(ref.packId(), ref.widget().id());
		if (override.anchor() != null) {
			overrides.setAnchor(ref.packId(), ref.widget().id(), override.anchor());
		}
		if (override.offset() != null) {
			overrides.setOffset(ref.packId(), ref.widget().id(), override.offset());
		}
		if (override.visible() != null) {
			overrides.setVisible(ref.packId(), ref.widget().id(), override.visible());
		}
	}
}
