package de.spassglas.vitrine.ui.home;

import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Ein Eintrag der Startseite, egal ob Vitrine-Paket oder Modrinth-Projekt.
 *
 * @param title     Anzeigename
 * @param subtitle  zweite Zeile (Autor, Kategorie) oder {@code null}
 * @param imageUrl  https-Bild (PNG oder WebP), oder {@code null}
 * @param fallback  Symbol, solange/falls das Bild fehlt
 * @param color     Grundfarbe (RGB) fuer Toenungen, z. B. Modrinths Icon-Farbe
 * @param pill      Text der Pille rechts unten ("Holen", "Installiert", ...), live abgefragt
 * @param pillIsAction ob die Pille ein Knopf ist (sonst nur Hinweis)
 * @param onOpen    Klick auf den Eintrag
 * @param onGet     Klick auf die Pille
 * @param badge     Quell-Abzeichen (Vitrine/Modrinth) in der Ecke des Logos, oder {@code null}
 */
public record HomeItem(String title, String subtitle, String imageUrl, Identifier fallback, int color,
		Supplier<String> pill, Supplier<Boolean> pillIsAction, Runnable onOpen, Runnable onGet, Identifier badge) {

	public static final Identifier BADGE_VITRINE = Identifier.of("vitrine", "textures/gui/icons/badge_vitrine.png");
	public static final Identifier BADGE_MODRINTH = Identifier.of("vitrine", "textures/gui/icons/badge_modrinth.png");

	public HomeItem(String title, String subtitle, String imageUrl, Identifier fallback, int color,
			Supplier<String> pill, Supplier<Boolean> pillIsAction, Runnable onOpen, Runnable onGet) {
		this(title, subtitle, imageUrl, fallback, color, pill, pillIsAction, onOpen, onGet, null);
	}
}
