package de.spassglas.vitrine.mixin.client;

import de.spassglas.vitrine.pack.VitrinePackProvider;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.VanillaResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Haengt {@link VitrinePackProvider} (H1) an Vanillas eigenen
 * Ressourcenpaket-Scan: nachdem {@link VanillaResourcePackProvider#register}
 * die Pakete aus {@code resourcepacks/} eingesammelt hat, meldet dieser Mixin
 * zusaetzlich alles aus {@code config/vitrine/packs/} an denselben {@code
 * profileAdder} an -- genau die Stelle, die Fabric API selbst fuer ihre
 * Mod-Ressourcenpakete nutzt ({@code
 * net.fabricmc.fabric.mixin.resource.loader.client.VanillaResourcePackProviderMixin}
 * in {@code fabric-resource-loader-v0}, Apache-2.0, siehe THIRD-PARTY.md --
 * dieselbe Zielklasse/-methode, eigener Code).
 *
 * <p>Der {@code instanceof}-Test beschraenkt die Erweiterung auf den echten
 * Client-Provider ({@link DefaultClientResourcePackProvider}): {@link
 * VanillaResourcePackProvider} wird laut Vanilla auch fuer den serverseitigen
 * Datenpaket-Scan verwendet, und Vitrine ist ein reiner Client-Mod (siehe
 * {@code fabric.mod.json}, {@code "environment": "client"}).</p>
 */
@Mixin(VanillaResourcePackProvider.class)
public class VanillaResourcePackProviderMixin {

	@Inject(method = "register", at = @At("RETURN"))
	private void vitrine$registerVitrinePacks(Consumer<ResourcePackProfile> profileAdder, CallbackInfo ci) {
		if ((Object) this instanceof DefaultClientResourcePackProvider) {
			VitrinePackProvider.clientInstance().register(profileAdder);
		} else if ((Object) this instanceof net.minecraft.resource.VanillaDataPackProvider) {
			// ROADMAP AD1: Verhaltens-Addons als Datenpakete der Welt anmelden
			// (integrierter Server im Einzelspieler/LAN-Host).
			VitrinePackProvider.dataInstance().register(profileAdder);
		}
	}
}
