package com.texturestudio.mixin;

import com.texturestudio.Atmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Atmosphere (1.21.11): here setupFog writes the fog straight into the GPU buffer, so the values are changed on
 * their way into updateBuffer (see {@link Atmosphere#applyFog}).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

	@ModifyArgs(method = "setupFog", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
	private void texturestudio$atmosphere(Args args) {
		if (!Atmosphere.fogActive(Minecraft.getInstance().gameRenderer.getMainCamera())) {
			return;
		}
		float[] v = new float[6];
		for (int i = 0; i < 6; i++) {
			v[i] = args.get(3 + i);
		}
		Vector4f color = new Vector4f((Vector4f) args.get(2));
		Atmosphere.applyFog(v, color);
		args.set(2, color);
		for (int i = 0; i < 6; i++) {
			args.set(3 + i, v[i]);
		}
	}
}
