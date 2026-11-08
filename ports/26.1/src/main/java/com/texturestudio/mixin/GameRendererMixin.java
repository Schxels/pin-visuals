package com.texturestudio.mixin;

import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "No totem pop": skip the on-screen totem animation. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$noTotemPop(ItemStack stack, CallbackInfo ci) {
		if (Tweaks.noTotemPop && stack != null && stack.is(Items.TOTEM_OF_UNDYING)) {
			ci.cancel();
		}
	}
}
