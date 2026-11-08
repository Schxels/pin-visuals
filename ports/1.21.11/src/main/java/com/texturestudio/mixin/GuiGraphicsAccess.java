package com.texturestudio.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives the menu access to the GUI render state, to submit batched fills (see FillBatch). */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccess {
	@Accessor("guiRenderState")
	GuiRenderState pinvisuals$renderState();
}
