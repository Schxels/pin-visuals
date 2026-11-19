package com.texturestudio.mixin;

import com.texturestudio.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook: the camera takes the freelook angles instead of the player's view direction. */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float pinvisuals$yaw(Entity e, float partialTick) {
		if (Freelook.active() && e == Minecraft.getInstance().player) {
			return Freelook.yaw();
		}
		return e.getViewYRot(partialTick);
	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float pinvisuals$pitch(Entity e, float partialTick) {
		if (Freelook.active() && e == Minecraft.getInstance().player) {
			return Freelook.pitch();
		}
		return e.getViewXRot(partialTick);
	}
}
