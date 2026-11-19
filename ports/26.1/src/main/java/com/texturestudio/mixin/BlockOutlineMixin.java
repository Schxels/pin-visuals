package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.LevelRenderer;
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
	private void renderHitOutline(PoseStack pose, VertexConsumer out, double x, double y, double z,
			BlockOutlineRenderState state, int color, float width) {
		throw new AssertionError();
	}

	@Redirect(method = "renderBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDLnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IF)V"))
	private void pinvisuals$outline(LevelRenderer self, PoseStack pose, VertexConsumer out, double x, double y,
			double z, BlockOutlineRenderState state, int color, float width) {
		if (!Tweaks.blockOutlineOn) {
			renderHitOutline(pose, out, x, y, z, state, color, width);
			return;
		}
		if (color == 0xFF000000 && width == 7.0f) {
			return; // the black high-contrast line under the vanilla one
		}
		int c = Tweaks.blockOutlineColorNow();
		float w = width * Tweaks.blockOutlineWidth / 3f;
		int glow = Tweaks.blockOutlineGlow;
		if (glow > 0) {
			renderHitOutline(pose, out, x, y, z, state, alpha(c, glow * 0x30 / 100), w * 4.5f);
			renderHitOutline(pose, out, x, y, z, state, alpha(c, glow * 0x60 / 100), w * 2.4f);
		}
		renderHitOutline(pose, out, x, y, z, state, c, w);
	}

	private static int alpha(int c, int a) {
		return (c & 0xFFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
	}
}
