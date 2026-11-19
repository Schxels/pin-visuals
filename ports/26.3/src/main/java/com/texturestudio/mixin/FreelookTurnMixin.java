package com.texturestudio.mixin;

import com.texturestudio.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: mouse movement turns the camera, the player keeps his direction. */
@Mixin(Entity.class)
public abstract class FreelookTurnMixin {

	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$freelook(double dx, double dy, CallbackInfo ci) {
		if (Freelook.active() && (Object) this == Minecraft.getInstance().player) {
			Freelook.turn(dx, dy);
			ci.cancel();
		}
	}
}
