package com.texturestudio.mixin;

import com.texturestudio.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: the mouse wheel zooms while the zoom key is held (instead of scrolling the hotbar). */
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$zoomScroll(long window, double x, double y, CallbackInfo ci) {
		if (Zoom.scroll(y)) {
			ci.cancel();
		}
	}
}
