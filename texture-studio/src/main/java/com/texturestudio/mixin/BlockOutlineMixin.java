package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Block outline (Studio): colour, thickness and a soft glow (two wider, faint lines under the outline) for the
 * outline of the block you look at.
 */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {

	@Shadow
	private void submitHitOutline(PoseStack pose, SubmitNodeCollector out, RenderType type,
			BlockOutlineRenderState state, int color, float width, boolean translucent) {
		throw new AssertionError();
	}

	@Redirect(method = "submitBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V"))
	private void pinvisuals$outline(LevelRenderer self, PoseStack pose, SubmitNodeCollector out, RenderType type,
			BlockOutlineRenderState state, int color, float width, boolean translucent) {
		if (!Tweaks.blockOutlineOn) {
			submitHitOutline(pose, out, type, state, color, width, translucent);
			return;
		}
		if (type == RenderTypes.secondaryBlockOutline()) {
			return; // the black high-contrast line under the vanilla one
		}
		int c = Tweaks.blockOutlineColorNow();
		float w = width * Tweaks.blockOutlineWidth / 3f;
		int glow = Tweaks.blockOutlineGlow;
		if (glow > 0) {
			submitHitOutline(pose, out, type, state, alpha(c, glow * 0x30 / 100), w * 4.5f, translucent);
			submitHitOutline(pose, out, type, state, alpha(c, glow * 0x60 / 100), w * 2.4f, translucent);
		}
		submitHitOutline(pose, out, type, state, c, w, translucent);
	}

	private static int alpha(int c, int a) {
		return (c & 0xFFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
	}
}
