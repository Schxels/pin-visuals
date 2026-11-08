package com.texturestudio.mixin;

import com.texturestudio.Tweaks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Particle tweaks: no totem burst, and the FPS tweaks "no explosion particles" / "fewer hit particles".
 * Emitters (totem, crits on hit) and single particles are both filtered.
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
			at = @At("HEAD"), cancellable = true)
	private void pinvisuals$filterEmitter(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
		if (Tweaks.noTotemParticles && options.getType() == ParticleTypes.TOTEM_OF_UNDYING
				|| pinvisuals$blocked(options.getType())) {
			ci.cancel();
		}
	}

	@Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true)
	private void pinvisuals$filterParticle(ParticleOptions options, double x, double y, double z, double vx, double vy,
			double vz, CallbackInfoReturnable<Particle> cir) {
		if (pinvisuals$blocked(options.getType())) {
			cir.setReturnValue(null);
		}
	}

	@Unique
	private static boolean pinvisuals$blocked(ParticleType<?> t) {
		if (Tweaks.noExplosionParticles && (t == ParticleTypes.EXPLOSION || t == ParticleTypes.EXPLOSION_EMITTER)) {
			return true;
		}
		return Tweaks.fewerHitParticles && (t == ParticleTypes.CRIT || t == ParticleTypes.ENCHANTED_HIT
				|| t == ParticleTypes.SWEEP_ATTACK || t == ParticleTypes.DAMAGE_INDICATOR);
	}
}
