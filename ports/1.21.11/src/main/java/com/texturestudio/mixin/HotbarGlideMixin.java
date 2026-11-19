package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Item swap animation: the hotbar selection slides to the new slot. */
@Mixin(Gui.class)
public class HotbarGlideMixin {

	@ModifyArg(method = "renderItemHotbar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			ordinal = 1), index = 2, require = 0)
	private int pinvisuals$glide(int x) {
		return x + Hooks.hotbarGlide();
	}
}
