package de.spassglas.vitrine.hud.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Verdrahteter Einstiegspunkt fuer den Aktivierungsweg von ROADMAP T5,
 * analog zu {@code /vitrine hotapply} (H2): {@code /vitrine hud <packId>}
 * laedt und aktiviert ein HUD-Paket ueber {@link HudActivation#activate}
 * (Zip-Sicherheit + {@code hud}-Faehigkeit werden dabei durchgesetzt),
 * {@code /vitrine hud-off <packId>} nimmt es wieder heraus. {@code packId}
 * ist die volle Ressourcen-Pack-Profil-Id (Praefix {@code "vitrine/"}), wie
 * bei den uebrigen {@code /vitrine}-Unterbefehlen.
 */
public final class HudCommand {

	private static final String PACK_ID_ARGUMENT = "packId";

	private HudCommand() {
	}

	/** Registriert {@code /vitrine hud [edit|<packId>]} und {@code /vitrine hud-off <packId>} beim Client-Befehlsbaum. */
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("vitrine")
						.then(ClientCommandManager.literal("hud")
								.then(ClientCommandManager.literal("edit")
										.executes(HudCommand::runEdit))
								.then(ClientCommandManager.argument(PACK_ID_ARGUMENT, StringArgumentType.greedyString())
										.executes(HudCommand::runActivate)))
						.then(ClientCommandManager.literal("hud-off")
								.then(ClientCommandManager.argument(PACK_ID_ARGUMENT, StringArgumentType.greedyString())
										.executes(HudCommand::runDeactivate)))));
	}

	/** {@code /vitrine hud edit}: oeffnet den In-Game-HUD-Editor (ROADMAP T6). */
	private static int runEdit(CommandContext<FabricClientCommandSource> context) {
		context.getSource().sendFeedback(Text.translatable("vitrine.hud.editor.opening"));
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.setScreen(HudEditorScreen.create(client.currentScreen)));
		return 1;
	}

	private static int runActivate(CommandContext<FabricClientCommandSource> context) {
		String packId = StringArgumentType.getString(context, PACK_ID_ARGUMENT);
		context.getSource().sendFeedback(Text.translatable("vitrine.hud.command.activating", packId));
		HudActivation.activate(packId);
		return 1;
	}

	private static int runDeactivate(CommandContext<FabricClientCommandSource> context) {
		String packId = StringArgumentType.getString(context, PACK_ID_ARGUMENT);
		context.getSource().sendFeedback(Text.translatable("vitrine.hud.command.deactivating", packId));
		HudActivation.deactivate(packId);
		return 1;
	}
}
