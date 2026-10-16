package de.spassglas.vitrine.ui.onboarding;

import de.spassglas.vitrine.i18n.Translations;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * ROADMAP X4: der Erster-Start-Ablauf erklaert Klasse A, Klasse B und das
 * Faehigkeiten-Prinzip -- in genau dieser Reihenfolge, wortgleich zum Zweck
 * der Zeile. Dieser Test deckt den Teil ab, der ohne laufenden Client
 * pruefbar ist: {@link OnboardingModel} liefert stabil drei Abschnitte, und
 * jeder ihrer Uebersetzungs-Keys ist in beiden Sprachdateien tatsaechlich
 * hinterlegt (nicht nur ein Key, der auf sich selbst zurueckfiele).
 */
public final class OnboardingModelTest {

	@Test
	public void hasExactlyThreeSectionsInClassAClassBCapabilitiesOrder() {
		List<OnboardingModel.Section> sections = OnboardingModel.sections();

		assertEquals(sections.size(), 3, "Klasse A, Klasse B, Faehigkeiten -- nicht mehr, nicht weniger");
		assertTrue(sections.get(0).titleKey().contains("class_a"), "erster Abschnitt ist Klasse A");
		assertTrue(sections.get(1).titleKey().contains("class_b"), "zweiter Abschnitt ist Klasse B");
		assertTrue(sections.get(2).titleKey().contains("capabilities"), "dritter Abschnitt sind die Faehigkeiten");
	}

	@Test
	public void everySectionKeyExistsInBothLocales() {
		for (OnboardingModel.Section section : OnboardingModel.sections()) {
			for (String locale : new String[] {"en_us", "de_de"}) {
				assertTrue(Translations.has(locale, section.titleKey()),
						locale + " fehlt Titel-Key '" + section.titleKey() + "'");
				assertTrue(Translations.has(locale, section.bodyKey()),
						locale + " fehlt Text-Key '" + section.bodyKey() + "'");
			}
		}
	}

	@Test
	public void sectionsListIsStableAcrossCalls() {
		assertEquals(OnboardingModel.sections(), OnboardingModel.sections(),
				"reine Daten -- zwei Aufrufe liefern dieselbe Reihenfolge/denselben Inhalt");
	}
}
