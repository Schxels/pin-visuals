package com.texturestudio;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Pop chams: when another player pops a totem, a coloured, see-through copy of them (frozen in their pose) appears
 * where they stood, rises a little and fades out. The copy is a client-only player entity with a negative id that is
 * invisible itself; {@link CosmeticLayer} draws it as a glowing coloured silhouette.
 */
public final class PopChams {

	/** A ghost: base position and when it appeared. */
	public record Ghost(double x, double y, double z, long born, long lifeMs) {
	}

	private static final double RISE = 0.9;

	private static final Map<Integer, Ghost> GHOSTS = new ConcurrentHashMap<>();
	private static int nextId = -2_000_000;

	private PopChams() {
	}

	public static boolean enabled() {
		return !Cosmetics.NONE.equals(Cosmetics.popChams);
	}

	/** Called (client side) when a player plays the totem event. */
	public static void onPop(Player p) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || mc.level == null || p == mc.player || p.getId() < 0) {
			return;
		}
		spawn(mc.level, p);
	}

	/** Menu preview: a ghost of yourself 3 blocks in front of you, facing you. */
	public static void preview() {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (mc.level == null || p == null) {
			return;
		}
		double yaw = Math.toRadians(p.getYRot());
		double x = p.getX() - Math.sin(yaw) * 3;
		double z = p.getZ() + Math.cos(yaw) * 3;
		spawn(mc.level, p, x, p.getY(), z, p.getYRot() + 180f);
	}

	/** Spawns a ghost of p where p stands. */
	public static void spawn(ClientLevel level, Player p) {
		spawn(level, p, p.getX(), p.getY(), p.getZ(), p.yBodyRot);
	}

	private static void spawn(ClientLevel level, Player p, double x, double y, double z, float bodyYaw) {
		try {
			RemotePlayer g = new RemotePlayer(level, p.getGameProfile());
			int id = nextId--;
			g.setId(id);
			g.setUUID(java.util.UUID.randomUUID()); // the level refuses a second entity with the player's UUID
			g.setPos(x, y, z);
			g.setOldPosAndRot();
			float turn = bodyYaw - p.yBodyRot;
			g.setYRot(p.getYRot() + turn);
			g.setXRot(p.getXRot());
			g.yHeadRot = p.getYHeadRot() + turn;
			g.yHeadRotO = g.yHeadRot;
			g.yBodyRot = bodyYaw;
			g.yBodyRotO = bodyYaw;
			g.setPose(p.getPose());
			g.setInvisible(true);
			g.noPhysics = true;
			g.setNoGravity(true);
			GHOSTS.put(id, new Ghost(x, y, z, System.currentTimeMillis(), Math.round(Tweaks.popSeconds * 1000)));
			level.addEntity(g);
		} catch (RuntimeException e) {
			TextureStudioClient.LOGGER.warn("Pin Visuals: could not show the pop ghost", e);
		}
	}

	/** Ghost data for an entity id, or null if it is not a ghost. */
	public static Ghost ghost(int id) {
		return id < 0 ? GHOSTS.get(id) : null;
	}

	/** 0..1 how far the ghost's life is. */
	public static float progress(Ghost g) {
		return Math.min(1f, (System.currentTimeMillis() - g.born()) / (float) Math.max(1, g.lifeMs()));
	}

	/** Called every client tick: moves the ghosts up and removes old ones. */
	public static void tick(Minecraft mc) {
		if (GHOSTS.isEmpty()) {
			return;
		}
		ClientLevel level = mc.level;
		Iterator<Map.Entry<Integer, Ghost>> it = GHOSTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Ghost> e = it.next();
			Ghost g = e.getValue();
			float f = progress(g);
			Entity ent = level == null ? null : level.getEntity(e.getKey());
			if (f >= 1f || ent == null) {
				it.remove();
				if (level != null && ent != null) {
					level.removeEntity(e.getKey(), Entity.RemovalReason.DISCARDED);
				}
				continue;
			}
			// ease out: fast at first, then slowing down
			double rise = RISE * (1 - (1 - f) * (1 - f));
			ent.setOldPosAndRot();
			ent.setPos(g.x(), g.y() + rise, g.z());
			ent.setDeltaMovement(0, 0, 0);
		}
	}

	/** Ghost colour (ARGB without alpha) of a style at the given time. */
	public static int color(String id, long ms) {
		return switch (id) {
			case "blue" -> 0x38BDF8;
			case "purple" -> 0xA855F7;
			case "gold" -> 0xFACC15;
			case "green" -> 0x4ADE80;
			case "white" -> 0xF1F5F9;
			case "pink" -> 0xF472B6;
			case "rainbow" -> HitColor.hsv((ms % 1600L) / 1600f) & 0xFFFFFF;
			default -> 0xEF4444;
		};
	}
}
