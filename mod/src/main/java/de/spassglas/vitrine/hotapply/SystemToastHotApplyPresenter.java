package de.spassglas.vitrine.hotapply;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

import java.util.Set;

/**
 * Echte Umsetzung von {@link HotApplyToastPresenter} ueber Vanillas {@link
 * SystemToast} (ROADMAP H2, das "Toast"-Fertig-Kriterium). Alle Texte kommen
 * ueber {@code Text.translatable(...)} aus {@code lang/en_us.json}/{@code
 * de_de.json} (F3) -- keine hartkodierten Anzeigetexte.
 *
 * <p>Zeigt bewusst nur die Anzahl der Pakete im Reload-Stapel, nie einen
 * erfundenen Prozentwert (siehe Klassendoku von {@link HotApplyToastPresenter},
 * Charter-Regel 2).</p>
 */
public final class SystemToastHotApplyPresenter implements HotApplyToastPresenter {

	@Override
	public void showApplying(Set<String> packIds) {
		show(SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.translatable("vitrine.hotapply.applying.title"),
				Text.translatable("vitrine.hotapply.applying.description", packIds.size()));
	}

	@Override
	public void showApplied(Set<String> packIds) {
		show(SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.translatable("vitrine.hotapply.applied.title"),
				Text.translatable("vitrine.hotapply.applied.description", packIds.size()));
	}

	@Override
	public void showFailed(Set<String> packLabels, String reasonKey) {
		// ROADMAP H3: Klartext heisst hier ausdruecklich Paket-Name(n) + Grund,
		// nicht nur eine Anzahl -- ein Nutzer soll ohne Log-Datei sehen
		// koennen, welches Paket zurueckgenommen wurde und warum.
		// ROADMAP GATE81: packLabels sind Anzeigenamen (kein "vitrine/..."-
		// Profil-ID mehr, siehe PackDisplayNameLookup).
		// ROADMAP GATE11: der Grund ist ein Lang-Key und wird hier uebersetzt
		// eingesetzt -- im Toast steht ein ganzer Satz, kein Java-Klassenname.
		show(SystemToast.Type.PACK_LOAD_FAILURE,
				Text.translatable("vitrine.hotapply.failed.title"),
				Text.translatable("vitrine.hotapply.failed.description", String.join(", ", packLabels),
						Text.translatable(reasonKey)));
	}

	@Override
	public void showRolledBack(Set<String> packLabels, String reasonKey) {
		// ROADMAP GATE81: der Auto-Rollback (H3) hat packLabels (Klartext-
		// Namen) soeben deaktiviert -- sichtbar als eigener Toast, ohne dass
		// ein Flyout geoeffnet werden muss, mit demselben uebersetzten Grund
		// wie showFailed.
		show(SystemToast.Type.PACK_LOAD_FAILURE,
				Text.translatable("vitrine.hotapply.rolled_back.title"),
				Text.translatable("vitrine.hotapply.rolled_back.description", String.join(", ", packLabels),
						Text.translatable(reasonKey)));
	}

	private static void show(SystemToast.Type type, Text title, Text description) {
		ToastManager toastManager = MinecraftClient.getInstance().getToastManager();
		SystemToast.show(toastManager, type, title, description);
	}
}
