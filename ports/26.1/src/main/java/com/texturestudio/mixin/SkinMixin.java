package com.texturestudio.mixin;

import com.texturestudio.Hooks;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom skin: your own player uses the chosen skin (only on your screen). */
@Mixin(AbstractClientPlayer.class)
public class SkinMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void pinvisuals$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin s = Hooks.customSkin(this, cir.getReturnValue());
		if (s != null) {
			cir.setReturnValue(s);
		}
	}
}
