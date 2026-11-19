package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Block break animation: your own cracks are hidden while another animation shows the progress. */
@Mixin(MultiPlayerGameMode.class)
public class BreakCracksMixin {

	@Redirect(method = "continueDestroyBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V"),
			require = 0)
	private void pinvisuals$cracks(ClientLevel level, int id, BlockPos pos, int stage) {
		level.destroyBlockProgress(id, pos, stage >= 0 && Hooks.hideCracks() ? -1 : stage);
	}
}
