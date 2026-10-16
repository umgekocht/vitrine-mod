package de.spassglas.vitrine.ui.theme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

/**
 * Echte Umsetzung von {@link ThemeMessagePresenter} ueber Vanillas
 * {@link SystemToast} (ROADMAP T2), analog zu
 * {@code de.spassglas.vitrine.hotapply.SystemToastHotApplyPresenter}. Alle
 * Texte kommen ueber {@code Text.translatable(...)} aus
 * {@code lang/en_us.json}/{@code de_de.json} (F3) -- keine hartkodierten
 * Anzeigetexte.
 */
public final class SystemToastThemePresenter implements ThemeMessagePresenter {

	@Override
	public void showBrokenThemeKeptCurrent(String themeName, String reason) {
		ToastManager toastManager = MinecraftClient.getInstance().getToastManager();
		SystemToast.show(toastManager, SystemToast.Type.PACK_LOAD_FAILURE,
				Text.translatable("vitrine.theme.broken.title"),
				Text.translatable("vitrine.theme.broken.description", themeName, reason));
	}
}
