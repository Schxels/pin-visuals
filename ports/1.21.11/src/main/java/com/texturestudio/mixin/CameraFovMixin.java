package com.texturestudio.mixin;

import com.texturestudio.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: smaller field of view. */
@Mixin(GameRenderer.class)
public abstract class CameraFovMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void pinvisuals$zoom(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Zoom.apply(cir.getReturnValueF()));
	}
}
