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
		var gr = mc.gameRenderer;
		Identifier current = gr.currentPostEffect();
		boolean on = Features.blurOn != null && Features.blurOn.b() && mc.level != null;
		if (on) {
			Identifier want = id(Features.blurStrength.i());
			// a vanilla effect (spectating a creeper, spider ...) wins
			boolean other = current != null && !current.getNamespace().equals(TextureStudioClient.MOD_ID);
			if (!other && !want.equals(current)) {
				((com.texturestudio.mixin.GameRendererAccess) gr).pinvisuals$setPostEffect(want);
				mine = want;
			}
		} else if (mine != null) {
			if (mine.equals(current)) {
				gr.clearPostEffect();
			}
			mine = null;
		}
	}
}
