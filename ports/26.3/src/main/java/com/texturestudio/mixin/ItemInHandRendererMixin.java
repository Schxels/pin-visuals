package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
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
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {

	@Unique
	private boolean pinvisuals$shieldPushed;

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void pinvisuals$lowShieldStart(PlayerRenderState player, FirstPersonHandsAndItemsRenderState handsState, float frameTime, float xRot, InteractionHand hand,
			float attack, ItemStack stack, float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		pinvisuals$shieldPushed = Tweaks.lowShield && stack != null && stack.is(Items.SHIELD);
		if (pinvisuals$shieldPushed) {
			pose.pushPose();
			pose.translate(0f, Tweaks.LOW_SHIELD_OFFSET, 0f);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void pinvisuals$lowShieldEnd(PlayerRenderState player, FirstPersonHandsAndItemsRenderState handsState, float frameTime, float xRot, InteractionHand hand,
			float attack, ItemStack stack, float inverseArmHeight, PoseStack pose, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		if (pinvisuals$shieldPushed) {
			pose.popPose();
			pinvisuals$shieldPushed = false;
		}
	}
}
