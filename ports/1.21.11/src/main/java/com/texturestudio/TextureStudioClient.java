package com.texturestudio;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

public class TextureStudioClient implements ClientModInitializer {

	public static final String MOD_ID = "texturestudio";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping openKey;

	/** Name der Taste, mit der Texture Studio geoeffnet wird (fuer die Anzeige in der Oberflaeche). */
	public static String openKeyName() {
		return openKey == null ? "O" : openKey.getTranslatedKeyMessage().getString();
	}

	@Override
	public void onInitializeClient() {
		openKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.texturestudio.open", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));

		// Cosmetics: Auswahl laden, eigene Ebene an alle Spieler-Renderer haengen, Vanilla-Umhang bei eigenem ausblenden
		MixConfig startConfig = MixConfig.load();
		Cosmetics.load(startConfig);
		Tweaks.load(startConfig);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticLayer(avatar, context.getModelSet()));
			}
		});
		LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER
				.register(state -> Cosmetics.NONE.equals(Cosmetics.cape) || !Cosmetics.isLocal(state));

		// Kill effects and the Spotify lyrics in the sky (both under Extras)
		KillEffects.register();
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(MOD_ID, "atmosphere"), Atmosphere::extractHud);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"), LyricsHud::extract);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "armor"), ArmorHud::extract);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "spotify"), SpotifyHud::extract);
		Runtime.getRuntime().addShutdownHook(new Thread(Lyrics::shutdown, "PinVisuals-SpotifyStop"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KillEffects.tick(client);
			Lyrics.tick();
			HitColor.tick();
			PopChams.tick(client);
			Atmosphere.tick(client);
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new TextureStudioScreen(null));
				}
			}
		});
	}
}
