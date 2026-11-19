package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Custom Skin (Player): your own player wears another skin, only on your screen. Built-in skins (alien, robot, ...)
 * or your own PNG file ("pinvisuals/skin.png" in the Minecraft folder).
 */
final class Skins {

	static final String[] IDS = { "alien", "robot", "ninja", "astronaut", "shadow", "file" };
	static final String[] NAMES = { "Alien", "Robot", "Ninja", "Astronaut", "Shadow", "Own file (pinvisuals/skin.png)" };
	private static final Identifier FILE_ID = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "dynamic/own_skin");

	private static long fileStamp = -1;
	private static boolean fileOk;

	private Skins() {
	}

	static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("pinvisuals").resolve("skin.png");
	}

	static PlayerSkin skin(Object player, PlayerSkin normal) {
		Minecraft mc = Minecraft.getInstance();
		if (Features.skinOn == null || !Features.skinOn.b() || player != mc.player || normal == null) {
			return null;
		}
		String id = IDS[Math.max(0, Math.min(IDS.length - 1, Features.skinChoice.i()))];
		PlayerModelType model = Features.skinSlim.b() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		ClientAsset.Texture body;
		if ("file".equals(id)) {
			if (!loadFile(mc)) {
				return null;
			}
			body = new ClientAsset.ResourceTexture(FILE_ID, FILE_ID);
		} else {
			Identifier asset = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "skin/" + id);
			body = new ClientAsset.ResourceTexture(asset,
					Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "textures/skin/" + id + ".png"));
		}
		return new PlayerSkin(body, normal.cape(), normal.elytra(), model, normal.secure());
	}

	/** (Re)loads the own skin file when it changed; false if there is none or it is not a 64x64 skin. */
	private static boolean loadFile(Minecraft mc) {
		Path p = file();
		long stamp;
		try {
			stamp = Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : 0;
		} catch (Exception e) {
			stamp = 0;
		}
		if (stamp == fileStamp) {
			return fileOk;
		}
		fileStamp = stamp;
		fileOk = false;
		if (stamp == 0) {
			return false;
		}
		try (InputStream in = Files.newInputStream(p)) {
			NativeImage img = NativeImage.read(in);
			if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
				img.close();
				return false;
			}
			mc.getTextureManager().register(FILE_ID, new DynamicTexture(() -> "pin visuals own skin", img));
			fileOk = true;
		} catch (Exception e) {
			TextureStudioClient.LOGGER.warn("Pin Visuals: could not read {}", p, e);
		}
		return fileOk;
	}
}
