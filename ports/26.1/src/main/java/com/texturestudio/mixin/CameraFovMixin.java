package com.texturestudio.mixin;

import com.texturestudio.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: smaller field of view. */
@Mixin(Camera.class)
public abstract class CameraFovMixin {

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void pinvisuals$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Zoom.apply(cir.getReturnValueF()));
	}
}
