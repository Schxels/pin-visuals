package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.texturestudio.Hooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item physics: dropped items lie flat on the ground (blocks stand on it) with a fixed random turn, instead of
 * hovering, bobbing and spinning. Stacks become a small pile.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemPhysicsMixin {

	@Shadow
	@Final
	private RandomSource random;

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true)
	private void pinvisuals$physics(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera,
			CallbackInfo ci) {
		if (!Hooks.itemPhysics()) {
			return;
		}
		ci.cancel();
		if (state.item.isEmpty()) {
			return;
		}
		AABB box = state.item.getModelBoundingBox();
		boolean flat = box.getZsize() <= 0.0625;
		float yaw = Math.floorMod(state.seed * 31 + 7, 360);
		pose.pushPose();
		if (flat) {
			// lay it down: the item's depth now points up, so extra copies of a stack pile up on top
			pose.translate(0f, (float) -box.minZ + 0.005f, 0f);
			pose.rotateDegrees(Axis.YP, yaw);
			pose.rotateDegrees(Axis.XP, -90f);
		} else {
			pose.translate(0f, (float) -box.minY, 0f);
			pose.rotateDegrees(Axis.YP, yaw);
		}
		ItemEntityRenderer.submitMultipleFromCount(pose, out, state.lightCoords, state, random, box);
		pose.popPose();
	}
}
