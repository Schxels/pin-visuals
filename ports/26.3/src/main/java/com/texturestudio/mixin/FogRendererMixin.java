package com.texturestudio.mixin;

import com.texturestudio.Atmosphere;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Atmosphere: pulls the fog in and tints it (see {@link Atmosphere#applyFog}). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void texturestudio$atmosphere(Camera camera, int renderDistance, DeltaTracker delta, float darken,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		FogData d = cir.getReturnValue();
		if (d == null || !Atmosphere.fogActive(camera)) {
			return;
		}
		float[] v = { d.environmentalStart, d.environmentalEnd, d.renderDistanceStart, d.renderDistanceEnd, d.skyEnd,
				d.cloudEnd };
		Atmosphere.applyFog(v, d.color);
		d.environmentalStart = v[0];
		d.environmentalEnd = v[1];
		d.renderDistanceStart = v[2];
		d.renderDistanceEnd = v[3];
		d.skyEnd = v[4];
		d.cloudEnd = v[5];
	}
}
