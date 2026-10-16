package de.spassglas.vitrine.hotapply;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.spassglas.vitrine.pack.PackUninstallService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimaler Einstiegspunkt fuer ROADMAP H2/H5, solange es noch keine
 * Store-Oberflaeche gibt (M3): {@code /vitrine hotapply <packId>} aktiviert
 * ein bereits angemeldetes Ressourcen-Pack-Profil (siehe {@code
 * de.spassglas.vitrine.pack.VitrinePackProvider}, H1) und stoesst den
 * debounced Reload ueber {@link HotApplyService} an, {@code /vitrine
 * deactivate <packId>} deaktiviert es wieder (Paket bleibt installiert), und
 * {@code /vitrine uninstall <packId>} deinstalliert es vollstaendig (Datei in
 * den Papierkorb, aus {@code state.json} entfernt, siehe {@code
 * de.spassglas.vitrine.pack.PackUninstaller}) -- derselbe Pfad, den S4 spaeter
 * aus Installieren-/Aktivieren-/Deinstallieren-Knoepfen statt aus Befehlen
 * heraus aufrufen wird.
 *
 * <p>Kein neues UI, keine neue Logik: {@code packId} ist die volle
 * Ressourcen-Pack-Profil-ID (Praefix {@code "vitrine/"}, siehe {@code
 * VitrinePackProvider}), die eigentliche Sammel-/Debounce-/Toast-Logik steckt
 * bereits getestet in {@link HotApplyCoordinator}, die
 * Deinstallieren-Logik in {@code PackUninstaller}.</p>
 */
public final class HotApplyCommand {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Command");
	private static final String PACK_ID_ARGUMENT = "packId";

	private HotApplyCommand() {
	}

	/** Registriert {@code /vitrine hotapply|deactivate|uninstall <packId>} beim Fabric-Client-Befehlsbaum. */
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("vitrine")
						.then(ClientCommandManager.literal("hotapply")
								.then(ClientCommandManager.argument(PACK_ID_ARGUMENT, StringArgumentType.greedyString())
										.executes(HotApplyCommand::runHotApply)))
						.then(ClientCommandManager.literal("deactivate")
								.then(ClientCommandManager.argument(PACK_ID_ARGUMENT, StringArgumentType.greedyString())
										.executes(HotApplyCommand::runDeactivate)))
						.then(ClientCommandManager.literal("uninstall")
								.then(ClientCommandManager.argument(PACK_ID_ARGUMENT, StringArgumentType.greedyString())
										.executes(HotApplyCommand::runUninstall)))));
	}

	private static int runHotApply(CommandContext<FabricClientCommandSource> context) {
		String packId = StringArgumentType.getString(context, PACK_ID_ARGUMENT);
		context.getSource().sendFeedback(Text.translatable("vitrine.hotapply.command.triggered", packId));
		HotApplyService.requestActivation(packId);
		return 1;
	}

	private static int runDeactivate(CommandContext<FabricClientCommandSource> context) {
		String packId = StringArgumentType.getString(context, PACK_ID_ARGUMENT);
		context.getSource().sendFeedback(Text.translatable("vitrine.deactivate.command.triggered", packId));
		HotApplyService.requestDeactivation(packId);
		return 1;
	}

	private static int runUninstall(CommandContext<FabricClientCommandSource> context) {
		String packId = StringArgumentType.getString(context, PACK_ID_ARGUMENT);
		context.getSource().sendFeedback(Text.translatable("vitrine.uninstall.command.triggered", packId));
		PackUninstallService.requestUninstall(packId).whenComplete((ignored, throwable) -> {
			if (throwable != null) {
				// Nur geloggt, kein verschluckter Fehler -- PackUninstaller
				// selbst hat schon alles Sinnvolle versucht (siehe dortige
				// Klassendoku), hier bleibt nur noch die Beobachtung.
				LOGGER.error("Deinstallieren von '{}' ueber /vitrine uninstall ist fehlgeschlagen: {}",
						packId, throwable.toString(), throwable);
			}
		});
		return 1;
	}
}
