package com.texturestudio.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Motion blur: sets the post effect. */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {

	@Invoker("setPostEffect")
	void pinvisuals$setPostEffect(Identifier id);
}
