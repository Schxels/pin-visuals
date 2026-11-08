package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hit colour: Minecraft tints a mob or player that was hit with the red rows of its 16x16 "entity colour overlay"
 * texture. We simply repaint those rows in the picked colour (see mixin/OverlayTextureMixin); "none" = vanilla red.
 */
public final class HitColor {

	/** Vanilla: red with 70 % strength. */
	private static final int VANILLA = 0xB2FF0000;

	private static DynamicTexture texture;
	private static String applied = "";
	private static int lastRgb;

	private HitColor() {
	}

	/** Called by the mixin once the overlay texture exists. */
	public static void attach(DynamicTexture t) {
		texture = t;
		applied = "";
	}

	/** Overlay colour (ARGB, alpha = strength) of a style at the given time. */
	public static int color(String id, long ms) {
		return switch (id) {
			case "blue" -> 0xB22F7BFF;
			case "purple" -> 0xB2A23BFF;
			case "pink" -> 0xB2FF4FC3;
			case "green" -> 0xB233E05A;
			case "gold" -> 0xB2FFB800;
			case "white" -> 0x99FFFFFF;
			case "shadow" -> 0xA0000000;
			case "rainbow" -> 0xB2000000 | (hsv((ms % 2400L) / 2400f) & 0xFFFFFF);
			default -> VANILLA;
		};
	}

	/** Called every client tick: repaints the overlay when the style changed (every tick for rainbow). */
	public static void tick() {
		DynamicTexture t = texture;
		if (t == null) {
			return;
		}
		String id = Cosmetics.hitColor;
		boolean animated = "rainbow".equals(id);
		if (id.equals(applied) && !animated) {
			return;
		}
		int c = color(id, System.currentTimeMillis());
		if (id.equals(applied) && c == lastRgb) {
			return;
		}
		try {
			NativeImage img = t.getPixels();
			if (img == null) {
				return;
			}
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 16; x++) {
					img.setPixel(x, y, c);
				}
			}
			t.upload();
			applied = id;
			lastRgb = c;
		} catch (RuntimeException e) {
			TextureStudioClient.LOGGER.warn("Pin Visuals: could not change the hit colour", e);
			applied = id;
			lastRgb = c;
		}
	}

	static int hsv(float h) {
		float r = Math.abs(h * 6 - 3) - 1;
		float g = 2 - Math.abs(h * 6 - 2);
		float b = 2 - Math.abs(h * 6 - 4);
		int ri = Math.round(Math.max(0, Math.min(1, r)) * 255);
		int gi = Math.round(Math.max(0, Math.min(1, g)) * 255);
		int bi = Math.round(Math.max(0, Math.min(1, b)) * 255);
		return 0xFF000000 | ri << 16 | gi << 8 | bi;
	}
}
