package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Low fire": the burning overlay is drawn lower on screen. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

	@Unique
	private static boolean pinvisuals$firePushed;

	@Inject(method = "renderFire", at = @At("HEAD"))
	private static void pinvisuals$lowFireStart(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite,
			CallbackInfo ci) {
		pinvisuals$firePushed = Tweaks.lowFire;
		if (pinvisuals$firePushed) {
			pose.pushPose();
			pose.translate(0f, Tweaks.LOW_FIRE_OFFSET, 0f);
		}
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void pinvisuals$lowFireEnd(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite,
			CallbackInfo ci) {
		if (pinvisuals$firePushed) {
			pose.popPose();
			pinvisuals$firePushed = false;
		}
	}
}
