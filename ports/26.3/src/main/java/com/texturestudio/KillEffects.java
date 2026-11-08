package com.texturestudio;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side kill effects. A kill is a player that dies shortly after you hit them, or near an end crystal you
 * hit / a respawn anchor you used (crystal and anchor PvP). Everything is drawn only on this client.
 */
public final class KillEffects {

	/** How long after your last hit a player's death still counts as your kill. */
	private static final long HIT_WINDOW_MS = 10_000;
	/** Crystal / anchor explosions: time window and radius. */
	private static final long BLAST_WINDOW_MS = 2_500;
	private static final double BLAST_RADIUS = 12;

	private record Blast(Vec3 pos, long time) {
	}

	private static final class Active {
		final String id;
		final double x;
		final double y;
		final double z;
		int age;

		Active(String id, double x, double y, double z) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static final Map<Integer, Long> lastHit = new HashMap<>();
	private static final Map<Integer, Vec3> lastPos = new HashMap<>();
	private static final Map<Integer, Float> lastHealth = new HashMap<>();
	private static final List<Blast> blasts = new ArrayList<>();
	private static final Set<Integer> handled = new HashSet<>();
	private static final List<Active> active = new ArrayList<>();
	private static int boltIds = -77_000;

	private KillEffects() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				long now = System.currentTimeMillis();
				if (entity instanceof Player) {
					lastHit.put(entity.getId(), now);
				} else if (entity instanceof EndCrystal) {
					blasts.add(new Blast(entity.position(), now));
				}
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
				blasts.add(new Blast(Vec3.atCenterOf(hit.getBlockPos()), System.currentTimeMillis()));
			}
			return InteractionResult.PASS;
		});
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			lastHit.clear();
			lastPos.clear();
			lastHealth.clear();
			blasts.clear();
			handled.clear();
			active.clear();
			return;
		}
		long now = System.currentTimeMillis();
		blasts.removeIf(b -> now - b.time() > BLAST_WINDOW_MS);
		lastHit.values().removeIf(t -> now - t > HIT_WINDOW_MS);

		String effect = Cosmetics.kill;
		Set<Integer> seen = new HashSet<>();
		for (Player p : level.players()) {
			if (p == mc.player) {
				continue;
			}
			int id = p.getId();
			seen.add(id);
			boolean dead = p.isDeadOrDying() || p.getHealth() <= 0f;
			if (!dead) {
				handled.remove(id);
				if (lastHit.containsKey(id)) {
					lastPos.put(id, p.position());
					lastHealth.put(id, p.getHealth());
				}
				continue;
			}
			if (handled.add(id) && isMine(p.position(), lastHit.get(id), now)) {
				start(effect, p.getX(), p.getY(), p.getZ());
			}
		}
		// a player you just hit who vanished at low health (servers that remove dead players at once)
		for (Iterator<Map.Entry<Integer, Vec3>> it = lastPos.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Vec3> e = it.next();
			if (seen.contains(e.getKey())) {
				continue;
			}
			Long t = lastHit.get(e.getKey());
			Float hp = lastHealth.get(e.getKey());
			if (t != null && now - t < 1500 && hp != null && hp <= 4f) {
				Vec3 v = e.getValue();
				start(effect, v.x, v.y, v.z);
			}
			lastHealth.remove(e.getKey());
			it.remove();
		}

		for (Iterator<Active> it = active.iterator(); it.hasNext();) {
			Active a = it.next();
			try {
				run(level, a);
			} catch (RuntimeException ex) {
				a.age = 999;
			}
			a.age++;
			if (a.age > 40) {
				it.remove();
			}
		}
	}

	private static boolean isMine(Vec3 pos, Long hitTime, long now) {
		if (hitTime != null && now - hitTime <= HIT_WINDOW_MS) {
			return true;
		}
		for (Blast b : blasts) {
			if (b.pos().distanceTo(pos) <= BLAST_RADIUS) {
				return true;
			}
		}
		return false;
	}

	private static void start(String effect, double x, double y, double z) {
		if (effect == null || Cosmetics.NONE.equals(effect) || active.size() > 8) {
			return;
		}
		active.add(new Active(effect, x, y, z));
	}

	/** Shows an effect 4 blocks in front of you (menu preview). */
	public static void preview(String effect) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Vec3 look = mc.player.getLookAngle();
		Vec3 p = mc.player.position().add(look.x * 4, 0, look.z * 4);
		active.add(new Active(effect, p.x, p.y, p.z));
	}

	/** Colours used by the menu preview. */
	public static int[] palette(String id) {
		return switch (id) {
			case "lightning" -> new int[] { 0xFFBFDBFE, 0xFF60A5FA, 0xFFFFFFFF };
			case "firework" -> new int[] { 0xFFF472B6, 0xFFA855F7, 0xFF22D3EE, 0xFFFACC15 };
			case "souls" -> new int[] { 0xFF67E8F9, 0xFF22D3EE, 0xFFE0F2FE };
			case "hearts" -> new int[] { 0xFFF43F5E, 0xFFFB7185, 0xFFFFE4E6 };
			case "ender" -> new int[] { 0xFFA855F7, 0xFF7E22CE, 0xFFE879F9 };
			case "crimson" -> new int[] { 0xFFB91C1C, 0xFFEF4444, 0xFF7F1D1D };
			default -> new int[] { 0xFFF97316, 0xFFFACC15, 0xFFEF4444 };
		};
	}

	// ---------------------------------------------------------------- effects

	private static void run(ClientLevel level, Active a) {
		double x = a.x;
		double y = a.y;
		double z = a.z;
		int t = a.age;
		var r = level.getRandom();
		switch (a.id) {
			case "lightning" -> {
				if (t == 0) {
					if (!bolt(level, x, y, z)) {
						sound(level, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
					}
					sound(level, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1f);
				}
				if (t < 8) {
					for (int i = 0; i < 14; i++) {
						double ang = r.nextDouble() * Math.PI * 2;
						double rad = 0.3 + t * 0.25;
						part(level, ParticleTypes.ELECTRIC_SPARK, x + Math.cos(ang) * rad, y + 0.1 + r.nextDouble() * 1.8,
								z + Math.sin(ang) * rad, Math.cos(ang) * 0.2, 0.1, Math.sin(ang) * 0.2);
					}
				}
				if (t < 3) {
					for (int i = 0; i < 20; i++) {
						part(level, ParticleTypes.END_ROD, x, y + i * 0.5, z, (r.nextDouble() - 0.5) * 0.08, 0.02,
								(r.nextDouble() - 0.5) * 0.08);
					}
				}
			}
			case "firework" -> {
				if (t == 0 || t == 7 || t == 13) {
					int k = t / 6;
					FireworkExplosion.Shape shape = k == 0 ? FireworkExplosion.Shape.LARGE_BALL
							: k == 1 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.BURST;
					IntList colors = IntList.of(0xF472B6, 0xA855F7, 0x22D3EE, 0xFACC15);
					IntList fade = IntList.of(0xFFFFFF);
					level.createFireworks(x, y + 1.6 + k * 0.6, z, 0, 0, 0,
							List.of(new FireworkExplosion(shape, colors, fade, true, true)), true);
				}
			}
			case "souls" -> {
				if (t == 0) {
					sound(level, x, y, z, SoundEvents.SOUL_ESCAPE.value(), 1.5f, 0.9f);
					sound(level, x, y, z, SoundEvents.WITHER_AMBIENT, 0.3f, 1.6f);
				}
				if (t < 24) {
					for (int i = 0; i < 4; i++) {
						double ang = t * 0.45 + i * Math.PI / 2;
						double rad = 0.8 - t * 0.02;
						part(level, ParticleTypes.SOUL, x + Math.cos(ang) * rad, y + t * 0.09, z + Math.sin(ang) * rad, 0,
								0.05, 0);
					}
					if (t % 3 == 0) {
						part(level, ParticleTypes.SCULK_SOUL, x, y + 0.6 + t * 0.06, z, 0, 0.08, 0);
					}
				}
			}
			case "hearts" -> {
				if (t == 0) {
					sound(level, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 1.2f);
					sound(level, x, y, z, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
					for (int i = 0; i < 18; i++) {
						double ang = i * Math.PI * 2 / 18;
						part(level, ParticleTypes.HEART, x + Math.cos(ang) * 1.1, y + 1, z + Math.sin(ang) * 1.1, 0, 0.1, 0);
					}
				}
				if (t < 16 && t % 2 == 0) {
					part(level, ParticleTypes.HEART, x + (r.nextDouble() - 0.5), y + 1.8 + t * 0.12,
							z + (r.nextDouble() - 0.5), 0, 0.1, 0);
				}
			}
			case "ender" -> {
				if (t == 0) {
					sound(level, x, y, z, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.7f);
					sound(level, x, y, z, SoundEvents.ENDER_EYE_DEATH, 1f, 0.8f);
					for (int i = 0; i < 80; i++) {
						part(level, ParticleTypes.PORTAL, x, y + 1, z, (r.nextDouble() - 0.5) * 3, r.nextDouble() * 2,
								(r.nextDouble() - 0.5) * 3);
					}
				}
				if (t < 20) {
					for (int i = 0; i < 6; i++) {
						double ang = r.nextDouble() * Math.PI * 2;
						part(level, ParticleTypes.REVERSE_PORTAL, x + Math.cos(ang) * 0.5, y + r.nextDouble() * 2,
								z + Math.sin(ang) * 0.5, 0, 0.12, 0);
					}
				}
			}
			case "crimson" -> {
				if (t == 0) {
					sound(level, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
					sound(level, x, y, z, SoundEvents.WITHER_BREAK_BLOCK, 0.4f, 1.4f);
					BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK,
							Blocks.REDSTONE_BLOCK.defaultBlockState());
					for (int i = 0; i < 70; i++) {
						part(level, blood, x + (r.nextDouble() - 0.5) * 0.6, y + 0.4 + r.nextDouble() * 1.4,
								z + (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, r.nextDouble() * 0.5,
								(r.nextDouble() - 0.5) * 0.6);
					}
				}
				if (t < 10) {
					for (int i = 0; i < 4; i++) {
						part(level, ParticleTypes.DAMAGE_INDICATOR, x + (r.nextDouble() - 0.5), y + 1 + r.nextDouble(),
								z + (r.nextDouble() - 0.5), 0, 0.2, 0);
					}
				}
			}
			default -> { // flames
				if (t == 0) {
					sound(level, x, y, z, SoundEvents.BLAZE_SHOOT, 1.2f, 0.8f);
					sound(level, x, y, z, SoundEvents.FIRECHARGE_USE, 1f, 0.7f);
				}
				if (t < 26) {
					for (int i = 0; i < 3; i++) {
						double ang = t * 0.5 + i * Math.PI * 2 / 3;
						double rad = 0.7;
						part(level, i == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
								x + Math.cos(ang) * rad, y + t * 0.1, z + Math.sin(ang) * rad, 0, 0.02, 0);
					}
					if (t % 4 == 0) {
						part(level, ParticleTypes.LAVA, x, y + 0.5, z, 0, 0, 0);
					}
				}
			}
		}
	}

	/** A lightning bolt that only exists on this client (visual + its own thunder sound). */
	private static boolean bolt(ClientLevel level, double x, double y, double z) {
		try {
			LightningBolt b = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (b == null) {
				return false;
			}
			b.setVisualOnly(true);
			b.setId(boltIds--);
			b.setPos(x, y, z);
			level.addEntity(b);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void part(ClientLevel level, ParticleOptions p, double x, double y, double z, double vx, double vy,
			double vz) {
		level.addParticle(p, x, y, z, vx, vy, vz);
	}

	private static void sound(ClientLevel level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
		level.playLocalSound(x, y, z, s, SoundSource.PLAYERS, vol, pitch, false);
	}
}
