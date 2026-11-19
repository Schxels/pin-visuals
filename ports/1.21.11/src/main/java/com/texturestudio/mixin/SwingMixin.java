package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swing animation: how long your own arm swing takes (only the animation; attacks and mining speed stay the same).
 * "Mining fatigue" works like the Fake Fatigue mod: as slow as with that effect.
 */
@Mixin(LivingEntity.class)
public abstract class SwingMixin {

	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
	private void pinvisuals$swing(CallbackInfoReturnable<Integer> cir) {
		if (!((Object) this instanceof LocalPlayer)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean mining = mc.gameMode != null && mc.gameMode.isDestroying();
		int base = cir.getReturnValueI();
		int d = Hooks.swingDuration(base, mining);
		if (d != base) {
			cir.setReturnValue(d);
		}
	}
}
