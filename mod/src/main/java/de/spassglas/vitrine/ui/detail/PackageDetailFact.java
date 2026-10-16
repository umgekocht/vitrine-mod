package de.spassglas.vitrine.ui.detail;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine einzelne Zeile im "Details"-Abschnitt der Store-Detailseite
 * (docs/UI-SPEC.md "5. Detailseite"): ein Beschriftungs-Uebersetzungs-Key
 * und der zugehoerige Wert.
 *
 * <p>Ein fehlender optionaler Manifest-Wert (z. B. keine Lizenz angegeben)
 * ist {@link Optional#empty()} -- die Detailseite zeigt dann ehrlich
 * {@code vitrine.detail.value.unknown} ("keine Angaben") statt die Zeile zu
 * verstecken.</p>
 *
 * @param labelKey Uebersetzungs-Key der Zeilenbeschriftung (z. B. {@code vitrine.detail.field.version})
 * @param value    der Wert, oder leer wenn im Manifest nicht angegeben
 * @param copyable GATE69: ob die Zeile einen Kopier-Knopf bekommt (nur die Pruefsumme,
 *                 docs/UI-SPEC.md "Details": "Prüfsumme (kopierbar)")
 */
public record PackageDetailFact(String labelKey, Optional<String> value, boolean copyable) {

	public PackageDetailFact {
		Objects.requireNonNull(labelKey, "labelKey");
		Objects.requireNonNull(value, "value");
	}

	static PackageDetailFact of(String labelKey, String value) {
		return of(labelKey, value, false);
	}

	static PackageDetailFact of(String labelKey, String value, boolean copyable) {
		return new PackageDetailFact(labelKey,
				value == null || value.isBlank() ? Optional.empty() : Optional.of(value), copyable);
	}

	static PackageDetailFact of(String labelKey, Optional<String> value) {
		return new PackageDetailFact(labelKey, value, false);
	}

	/** Ob ein Wert vorliegt (sonst zeigt die Seite "keine Angaben"). */
	public boolean isKnown() {
		return value.isPresent();
	}
}
