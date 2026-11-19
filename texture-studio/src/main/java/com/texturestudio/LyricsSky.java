package com.texturestudio;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/**
 * Lyrics "Scrambled in the sky": every lyric line becomes a block of words that hangs at a fixed place in the world
 * (not stuck to the camera), 18 to 50 degrees left or right of where you looked when the line started, at a random
 * height. The block faces you only by turning around the vertical axis (yaw-only billboard). Its words are broken
 * into rows of 2 to 4 and pop in one by one at their time, each a little tilted and moved. Older blocks drift up and
 * fade out when the next line comes.
 * <p>
 * Everything "random" (side angle, height, rows, tilt, offsets) comes from a hash of the line or the word, never from
 * a new random number per frame, so nothing flickers.
 */
public final class LyricsSky {

	private static final long FADE_MS = 1400;
	private static final long POP_MS = 260;

	private record Word(String text, long at, float tilt, float dx, float dy) {
	}

	private static final class Block {
		final int index;
		final Vec3 anchor;
		final List<List<Word>> rows;
		long fadeStart = -1;

		Block(int index, Vec3 anchor, List<List<Word>> rows) {
			this.index = index;
			this.anchor = anchor;
			this.rows = rows;
		}
	}

	private static final List<Block> BLOCKS = new ArrayList<>();
	private static int lastIndex = -1;
	private static long lastTrack = -1;
	private static int counter;

	private LyricsSky() {
	}

	// ------------------------------------------------------------------ stable "random"

	private static int hash(String s, int salt) {
		int h = 0x811C9DC5 ^ salt * 0x9E3779B9;
		for (int i = 0; i < s.length(); i++) {
			h ^= s.charAt(i);
			h *= 0x01000193;
		}
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return h;
	}

