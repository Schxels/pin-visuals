package com.texturestudio.mixin;

import com.texturestudio.Atmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Atmosphere: pulls the fog in and tints it (see {@link Atmosphere#applyFog}).
 * <p>
 * Changed in updateBuffer (right before the fog goes to the GPU) and not at the end of setupFog: mods like Sodium
 * Extra change the fog after setupFog and would otherwise undo it.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

	@Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
	private void texturestudio$atmosphere(FogData d, CallbackInfo ci) {
		if (d == null || !Atmosphere.fogActive(Minecraft.getInstance().gameRenderer.mainCamera())) {
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
