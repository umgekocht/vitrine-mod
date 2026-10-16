package de.spassglas.vitrine.addon.script.client;

import de.spassglas.vitrine.addon.script.ScriptAddonHudValues;
import de.spassglas.vitrine.addon.script.ScriptAddonRuntime;
import de.spassglas.vitrine.script.ScriptValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Echte Umsetzung von {@link ScriptAddonRuntime} ueber einen laufenden
 * {@link MinecraftClient} (ROADMAP AD2). Jede Wirkung ausschliesslich ueber
 * vorhandene Vanilla-/Vitrine-Mechanismen:
 * <ul>
 *   <li>{@link #showMessage} -- Actionbar-Text wie
 *       {@link de.spassglas.vitrine.addon.AddonRule.Action.Kind#NACHRICHT}
 *       (server-seitiges Gegenstueck), hier client-seitig: der vom Addon-Autor
 *       gewaehlte Text ist roher Anzeigetext ({@code Text.literal}), keine
 *       Uebersetzung.</li>
 *   <li>{@link #playSound} -- ausschliesslich ueber {@code
 *       Registries.SOUND_EVENT.getOrEmpty(Identifier)}, nie Reflection; eine
 *       unbekannte Sound-Id spielt nichts und loggt eine Warnung, stuerzt
 *       nichts ab.</li>
 *   <li>{@link #setHudValue} -- schreibt in {@link ScriptAddonHudValues}.</li>
 * </ul>
 */
public final class MinecraftScriptAddonRuntime implements ScriptAddonRuntime {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Script");

	private final MinecraftClient client;

	public MinecraftScriptAddonRuntime(MinecraftClient client) {
		this.client = client;
	}

	@Override
	public void showMessage(String text) {
		if (client.player == null) {
			LOGGER.debug("Addon-Skript-Nachricht ohne Spieler verworfen: {}", text);
			return;
		}
		client.player.sendMessage(Text.literal(text), true);
	}

	@Override
	public void playSound(String soundId) {
		Identifier id = Identifier.tryParse(soundId);
		if (id == null) {
			LOGGER.warn("Addon-Skript: Sound-Id '{}' ist keine gueltige Kennung.", soundId);
			return;
		}
		Registries.SOUND_EVENT.getOrEmpty(id).ifPresentOrElse(this::play,
				() -> LOGGER.warn("Addon-Skript: unbekannter Sound '{}'.", soundId));
	}

	private void play(SoundEvent sound) {
		client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0f));
	}

	@Override
	public void setHudValue(String key, ScriptValue value) {
		ScriptAddonHudValues.set(key, value);
	}
}
