package com.texturestudio;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Spotify lyrics softly "in the sky": high above the horizon (about 34 degrees), in the smooth menu font,
 * slightly see-through with a faint halo instead of a backdrop. The lines hang at a sky direction that only slowly
 * follows your view, so they drift a little when you turn, like something written in the sky.
 */
public final class LyricsHud {

	/** Height of the lyrics above the horizon in degrees. */
	private static final float ELEVATION = 34f;

	private static int shownIndex = -1;
	private static long changedAt;
	private static float anchorYaw = Float.NaN;
	private static long lastFrame;

	private static Identifier haloId;
	private static boolean haloFailed;

	private LyricsHud() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		// (with F1 the HUD, and so this element, is not drawn at all)
		if (mc.player == null || !Lyrics.enabled() || Lyrics.state() != Lyrics.State.READY) {
			anchorYaw = Float.NaN;
			return;
		}
		List<Lyrics.Line> lines = Lyrics.lines();
		if (lines.isEmpty()) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		float pt = delta.getGameTimeDeltaPartialTick(true);
		long now = System.currentTimeMillis();

		// lazy sky anchor: follows the view direction slowly
		float yaw = mc.player.getYRot(pt);
		if (Float.isNaN(anchorYaw)) {
			anchorYaw = yaw;
		}
		float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		float diff = wrap(yaw - anchorYaw);
		anchorYaw += diff * Math.min(1f, dt * 1.6f);
		diff = wrap(yaw - anchorYaw);

		float vfov = mc.options.fov().get();
		double halfV = Math.toRadians(vfov / 2f);
		double halfH = Math.atan(Math.tan(halfV) * w / (double) h);
		double angV = Math.toRadians(ELEVATION + mc.player.getXRot(pt));
		if (Math.abs(angV) > halfV + 0.4) {
			return;
		}
		double angH = Math.toRadians(-diff);
		float cy = (float) (h / 2.0 - Math.tan(angV) / Math.tan(halfV) * h / 2.0);
		float cx = (float) (w / 2.0 + Math.tan(angH) / Math.tan(halfH) * w / 2.0);
		// fade out towards the lower half and outside the screen
		float vis = 1f - clamp((cy - h * 0.45f) / (h * 0.25f));
		vis = Math.min(vis, clamp((cy + 30) / 50f));
		if (vis <= 0.02f) {
			return;
		}

