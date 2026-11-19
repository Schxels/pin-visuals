package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item swap animation: the new main-hand item is taken at once, so the vanilla lower-and-raise does not happen. */
@Mixin(ItemInHandRenderer.class)
public class HandSwapMixin {

	@Shadow
	private ItemStack mainHandItem;

	@Inject(method = "tick", at = @At("HEAD"))
	private void pinvisuals$swap(CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (Hooks.smoothSwap() && mc.player != null) {
			ItemStack now = mc.player.getMainHandItem();
			if (mainHandItem != now) {
				mainHandItem = now;
			}
		}
	}
}
