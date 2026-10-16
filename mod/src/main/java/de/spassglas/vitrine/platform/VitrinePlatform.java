package de.spassglas.vitrine.platform;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * Einstieg der Addon-Plattform (eigener Fabric-Einstiegspunkt, damit sie
 * unabhaengig vom restlichen Vitrine-Start bleibt). Haengt Takt, HUD,
 * Welt-Ereignisse, Chat und den Befehl {@code /vitrine-konsole} ein.
 */
public final class VitrinePlatform implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(PlatformHost::clientTick);
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			PlatformHost.hudContext = context;
			try {
				PlatformHost.fire("hud", VitrineApi.graphics());
			} finally {
				PlatformHost.hudContext = null;
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> PlatformHost.fire("join")));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> PlatformHost.fire("leave")));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				PlatformHost.fire("chat", message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) ->
				PlatformHost.fire("chat", message.getString()));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(ClientCommandManager.literal("vitrine-konsole").executes(ctx -> {
					MinecraftClient mc = MinecraftClient.getInstance();
					mc.send(() -> mc.setScreen(new AddonConsoleScreen(null)));
					return 1;
				})));
	}
}