		long pos = Lyrics.position() + 150; // a little early reads nicer
		int idx = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() <= pos) {
				idx = i;
			} else {
				break;
			}
		}
		if (idx != shownIndex) {
			shownIndex = idx;
			changedAt = now;
		}
		float slide = clamp((now - changedAt) / 420f);
		slide = 1f - (1f - slide) * (1f - slide) * (1f - slide);

		int[] col = Lyrics.colors(Cosmetics.lyrics);
		Font font = MenuFonts.get("nunito");

		String cur = idx >= 0 ? lines.get(idx).text() : "";
		if (cur.isEmpty()) {
			cur = "♪";
		}
		long start = idx >= 0 ? lines.get(idx).timeMs() : 0;
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
		float sung = end > start ? clamp((pos - start) / (float) (end - start)) : 1f;

		float big = Math.max(1f, Math.min(1.6f, w * 0.7f / Math.max(1, font.width(cur))));
		float yCur = cy + (1f - slide) * 8f;

		final String line = cur;
		final int fIdx = idx;
		final float fSlide = slide;
		final float fVis = vis;
		Runnable paint = () -> {
			// faint coloured halo instead of a backdrop
			if (col[1] != 0) {
				float hw = Math.max(120, font.width(line) * big + 70);
				halo(g, mc, cx - hw / 2f, yCur - 22, hw, 9 * big + 44, col[1], (int) (0x38 * fVis));
			}

			// song title for a few seconds when a new song starts
			long since = now - Lyrics.trackSince();
			if (since < 5000) {
				float a = since < 4000 ? 1f : 1f - (since - 4000) / 1000f;
				String head = "♪  " + Lyrics.title() + "  ·  " + Lyrics.artist();
				text(g, font, head, cx, yCur - 16, 0.85f, alpha(col[0], (int) (0x90 * a * fVis)), false);
			}

			// previous line drifting up and fading
			if (fIdx > 0 && fSlide < 1f) {
				text(g, font, lines.get(fIdx - 1).text(), cx, yCur - 14 - fSlide * 6, 1.1f,
						alpha(col[0], (int) (0x70 * (1f - fSlide) * fVis)), false);
			}

			// current line: soft base, the sung part brighter on top
			float a = Math.min(1f, fSlide * 1.3f) * fVis;
			float left = cx - font.width(line) * big / 2f;
			at(g, font, line, left, yCur, big, alpha(col[0], (int) (0x78 * a)), false);
			int k = Math.round(line.length() * sung);
			if (k > 0) {
				String done = line.substring(0, Math.min(line.length(), k));
				int c = "aurora".equals(Cosmetics.lyrics) ? Cosmetics.lerp(col[0], col[2], sung) : col[2];
				at(g, font, done, left, yCur, big, alpha(c, (int) (0xE6 * a)), false);
			}

			// next line, small and faint
			if (fIdx + 1 < lines.size()) {
				String next = lines.get(fIdx + 1).text();
				if (!next.isEmpty()) {
					text(g, font, next, cx, yCur + 9 * big + 5, 1f, alpha(col[0], (int) (0x48 * fVis)), false);
				}
			}
		};

		if (Tweaks.lyricsThroughBlocks) {
			paint.run();
			return;
		}
		// only where the sky is free: rays from the eye through columns of the lyrics, blocked parts are cut out
		float bw = Math.max(140, font.width(cur) * big + 60);
		boolean[] free = skyMask(mc, pt, cx - bw / 2f, bw, yCur + 4 * big, w, h, halfH, halfV);
		int n = free.length;
		for (int i = 0; i < n;) {
			if (!free[i]) {
				i++;
				continue;
			}
			int j = i;
			while (j < n && free[j]) {
				j++;
			}
			int x0 = i == 0 ? 0 : Math.round(cx - bw / 2f + bw * i / n);
			int x1 = j == n ? w : Math.round(cx - bw / 2f + bw * j / n);
			FillBatch.flush(g).enableScissor(x0, 0, x1, h);
			paint.run();
			FillBatch.flush(g).disableScissor();
			i = j;
		}
	}

	private static final int MASK_COLUMNS = 20;
	private static final double SKY_RAY = 96;
	private static boolean[] lastMask;
	private static long lastMaskAt;

	/**
	 * For columns across the lyrics: is the sky free there? A ray goes from the eye through the screen point; if it
	 * hits a block within {@link #SKY_RAY} blocks that column is covered. Refreshed about 20 times a second.
	 */
	private static boolean[] skyMask(Minecraft mc, float pt, float x0, float bw, float y, int w, int h, double halfH,
			double halfV) {
		long now = System.currentTimeMillis();
		if (lastMask != null && now - lastMaskAt < 50) {
			return lastMask;
		}
		boolean[] free = new boolean[MASK_COLUMNS];
		var player = mc.player;
		Vec3 eye = player.getEyePosition(pt);
		float yaw = player.getYRot(pt);
		float pitch = player.getXRot(pt);
		double pitchOff = Math.toDegrees(Math.atan((h / 2.0 - y) / (h / 2.0) * Math.tan(halfV)));
		for (int i = 0; i < MASK_COLUMNS; i++) {
			double sx = x0 + bw * (i + 0.5) / MASK_COLUMNS;
			double yawOff = Math.toDegrees(Math.atan((sx - w / 2.0) / (w / 2.0) * Math.tan(halfH)));
			Vec3 dir = Vec3.directionFromRotation((float) (pitch - pitchOff), (float) (yaw + yawOff));
			Vec3 end = eye.add(dir.scale(SKY_RAY));
			HitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
					player));
			free[i] = hit == null || hit.getType() != HitResult.Type.BLOCK;
		}
		lastMask = free;
		lastMaskAt = now;
		return free;
	}

	private static float wrap(float deg) {
		deg %= 360f;
		if (deg > 180f) {
			deg -= 360f;
		} else if (deg < -180f) {
			deg += 360f;
		}
		return deg;
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private static void text(GuiGraphicsExtractor g, Font font, String s, float cx, float y, float scale, int color,
			boolean shadow) {
		at(g, font, s, cx - font.width(s) * scale / 2f, y, scale, color, shadow);
	}

	private static void at(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int color,
			boolean shadow) {
		if ((color >>> 24) < 6) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		FillBatch.flush(g).text(font, s, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** Soft radial light (one small texture, tinted). */
	private static void halo(GuiGraphicsExtractor g, Minecraft mc, float x, float y, float w, float h, int color,
			int a) {
		if (a < 4) {
			return;
		}
		Identifier id = haloTexture(mc);
		if (id == null) {
			return;
		}
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, id, Math.round(x), Math.round(y), 0f, 0f,
				Math.round(w), Math.round(h), 64, 64, 64, 64, alpha(color, a));
	}

	private static Identifier haloTexture(Minecraft mc) {
		if (haloId != null || haloFailed) {
			return haloId;
		}
		try {
			com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					double dx = (x + 0.5 - 32) / 32.0;
					double dy = (y + 0.5 - 32) / 32.0;
					double d = Math.min(1, Math.sqrt(dx * dx + dy * dy));
					double s = 1 - d;
					int al = (int) Math.round(255 * s * s * (3 - 2 * s));
					img.setPixel(x, y, (al << 24) | 0xFFFFFF);
				}
			}
			Identifier id = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "lyrics/halo");
			mc.getTextureManager().register(id,
					new net.minecraft.client.renderer.texture.DynamicTexture(() -> "pin visuals lyrics halo", img));
			haloId = id;
		} catch (RuntimeException e) {
			haloFailed = true;
		}
		return haloId;
	}

	private static int alpha(int c, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
	}
}
