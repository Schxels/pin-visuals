package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dura Guard: no mining (or sword/axe hits) while the held tool is about to break. */
@Mixin(MultiPlayerGameMode.class)
public class DuraGuardMixin {

	@Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$start(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
		if (Hooks.duraBlocks(false)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$continue(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
		if (Hooks.duraBlocks(false)) {
			((MultiPlayerGameMode) (Object) this).stopDestroyBlock();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$attack(Player player, Entity target, CallbackInfo ci) {
		if (Hooks.duraBlocks(true)) {
			ci.cancel();
		}
	}
}
