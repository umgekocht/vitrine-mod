package de.spassglas.vitrine.addon;

import de.spassglas.vitrine.Vitrine;
import de.spassglas.vitrine.manifest.Manifest;
import de.spassglas.vitrine.manifest.ManifestParser;
import de.spassglas.vitrine.pack.VitrinePackProvider;
import de.spassglas.vitrine.state.PackStateStore;
import de.spassglas.vitrine.vpkg.VpkgArchive;
import de.spassglas.vitrine.vpkg.VpkgCapability;
import de.spassglas.vitrine.vpkg.VpkgCapabilityGrant;
import de.spassglas.vitrine.vpkg.VpkgEntry;
import de.spassglas.vitrine.vpkg.VpkgSecurityValidator;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Laufzeit der Verhaltensregeln (ROADMAP AD2). Haengt sich einmal an die
 * Fabric-Ereignisse und gleicht alle 2 Sekunden mit dem Aktiv-Schalter der
 * Bibliothek ab: eingeschaltete Addons mit {@code regeln/*.json} wirken ab dem
 * naechsten Ereignis, ausgeschaltete sofort nicht mehr. Kein Neustart, kein
 * Welt-Neuladen. Dieselben Pruefungen wie bei HUD-Paketen: Signatur/Pruefsumme,
 * angemeldete und zugestimmte Faehigkeit {@code verhalten}, Zip-Sicherheit.
 */
public final class AddonRules {

	public static final String RULES_PREFIX = "regeln/";

	private static final Map<String, List<AddonRule>> ACTIVE = new HashMap<>();
	private static final Set<String> SEEN = new HashSet<>();
	private static long tick;

	private AddonRules() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AddonRules::onTick);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (player instanceof ServerPlayerEntity sp) {
				fire("block_abgebaut", Registries.BLOCK.getId(state.getBlock()).toString(), sp);
			}
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (player instanceof ServerPlayerEntity sp) {
				fire("item_benutzt", Registries.ITEM.getId(stack.getItem()).toString(), sp);
			}
			return TypedActionResult.pass(stack);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> fire("beigetreten", null, handler.player)));
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
			if (killer instanceof ServerPlayerEntity sp) {
				fire("mob_besiegt", Registries.ENTITY_TYPE.getId(killed.getType()).toString(), sp);
			}
		});
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		if (tick % 40 == 0) {
			sync();
		}
		if (tick % 20 != 0) {
			return;
		}
		long seconds = tick / 20;
		for (List<AddonRule> rules : ACTIVE.values()) {
			for (AddonRule rule : rules) {
				if (rule.event().equals("timer") && seconds % rule.everySeconds() == 0) {
					for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
						run(rule, p);
					}
				}
			}
		}
	}

	private static void fire(String event, String target, ServerPlayerEntity player) {
		for (List<AddonRule> rules : ACTIVE.values()) {
			for (AddonRule rule : rules) {
				if (rule.event().equals(event) && (rule.target() == null || rule.target().equals(target))) {
					run(rule, player);
				}
			}
		}
	}

	private static void run(AddonRule rule, ServerPlayerEntity player) {
		if (rule.chance() < 1.0 && ThreadLocalRandom.current().nextDouble() >= rule.chance()) {
			return;
		}
		for (AddonRule.Action a : rule.actions()) {
			try {
				switch (a.kind()) {
					case NACHRICHT -> player.sendMessage(Text.literal(a.value()
							.replace("{spieler}", player.getName().getString())), a.actionbar());
					case GEBEN -> Registries.ITEM.getOrEmpty(Identifier.tryParse(a.value())).ifPresent(item -> {
						ItemStack s = new ItemStack(item, a.amount());
						if (!player.getInventory().insertStack(s)) {
							player.dropItem(s, false);
						}
					});
					case EFFEKT -> Registries.STATUS_EFFECT.getEntry(Identifier.tryParse(a.value())).ifPresent(e ->
							player.addStatusEffect(new StatusEffectInstance(e, a.amount() * 20, a.level() - 1)));
					case XP -> player.addExperience(a.amount());
					case BEFEHL -> {
						MinecraftServer server = player.getServer();
						if (server != null) {
							server.getCommandManager().executeWithPrefix(
									player.getCommandSource().withLevel(2).withSilent(),
									a.value().replace("{spieler}", player.getName().getString()));
						}
					}
				}
			} catch (RuntimeException e) {
				Vitrine.LOGGER.warn("Addon-Aktion {} fehlgeschlagen: {}", a.kind(), e.toString());
			}
		}
	}

	/** Gleicht die aktiven Regeln mit dem Aktiv-Schalter in state.json ab. */
	static synchronized void sync() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(Vitrine.MOD_ID);
		Set<String> enabled;
		try {
			enabled = new HashSet<>(PackStateStore.load(configDir).activePackIdsInOrder());
		} catch (RuntimeException e) {
			return;
		}
		ACTIVE.keySet().retainAll(enabled);
		SEEN.retainAll(enabled);
		for (String packId : enabled) {
			if (SEEN.add(packId)) {
				load(packId).ifPresent(rules -> {
					ACTIVE.put(packId, rules);
					Vitrine.LOGGER.info("Addon '{}': {} Regel(n) aktiv -- ohne Neustart.", packId, rules.size());
				});
			}
		}
	}

	private static Optional<List<AddonRule>> load(String packId) {
		VitrinePackProvider provider = VitrinePackProvider.dataInstance();
		Optional<Path> file = provider.findPackFile(packId);
		if (file.isEmpty() || !VitrinePackProvider.hasValidIntegrity(file.get())) {
			return Optional.empty();
		}
		try (VpkgArchive archive = VpkgArchive.open(file.get())) {
			List<String> names = new ArrayList<>();
			for (VpkgEntry entry : archive.entries()) {
				String n = entry.name();
				if (!entry.directory() && n.startsWith(RULES_PREFIX) && n.toLowerCase(Locale.ROOT).endsWith(".json")
						&& n.indexOf('/', RULES_PREFIX.length()) < 0) {
					names.add(n);
				}
			}
			if (names.isEmpty()) {
				return Optional.empty();
			}
			Manifest manifest = ManifestParser.parse(archive.readManifestBytes());
			VpkgSecurityValidator.requireSafe(archive);
			VpkgCapabilityGrant grant = VpkgCapabilityGrant.of(manifest);
			grant.requireGranted(VpkgCapability.VERHALTEN);
			if (!provider.hasCapabilityConsent(file.get(), manifest)) {
				Vitrine.LOGGER.warn("Addon '{}': Faehigkeit 'verhalten' nie zugestimmt -- Regeln bleiben aus.", packId);
				return Optional.empty();
			}
			names.sort(String::compareTo);
			List<AddonRule> rules = new ArrayList<>();
			for (String n : names) {
				rules.addAll(AddonRule.parse(archive.readEntryBytes(n)));
			}
			return Optional.of(List.copyOf(rules));
		} catch (Exception e) {
			Vitrine.LOGGER.warn("Addon '{}': Regeln nicht geladen: {}", packId, e.getMessage());
			return Optional.empty();
		}
	}
}
