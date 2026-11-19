package com.texturestudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Viewmodel (Studio): moves, turns and scales the item in first person, right after Minecraft placed it in the hand.
 * The left hand is mirrored.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ViewmodelMixin {

	@Inject(method = "applyItemArmTransform", at = @At("RETURN"))
	private void pinvisuals$viewmodel(PoseStack pose, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		com.texturestudio.Hooks.swapTransform(pose, arm);
		com.texturestudio.Hooks.knifeInspect(pose, arm);
		if (!Tweaks.vmOn) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(side * Tweaks.vmX / 100f, Tweaks.vmY / 100f, Tweaks.vmZ / 100f);
		pose.mulPose(Axis.XP.rotationDegrees(Tweaks.vmRotX));
		pose.mulPose(Axis.YP.rotationDegrees(side * Tweaks.vmRotY));
		pose.mulPose(Axis.ZP.rotationDegrees(side * Tweaks.vmRotZ));
		float s = Tweaks.vmScale / 100f;
		pose.scale(s, s, s);
	}
}
