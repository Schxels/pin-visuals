package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.Hooks;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hurt cam: the camera shake when hit, scaled down (or off). Also hides the hand while zooming. */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {

	@Unique
	private final Matrix4f pinvisuals$before = new Matrix4f();

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$hurtStart(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		float k = Hooks.hurtStrength();
		if (k < 0f) {
			return;
		}
		if (k <= 0f) {
			ci.cancel();
			return;
		}
		pinvisuals$before.set(pose.last().pose());
	}

	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void pinvisuals$hurtEnd(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		float k = Hooks.hurtStrength();
		if (k < 0f || k >= 1f) {
			return;
		}
		Matrix4f m = pose.last().pose();
		pinvisuals$before.lerp(m, k, m);
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$zoomHand(CallbackInfo ci) {
		if (Hooks.hideHand()) {
			ci.cancel();
		}
	}
}
