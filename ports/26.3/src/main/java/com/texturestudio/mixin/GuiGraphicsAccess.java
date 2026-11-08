package com.texturestudio.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives the menu access to the GUI render state, to submit batched fills (see FillBatch). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccess {
	@Accessor("guiRenderState")
	GuiRenderState pinvisuals$renderState();
}
