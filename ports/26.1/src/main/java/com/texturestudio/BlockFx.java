package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.texturestudio.mixin.GameModeAccess;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Block Break Animation (a battery that charges up, a colour that fills the block, or a glow instead of the cracks)
 * and Block Place Animation (a placed block pops up a little bigger and settles).
 */
final class BlockFx {

	static final String[] BREAK_STYLES = { "Battery bar", "Colour fill", "Glow" };

	private record Watch(BlockPos pos, BlockState before, int until) {
	}

	private record Pop(BlockPos pos, BlockState state, long start) {
	}

	private static final List<Watch> WATCH = new ArrayList<>();
	private static final List<Pop> POPS = new ArrayList<>();
	private static int ticks;
	private static float shown;
	private static BlockPos shownPos;
	private static long lastFrame = System.nanoTime();
	private static float lastRaw;
	private static long lastChange;

	private BlockFx() {
	}

	static boolean breakOn() {
		return Features.breakOn != null && Features.breakOn.b();
	}

	static boolean placeOn() {
		return Features.placeOn != null && Features.placeOn.b();
	}

	/** Own cracks hidden (another break animation replaces them)? */
	static boolean hideCracks() {
		return breakOn() && Features.breakHideCracks.b();
	}

	static void register() {
		// the client places the block right after this callback: watch the two possible spots for a change
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && placeOn()) {
				BlockPos a = hit.getBlockPos();
				BlockPos b = a.relative(hit.getDirection());
				WATCH.add(new Watch(b.immutable(), level.getBlockState(b), ticks + 4));
				WATCH.add(new Watch(a.immutable(), level.getBlockState(a), ticks + 4));
			}
			return InteractionResult.PASS;
		});
	}

	static void tick(Minecraft mc) {
		ticks++;
		if (mc.level == null) {
			WATCH.clear();
			POPS.clear();
			return;
		}
		Iterator<Watch> it = WATCH.iterator();
		while (it.hasNext()) {
			Watch w = it.next();
			BlockState now = mc.level.getBlockState(w.pos);
			if (now != w.before && !now.isAir() && now.getRenderShape() == RenderShape.MODEL) {
				POPS.removeIf(p -> p.pos.equals(w.pos));
				POPS.add(new Pop(w.pos, now, System.nanoTime()));
				WATCH.removeIf(o -> o.pos.equals(w.pos) || o.until <= ticks);
				break;
			}
			if (w.until <= ticks) {
				it.remove();
			}
		}
	}

	static void render(PoseStack pose, SubmitNodeCollector out, Vec3 cam, float pt) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		if (placeOn()) {
			renderPops(pose, out, cam, mc, now);
		} else {
			POPS.clear();
		}
		if (!breakOn() || mc.gameMode == null) {
			shown = 0;
			return;
		}
		GameModeAccess gm = (GameModeAccess) mc.gameMode;
		BlockPos pos = gm.pinvisuals$pos();
		// (isDestroying() is not reliable in every version: a progress above 0 means you are mining)
		float raw = gm.pinvisuals$progress();
		if (raw != lastRaw) {
			lastRaw = raw;
			lastChange = now;
		}
		// still mining = the progress moved in the last moment (it grows every tick while you hold the button)
		boolean active = raw > 0f && now - lastChange < 300_000_000L && pos != null && !mc.level.getBlockState(pos).isAir();
		float target = active ? Math.max(0f, Math.min(1f, raw)) : 0f;
		if (active && !pos.equals(shownPos)) {
			shownPos = pos.immutable();
			shown = 0;
		}
		// the game counts in ticks: glide in between so it fills smoothly every frame
		shown += (target - shown) * Math.min(1f, dt * 18f);
		if (!active || shownPos == null) {
			return;
		}
		float p = shown;
		switch (Features.breakStyle.i()) {
			case 1 -> fill(pose, out, cam, shownPos, p);
			case 2 -> glowBlock(pose, out, cam, shownPos, p);
			default -> battery(pose, out, cam, shownPos, p, mc);
		}
	}

	// ------------------------------------------------------------------ break styles

	private static int chargeColor(float p) {
		if (p < 0.5f) {
			return WorldFx.mix(0xFFFF4040, 0xFFFFD43B, p * 2f);
		}
		return WorldFx.mix(0xFFFFD43B, 0xFF3BFF72, (p - 0.5f) * 2f);
	}

	/** A battery in front of the block that charges up; when it is full the block breaks. */
	private static void battery(PoseStack pose, SubmitNodeCollector out, Vec3 cam, BlockPos pos, float p, Minecraft mc) {
		Vec3 c = Vec3.atCenterOf(pos);
		Vec3 toCam = cam.subtract(c);
		double d = toCam.length();
		if (d < 0.01) {
			return;
		}
		Vec3 at = c.add(toCam.scale(Math.min(0.85, d * 0.5) / d));
		float size = Features.breakSize.f() / 100f;
		float t = WorldFx.seconds();
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
		pose.scale(size, size, size);
		int fillCol = chargeColor(p);
		WorldFx.draw(pose, out, q -> {
			// a lying battery: a rectangle that fills from left to right while you mine. No part lies on top of
			// another (see-through geometry is sorted, overlapping parts would flicker or hide each other).
			float w = 0.34f, h = 0.10f, b = 0.02f, g = 0.05f;
			int glowC = WorldFx.argb((int) (50 + 80 * p), fillCol);
			int glowO = WorldFx.argb(0, fillCol);
			// soft glow around the outside
			q.quad(new float[] { -w - g, h, 0 }, new float[] { w + g, h, 0 }, new float[] { w + g, h + g, 0 },
					new float[] { -w - g, h + g, 0 }, glowC, glowC, glowO, glowO);
			q.quad(new float[] { -w - g, -h - g, 0 }, new float[] { w + g, -h - g, 0 }, new float[] { w + g, -h, 0 },
					new float[] { -w - g, -h, 0 }, glowO, glowO, glowC, glowC);
			q.quad(new float[] { -w - g, -h, 0 }, new float[] { -w, -h, 0 }, new float[] { -w, h, 0 }, new float[] { -w - g, h, 0 },
					glowO, glowC, glowC, glowO);
			// frame and the little terminal on the right
			int frame = 0xFFF2F2F5;
			q.rect(-w, h - b, w, h, frame);
			q.rect(-w, -h, w, -h + b, frame);
			q.rect(-w, -h + b, -w + b, h - b, frame);
			q.rect(w - b, -h + b, w, h - b, frame);
			q.rect(w, -h * 0.45f, w + 0.035f, h * 0.45f, frame);
			// inside: filled part, a bright front edge, then the empty dark rest
			float x0 = -w + b, x1 = w - b, y0 = -h + b, y1 = h - b;
			float fx = x0 + (x1 - x0) * Math.max(0f, Math.min(1f, p));
			float edgeW = Math.min(0.014f, fx - x0);
			if (fx - edgeW > x0 + 0.001f) {
				q.rectV(x0, y0, fx - edgeW, y1, WorldFx.mix(fillCol, 0xFF000000, 0.3f), WorldFx.mix(fillCol, 0xFFFFFFFF, 0.25f));
			}
			if (edgeW > 0.001f) {
				q.rect(fx - edgeW, y0, fx, y1, WorldFx.argb((int) (190 + 60 * Math.sin(t * 9)), 0xFFFFFF));
			}
			if (x1 - fx > 0.001f) {
				q.rect(fx, y0, x1, y1, 0xE6141418);
			}
		});
		pose.popPose();
	}

	/** Colour that rises inside the block like a liquid. */
	private static void fill(PoseStack pose, SubmitNodeCollector out, Vec3 cam, BlockPos pos, float p) {
		int col = Features.breakColor.i();
		float t = WorldFx.seconds();
		pose.pushPose();
		pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
		WorldFx.draw(pose, out, q -> {
			float e = 0.004f;
			float top = Math.max(0.02f, p) * (1 + 2 * e) - e;
			q.box(-e, -e, -e, 1 + e, top, 1 + e, WorldFx.argb(120, col));
			float wave = 0.012f * (float) Math.sin(t * 6);
			q.quad(new float[] { -e, top + wave, -e }, new float[] { 1 + e, top - wave, -e }, new float[] { 1 + e, top + wave, 1 + e },
					new float[] { -e, top - wave, 1 + e }, WorldFx.argb(200, WorldFx.mix(col, 0xFFFFFFFF, 0.35f)));
		});
		pose.popPose();
	}

	/** The block glows stronger and stronger in your colour. */
	private static void glowBlock(PoseStack pose, SubmitNodeCollector out, Vec3 cam, BlockPos pos, float p) {
		int col = Features.breakColor.i();
		float t = WorldFx.seconds();
		float pulse = 0.85f + 0.15f * (float) Math.sin(t * (6 + 10 * p));
		pose.pushPose();
		pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
		WorldFx.draw(pose, out, q -> {
			float e = 0.004f;
			q.box(-e, -e, -e, 1 + e, 1 + e, 1 + e, WorldFx.argb((int) (190 * p * pulse), col));
			float g = 0.03f + 0.05f * p;
			q.box(-g, -g, -g, 1 + g, 1 + g, 1 + g, WorldFx.argb((int) (60 * p * pulse), col));
		});
		pose.popPose();
	}

	// ------------------------------------------------------------------ place pop

	private static void renderPops(PoseStack pose, SubmitNodeCollector out, Vec3 cam, Minecraft mc, long now) {
		float len = 0.45f * 100f / Features.placeSpeed.f();
		float amp = Features.placeStrength.f() / 100f;
		Iterator<Pop> it = POPS.iterator();
		while (it.hasNext()) {
			Pop pop = it.next();
			float t = (now - pop.start) / 1_000_000_000f / len;
			if (t >= 1f || mc.level.getBlockState(pop.pos) != pop.state) {
				it.remove();
				continue;
			}
			// big at once, then a springy settle to the real size
			float s = 1f + amp * (float) (Math.exp(-t * 5.5) * Math.cos(t * 13.0));
			pose.pushPose();
			pose.translate(pop.pos.getX() - cam.x + 0.5, pop.pos.getY() - cam.y + 0.5, pop.pos.getZ() - cam.z + 0.5);
			if (s > 1.004f) {
				pose.pushPose();
				pose.scale(s, s, s);
				pose.translate(-0.5, -0.5, -0.5);
				MovingBlockRenderState st = new MovingBlockRenderState();
				st.randomSeedPos = pop.pos;
				st.blockPos = pop.pos;
				st.blockState = pop.state;
				st.biome = mc.level.getBiome(pop.pos);
				st.cardinalLighting = mc.level.cardinalLighting();
				st.lightEngine = mc.level.getLightEngine();
				out.submitMovingBlock(pose, st);
				pose.popPose();
			}
			if (Features.placeFlash.b()) {
				float a = (1f - t) * (1f - t);
				float g = 0.51f + 0.12f * t;
				pose.translate(-0.5, -0.5, -0.5);
				WorldFx.draw(pose, out, q -> q.box(0.5f - g, 0.5f - g, 0.5f - g, 0.5f + g, 0.5f + g, 0.5f + g,
						WorldFx.argb((int) (110 * a), 0xFFFFFF)));
			}
			pose.popPose();
		}
	}
}
