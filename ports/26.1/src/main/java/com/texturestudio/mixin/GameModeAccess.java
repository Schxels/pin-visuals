package com.texturestudio.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block break animation: how far the block you are mining is. */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeAccess {

	@Accessor("destroyProgress")
	float pinvisuals$progress();

	@Accessor("destroyBlockPos")
	BlockPos pinvisuals$pos();
}