	/** 0..1 from a hash. */
	private static float unit(String s, int salt) {
		return (hash(s, salt) & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	// ------------------------------------------------------------------ building a block

	private static Block build(List<Lyrics.Line> lines, int idx, Minecraft mc, float pt) {
		Lyrics.Line line = lines.get(idx);
		String[] words = line.text().trim().split("\\s+");
		String seed = idx + "|" + line.text();
		long start = line.timeMs();
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
		long dur = Math.max(400, end - start);
		long per = Math.min(550, dur * 85 / 100 / Math.max(1, words.length));
		float tiltMax = Features.lyricTilt.f();

		// rows of 2..4 words, never one word alone at the end
		List<List<Word>> rows = new ArrayList<>();
		int i = 0;
		int r = 0;
		while (i < words.length) {
			int left = words.length - i;
			int n = 2 + (int) (unit(seed, 100 + r) * 3); // 2, 3 or 4
			if (n > 4) {
				n = 4;
			}
			if (left <= 4 && left - n <= 1) {
				n = left; // the rest fits in this row (avoids a single word at the end)
			} else if (left - n == 1) {
				n = n < 4 ? n + 1 : n - 1;
			}
			n = Math.min(n, left);
			List<Word> row = new ArrayList<>();
			for (int k = 0; k < n; k++) {
				int w = i + k;
				String text = words[w];
				String ws = seed + "#" + w + text;
				long at = line.wordTimes() != null && w < line.wordTimes().length ? line.wordTimes()[w] : start + w * per;
				float tilt = (unit(ws, 1) * 2f - 1f) * tiltMax;
				float dx = (unit(ws, 2) * 2f - 1f) * 2.5f;
				float dy = (unit(ws, 3) * 2f - 1f) * 2.0f;
				row.add(new Word(text, at, tilt, dx, dy));
			}
			rows.add(row);
			i += n;
			r++;
		}

		// fixed place in the world: alternately left / right of the view, random height
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 eye = cam.position();
		float side = (counter++ % 2 == 0) ? -1f : 1f;
		double yaw = Math.toRadians(cam.yRot() + side * (18 + unit(seed, 7) * 32));
		double elev = Math.toRadians(10 + unit(seed, 8) * 22);
		double dist = Features.lyricDistance.get();
		Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(elev), Math.sin(elev), Math.cos(yaw) * Math.cos(elev));
		return new Block(idx, eye.add(dir.scale(dist)), rows);
	}

	// ------------------------------------------------------------------ drawing

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !Lyrics.enabled() || Lyrics.state() != Lyrics.State.READY) {
			BLOCKS.clear();
			lastIndex = -1;
			return;
		}
		List<Lyrics.Line> lines = Lyrics.lines();
		if (lines.isEmpty()) {
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(true);
		long now = System.currentTimeMillis();
		long pos = Lyrics.position() + Features.lyricOffset.i();

		if (Lyrics.trackSince() != lastTrack) {
			lastTrack = Lyrics.trackSince();
			BLOCKS.clear();
			lastIndex = -1;
		}
		int idx = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() <= pos) {
				idx = i;
			} else {
				break;
			}
		}
		if (idx < lastIndex) {
			BLOCKS.clear(); // jumped back in the song
		}
		int max = Math.max(1, Features.lyricBlocks.i());
		if (idx != lastIndex) {
			lastIndex = idx;
			if (idx >= 0 && !lines.get(idx).text().isBlank()) {
				BLOCKS.add(build(lines, idx, mc, pt));
			}
		}
		// the newest "Lines at once" blocks stay, older ones drift up and fade out
		int alive = 0;
		for (int i = BLOCKS.size() - 1; i >= 0; i--) {
			Block b = BLOCKS.get(i);
			if (b.fadeStart >= 0) {
				continue;
			}
			alive++;
			if (alive > max) {
				b.fadeStart = now;
			}
		}
		while (BLOCKS.size() > max + 6) {
			BLOCKS.remove(0);
		}

		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 eye = cam.position();
		double yaw = Math.toRadians(cam.yRot());
		double pitch = Math.toRadians(cam.xRot());
		// camera axes (yaw: 0 = south, turning right; pitch: positive = looking down)
		Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = right.cross(fwd);
		int w = g.guiWidth();
		int h = g.guiHeight();
		double tanV = Math.tan(Math.toRadians(cam.getFov() / 2.0));
		double tanH = tanV * w / (double) h;

		int[] col = Lyrics.colors(Cosmetics.lyrics);
		Font font = MenuFonts.get("nunito");
		float size = Features.lyricSize.f() / 100f;

		Iterator<Block> it = BLOCKS.iterator();
		while (it.hasNext()) {
			Block b = it.next();
			float fade = b.fadeStart < 0 ? 0f : (now - b.fadeStart) / (float) FADE_MS;
			if (fade >= 1f) {
				it.remove();
				continue;
			}
			float alpha = 1f - fade * fade;
			// drift up while fading
			Vec3 anchor = b.anchor.add(0, fade * 0.08 * Features.lyricDistance.get(), 0);
			Vec3 rel = anchor.subtract(eye);
			double dz = rel.dot(fwd);
			if (dz < 1) {
				continue;
			}
			double sx = w / 2.0 + rel.dot(right) / dz / tanH * w / 2.0;
			double sy = h / 2.0 - rel.dot(up) / dz / tanV * h / 2.0;
			if (sx < -w || sx > 2 * w || sy < -h || sy > 2 * h) {
				continue;
			}
			// one text line is 6 % of the distance high: the same size on screen for every distance setting
			double lineWorld = rel.length() * 0.06 * size;
			float k = (float) (lineWorld / dz / tanV * h / 2.0 / 9.0);
			// yaw-only billboard: seen from below / above the text gets flatter
			double elev = Math.atan2(rel.y, Math.sqrt(rel.x * rel.x + rel.z * rel.z));
			float squash = (float) Math.max(0.35, Math.cos(elev));
			if (k < 0.15f) {
				continue;
			}

			g.pose().pushMatrix();
			g.pose().translate((float) sx, (float) sy);
			g.pose().scale(k, k * squash);
			int rowsShown = 0;
			for (List<Word> row : b.rows) {
				if (row.get(0).at() > pos) {
					break;
				}
				rowsShown++;
			}
			float rowH = 12f;
			float top = -rowsShown * rowH / 2f;
			for (int r = 0; r < rowsShown; r++) {
				List<Word> row = b.rows.get(r);
				float total = 0;
				for (Word wd : row) {
					total += font.width(wd.text()) + 4;
				}
				total -= 4;
				float x = -total / 2f;
				float y = top + r * rowH;
				for (Word wd : row) {
					float ww = font.width(wd.text());
					if (wd.at() <= pos) {
						float t = Math.min(1f, (pos - wd.at()) / (float) POP_MS);
						float pop = backOut(t);
						float fresh = Math.min(1f, (pos - wd.at()) / 700f);
						int c = Cosmetics.lerp(0xFF000000 | col[2], 0xFF000000 | col[0], fresh);
						int a = Math.round(255 * alpha * Math.min(1f, t * 1.6f));
						if (a > 4) {
							g.pose().pushMatrix();
							g.pose().translate(x + ww / 2f + wd.dx(), y + 4 + wd.dy());
							g.pose().rotate((float) Math.toRadians(wd.tilt()));
							g.pose().scale(pop, pop);
							FillBatch.flush(g).text(font, wd.text(), Math.round(-ww / 2f), -4,
									(a << 24) | (c & 0xFFFFFF), true);
							g.pose().popMatrix();
						}
					}
					x += ww + 4;
				}
			}
			g.pose().popMatrix();
		}
	}

	/** Ease with a small overshoot ("backOut"). */
	private static float backOut(float t) {
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}
}
