package de.spassglas.vitrine.addon.script.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * Verdrahtet {@code /v <name>} (ROADMAP AD2, woertlich aus dem Backlog) auf
 * {@link ScriptAddons#fireCommand}. Bewusst ein eigener, flacher Befehl --
 * kein Unterbefehl von {@code /vitrine} wie {@code HudCommand}, die Roadmap
 * nennt den Namen woertlich.
 */
public final class ScriptAddonCommand {

	private static final String NAME_ARGUMENT = "name";

	private ScriptAddonCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("v")
						.then(ClientCommandManager.argument(NAME_ARGUMENT, StringArgumentType.word())
								.executes(ScriptAddonCommand::run))));
	}

	private static int run(CommandContext<FabricClientCommandSource> context) {
		String name = StringArgumentType.getString(context, NAME_ARGUMENT);
		boolean matched = ScriptAddons.fireCommand(name);
		if (!matched) {
			// Charter: kein stillschweigendes Nichtstun -- der Spieler erfaehrt,
			// dass kein aktives Addon-Skript auf diesen Namen reagiert.
			context.getSource().sendFeedback(Text.translatable("vitrine.script.command.unknown", name));
		}
		return 1;
	}
}
