package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.Tweaks;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Low shield": a shield in first person is held lower. */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

	@Unique
	private boolean pinvisuals$shieldPushed;

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void pinvisuals$lowShieldStart(AbstractClientPlayer player, float frameTime, float xRot, InteractionHand hand,
			float attack, ItemStack stack, float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		pinvisuals$shieldPushed = Tweaks.lowShield && stack != null && stack.is(Items.SHIELD);
		if (pinvisuals$shieldPushed) {
			pose.pushPose();
			pose.translate(0f, Tweaks.LOW_SHIELD_OFFSET, 0f);
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void pinvisuals$lowShieldEnd(AbstractClientPlayer player, float frameTime, float xRot, InteractionHand hand,
			float attack, ItemStack stack, float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		if (pinvisuals$shieldPushed) {
			pose.popPose();
			pinvisuals$shieldPushed = false;
		}
	}
}
