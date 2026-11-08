package com.texturestudio.mixin;

import com.texturestudio.PopChams;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity event 35 = "used a totem of undying": shows the pop ghost (see {@link PopChams}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPopMixin {

	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void texturestudio$totemPop(byte id, CallbackInfo ci) {
		if (id == 35 && (Object) this instanceof Player p && p.level().isClientSide()) {
			PopChams.onPop(p);
		}
	}
}
