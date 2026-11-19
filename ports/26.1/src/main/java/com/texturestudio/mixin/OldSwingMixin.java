package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Swing animation "1.7": the item does not dip down after a hit (no attack cooldown in the animation). */
@Mixin(ItemInHandRenderer.class)
public abstract class OldSwingMixin {

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float pinvisuals$noDip(LocalPlayer player, float partialTick) {
		if (Hooks.oldSwing()) {
			return 1f;
		}
		return player.getItemSwapScale(partialTick);
	}
}
