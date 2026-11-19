package com.texturestudio;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Trail (Player): particles behind you while you walk. Only on your screen. */
public final class Trail {

	static final String[] NAMES = { "Dust", "Swirls", "Flame", "Soul flame", "End rod", "Hearts", "Notes",
			"Cherry petals", "Snowflakes", "Glow", "Totem", "Sparks" };

	private static final Random RNG = new Random();
	private static Vec3 last;
	private static double carry;

	private Trail() {
	}

	/** Types that use the colour setting. */
	static boolean coloured(int type) {
		return type == 0 || type == 1;
	}

	static void tick(Minecraft mc) {
		Player p = mc.player;
		if (Features.trailOn == null || !Features.trailOn.b() || p == null || mc.level == null || mc.isPaused()) {
			last = null;
			return;
		}
		Vec3 now = p.position();
		Vec3 from = last;
		last = now;
		if (from == null || p.isSpectator() || p.isInvisible()) {
			return;
		}
		if (Features.trailSprint.b() && !p.isSprinting()) {
			return;
		}
		if (!Features.trailFirst.b() && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		double dx = now.x - from.x;
		double dz = now.z - from.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 0.01 || dist > 4) {
			return;
		}
		carry += dist * Features.trailAmount.get();
		int type = Features.trailType.i();
		while (carry >= 1) {
			carry -= 1;
			double t = RNG.nextDouble();
			double x = from.x + dx * t + (RNG.nextDouble() - 0.5) * 0.25;
			double y = from.y + (now.y - from.y) * t + 0.1 + RNG.nextDouble() * 0.15;
			double z = from.z + dz * t + (RNG.nextDouble() - 0.5) * 0.25;
			Particle part = mc.particleEngine.createParticle(options(type), x, y, z, 0, 0.005, 0);
			if (part != null) {
				part.setLifetime(Math.max(2,
						(int) Math.round(Features.trailLength.get() * 20 * (0.75 + RNG.nextDouble() * 0.5))));
			}
		}
	}

	private static int color() {
		return Features.trailRainbow.b() ? Features.rainbow(1, 2400L) : Features.trailColor.color() & 0xFFFFFF;
	}

	private static ParticleOptions options(int type) {
		return switch (type) {
			case 0 -> new DustParticleOptions(color(), (float) (Features.trailSize.get() / 100.0));
			case 1 -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | color());
			case 2 -> ParticleTypes.FLAME;
			case 3 -> ParticleTypes.SOUL_FIRE_FLAME;
			case 4 -> ParticleTypes.END_ROD;
			case 5 -> ParticleTypes.HEART;
			case 6 -> ParticleTypes.NOTE;
			case 7 -> ParticleTypes.CHERRY_LEAVES;
			case 8 -> ParticleTypes.SNOWFLAKE;
			case 9 -> ParticleTypes.GLOW;
			case 10 -> ParticleTypes.TOTEM_OF_UNDYING;
			default -> ParticleTypes.ELECTRIC_SPARK;
		};
	}
}
