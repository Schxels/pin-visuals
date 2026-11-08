package com.texturestudio.mixin;

import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FPS tweak "calm end crystals": the crystal is drawn with age 0, so it neither spins nor bounces. */
@Mixin(EndCrystalRenderer.class)
public class EndCrystalRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
			at = @At("TAIL"))
	private void pinvisuals$calm(EndCrystal crystal, EndCrystalRenderState state, float partial, CallbackInfo ci) {
		if (Tweaks.calmCrystals) {
			state.ageInTicks = 0f;
		}
	}
}
