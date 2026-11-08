package com.texturestudio.mixin;

import com.texturestudio.HitColor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the "entity colour overlay" texture (red hit flash) to {@link HitColor} so it can be recoloured. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {

	@Shadow
	@Final
	private DynamicTexture texture;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void texturestudio$attach(CallbackInfo ci) {
		HitColor.attach(texture);
	}
}
