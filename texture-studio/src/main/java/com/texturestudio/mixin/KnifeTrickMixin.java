package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.Hooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knife inspect with the butterfly knife: arm plus the three knife parts with the flip trick of the Butterfly Knife
 * mod (fan open, flip, both handles around, close). Replaces the normal first-person hands while it plays.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class KnifeTrickMixin {

	@Shadow
	public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			PoseStack poseStack, SubmitNodeCollector collector, int light);

	@Shadow
	protected abstract void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int light,
			float equipProgress, float swingProgress, HumanoidArm arm);

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$butterfly(float partialTicks, PoseStack pose, SubmitNodeCollector out, LocalPlayer player,
			int light, CallbackInfo ci) {
		float[] p = Hooks.butterflyPose();
		if (p == null) {
			return;
		}
		// p = knifeRot, handleA, handleB, holdA, wrist, dx, dy, roll
		// the arm follows the hand a little (the knife a lot more, it turns in the fingers)
		pose.pushPose();
		pose.translate(p[5] * 0.7f, p[6] * 0.7f, 0f);
		renderPlayerArm(pose, out, light, 0f, 0f, HumanoidArm.RIGHT);
		pose.popPose();

		float mid = 5.5f / 16f;
		float ax = -(float) Math.sin(p[1]) * mid;
		float ay = -(float) Math.cos(p[1]) * mid;
		float bx = -(float) Math.sin(p[2]) * mid;
		float by = -(float) Math.cos(p[2]) * mid;
		float offX = bx + (ax - bx) * p[3];
		float offY = by + (ay - by) * p[3];

		pose.pushPose();
		pose.translate(0.52f + p[5], -0.38f + p[6], -0.80f);
		pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(20 + p[4])));
		pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(p[7])));
		pose.scale(0.45f, 0.45f, 0.45f);
		pose.mulPose(new Quaternionf().rotateZ(-p[0]));
		pose.translate(-offX, -offY, 0f);
		ItemDisplayContext ctx = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		pose.pushPose();
		pose.mulPose(new Quaternionf().rotateZ(-p[2]));
		renderItem(player, Hooks.butterflyPart("handle_b"), ctx, pose, out, light);
		pose.popPose();
		renderItem(player, Hooks.butterflyPart("blade"), ctx, pose, out, light);
		pose.pushPose();
		pose.mulPose(new Quaternionf().rotateZ(-p[1]));
		renderItem(player, Hooks.butterflyPart("handle_a"), ctx, pose, out, light);
		pose.popPose();
		pose.popPose();
		ci.cancel();
	}
}
