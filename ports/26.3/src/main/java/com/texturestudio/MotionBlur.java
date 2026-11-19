package com.texturestudio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Motion blur (Render): a post effect (assets/texturestudio/post_effect/motion_blur_N.json) that mixes the last
 * frame into the new one. The strength is a fixed shader value, so there is one file per step (10 % .. 90 %).
 */
public final class MotionBlur {

	private static Identifier mine;

	private MotionBlur() {
	}

	static Identifier id(int strength) {
		int step = Math.max(1, Math.min(9, Math.round(strength / 10f)));
		return Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "motion_blur_" + step);
	}

	static void tick(Minecraft mc) {
		// 26.3: post effects work differently, motion blur is not available here
	}
}
