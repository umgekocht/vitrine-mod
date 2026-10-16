package de.spassglas.vitrine.ui.onboarding;

import java.util.List;

/**
 * Client-freier Inhalt des Erster-Start-Ablaufs (ROADMAP X4). Reine Daten --
 * Reihenfolge und Uebersetzungs-Keys der drei Erklaerabschnitte sind so ohne
 * laufenden Client pruefbar (siehe {@code OnboardingModelTest}); den Aufbau
 * (Zeichnen, Knopf, Persistenz von {@code onboardingSeen}) uebernimmt
 * {@code OnboardingScreen}.
 *
 * <p>Die drei Abschnitte entsprechen wortgleich dem Zweck der Zeile:</p>
 * <ol>
 *   <li>Klasse A (Texturen/Theme/HUD/Sound/Sprache): wird sofort angewendet
 *       ("hot"), ohne Neustart, fuehrt nie eigenen Programmcode aus.</li>
 *   <li>Klasse B ({@code .jar}-Mods): braucht einen Neustart, ist
 *       standardmaessig ausgeblendet (Opt-in ueber "Java-Mods anzeigen" in
 *       den Einstellungen, siehe {@code JavaModOfferGate}/
 *       {@code VitrineSettings#showJavaModOffers}).</li>
 *   <li>Faehigkeiten sind eine Whitelist (Charter-Regel 4, "Default ist
 *       Verbot"): ein Paket bekommt nur Zugriff auf das, was es im Manifest
 *       anmeldet und der Nutzer vor der Installation bestaetigt (siehe
 *       {@code CapabilityConsentDialog}).</li>
 * </ol>
 */
public final class OnboardingModel {

	/** Ein Erklaerabschnitt: Ueberschrift plus Klartext-Erklaerung, je ein Uebersetzungs-Key. */
	public record Section(String titleKey, String bodyKey) {
	}

	private static final List<Section> SECTIONS = List.of(
			new Section("vitrine.onboarding.section.class_a.title", "vitrine.onboarding.section.class_a.body"),
			new Section("vitrine.onboarding.section.class_b.title", "vitrine.onboarding.section.class_b.body"),
			new Section("vitrine.onboarding.section.capabilities.title",
					"vitrine.onboarding.section.capabilities.body"));

	private OnboardingModel() {
	}

	/** Die drei Erklaerabschnitte, in fester, wortgleicher Reihenfolge zum Zweck der Zeile. */
	public static List<Section> sections() {
		return SECTIONS;
	}
}
