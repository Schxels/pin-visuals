package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Spotify overlay: a small "now playing" card in a top corner with the album cover, song, artist and a progress bar.
 * The song comes from the same Windows media helper as the lyrics (see {@link Lyrics}).
 * Styles: "glass" (see-through, theme colours), "dark" (Spotify-like dark card), "minimal" (one line + thin bar).
 */
public final class SpotifyHud {

	private static final Identifier COVER = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "spotify/cover");
	private static final int GREEN = 0xFF1DB954;

	private static int loadedVersion = -1;
	private static boolean hasCover;
	private static float shown;
	private static long lastFrame;

	private SpotifyHud() {
	}

	public static boolean enabled() {
		return !Cosmetics.NONE.equals(Cosmetics.spotify);
	}

	/** One line for the menu. */
	public static String statusLine() {
		if (!enabled()) {
			return "Pick a style to turn it on";
		}
		if (!Lyrics.windowsOk()) {
			return "Needs Windows (media controls)";
		}
		if (!Lyrics.hasTrack()) {
			return Lyrics.helperRunning() ? "Play a song in Spotify" : "Starting...";
		}
		return (Lyrics.playing() ? "Now: " : "Paused: ") + Lyrics.title();
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		String style = Cosmetics.spotify;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		boolean want = enabled() && mc.player != null && Lyrics.hasTrack();
		shown = want ? Math.min(1f, shown + dt * 4f) : Math.max(0f, shown - dt * 4f);
		if (shown <= 0.01f || Cosmetics.NONE.equals(style)) {
			return;
		}
		updateCover(mc);
		String title = Lyrics.title();
		String artist = Lyrics.artist().isEmpty() ? "Advertisement" : Lyrics.artist();
		int w = g.guiWidth();
		boolean right = Tweaks.spotifyRight;
		int cw = width(mc.font, style, title, artist);
		int ch = height(style);
		float ease = 1f - (1f - shown) * (1f - shown);
		int slide = Math.round((1f - ease) * 12);
		int x = right ? w - cw - 6 : 6;
		int y = 6 - slide;
		draw(g, mc.font, style, x, y, title, artist, Lyrics.position(), Lyrics.durationMs(), Lyrics.playing(),
				hasCover ? COVER : null, ease);
	}

	/** Texture of the current album cover, or null. */
	static Identifier cover() {
		return hasCover && Lyrics.coverFile() != null ? COVER : null;
	}

	private static void updateCover(Minecraft mc) {
		int v = Lyrics.coverVersion();
		String file = Lyrics.coverFile();
		if (file == null) {
			hasCover = false;
			return;
		}
		if (v == loadedVersion && hasCover) {
			return;
		}
		loadedVersion = v;
		try (InputStream in = Files.newInputStream(Path.of(file))) {
			NativeImage img = NativeImage.read(in);
			mc.getTextureManager().register(COVER, new DynamicTexture(() -> "pin visuals spotify cover", img));
			hasCover = true;
		} catch (Exception e) {
			hasCover = false;
		}
	}

	// ================================================================ drawing (also used by the menu preview)

	static int height(String style) {
		return "minimal".equals(style) ? 16 : 40;
	}

	static int width(Font font, String style, String title, String artist) {
		Font f = MenuFonts.get("nunito");
		if ("minimal".equals(style)) {
			int tw = f.width(title) + f.width("  " + artist);
			return Math.max(110, Math.min(200, tw + 22));
		}
		int tw = Math.max(f.width(title), f.width(artist) + 40);
		return Math.max(140, Math.min(200, tw + 50));
	}

	/** Draws the card with its top-left corner at x, y. a = fade (0..1). cover may be null. */
	static void draw(GuiGraphicsExtractor g, Font vanilla, String style, int x, int y, String title, String artist,
			long pos, long dur, boolean playing, Identifier cover, float a) {
		Font f = MenuFonts.get("nunito");
		int w = width(vanilla, style, title, artist);
		int h = height(style);
		float prog = dur > 0 ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f;
		int c1 = "dark".equals(style) ? GREEN : 0xFF22D3EE;
		int c2 = "dark".equals(style) ? 0xFF1ED760 : 0xFFA855F7;

		// panel
		if ("dark".equals(style)) {
			round(g, x, y, w, h, mul(0xF0121212, a));
			round(g, x + 1, y, w - 2, 1, mul(0x18FFFFFF, a));
		} else {
			round(g, x, y, w, h, mul(0x9010081E, a));
			FillBatch.fill(g, x + 2, y, x + w - 2, y + 1, mul(0x50FFFFFF, a));
			FillBatch.fill(g, x, y + 2, x + 1, y + h - 2, mul(alpha(c1, 0x50), a));
			FillBatch.fill(g, x + w - 1, y + 2, x + w, y + h - 2, mul(alpha(c2, 0x50), a));
			FillBatch.fill(g, x + 2, y + h - 1, x + w - 2, y + h, mul(alpha(c2, 0x40), a));
		}

		if ("minimal".equals(style)) {
			int bx = x + 5;
			bars(g, bx, y + 4, c1, c2, playing, a);
			int tx = bx + 11;
			int room = w - (tx - x) - 5;
			String t = f.plainSubstrByWidth(title, room);
			at(g, f, t, tx, y + 3, mul(0xFFFFFFFF, a));
			int rest = room - f.width(t);
			if (rest > 16) {
				at(g, f, f.plainSubstrByWidth("  " + artist, rest), tx + f.width(t), y + 3, mul(0xFFB4B4C8, a));
			}
			int pw = w - 4;
			FillBatch.fill(g, x + 2, y + h - 3, x + 2 + pw, y + h - 2, mul(0x40FFFFFF, a));
			grad(g, x + 2, y + h - 3, Math.max(1, Math.round(pw * prog)), 1, mul(c1, a), mul(c2, a));
			return;
		}

		// cover
		int cs = 32;
		int cx = x + 4;
		int cy = y + 4;
		if (cover != null) {
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, cover, cx, cy, 0f, 0f, cs, cs, 64, 64, 64, 64,
					mul(0xFFFFFFFF, a));
		} else {
			grad(g, cx, cy, cs, cs, mul(c1, a), mul(c2, a));
			note(g, cx + 12, cy + 11, mul(0xE0FFFFFF, a));
		}
		if (!playing) {
			FillBatch.fill(g, cx, cy, cx + cs, cy + cs, mul(0x90000000, a));
			FillBatch.fill(g, cx + 11, cy + 10, cx + 14, cy + 22, mul(0xFFFFFFFF, a));
			FillBatch.fill(g, cx + 18, cy + 10, cx + 21, cy + 22, mul(0xFFFFFFFF, a));
		}

		int tx = cx + cs + 6;
		int room = x + w - 6 - tx;
		at(g, f, f.plainSubstrByWidth(title, room - 12), tx, y + 5, mul(0xFFFFFFFF, a));
		bars(g, x + w - 13, y + 5, c1, c2, playing, a);
		String time = fmt(pos) + " / " + fmt(dur);
		int timeW = Math.round(f.width(time) * 0.75f);
		at(g, f, f.plainSubstrByWidth(artist, room - timeW - 4), tx, y + 16, mul(0xFFB4B4C8, a));
		if (a > 0.05f) {
			g.pose().pushMatrix();
			g.pose().translate(x + w - 6 - timeW, y + 17);
			g.pose().scale(0.75f, 0.75f);
			FillBatch.flush(g).text(f, time, 0, 0, mul(0xFF9A9AB0, a), false);
			g.pose().popMatrix();
		}

		int py = y + h - 9;
		int pw = room;
		round(g, tx, py, pw, 3, mul(0x50FFFFFF, a));
		int done = Math.max(2, Math.round(pw * prog));
		grad(g, tx, py, done, 3, mul(c1, a), mul(c2, a));
		FillBatch.fill(g, tx + done - 1, py - 1, tx + done + 1, py + 4, mul(0xFFFFFFFF, a));
	}

	/** Three little equalizer bars that bounce while playing. */
	private static void bars(GuiGraphicsExtractor g, int x, int y, int c1, int c2, boolean playing, float a) {
		long ms = System.currentTimeMillis();
		for (int i = 0; i < 3; i++) {
			int bh = playing ? 2 + (int) Math.round((Math.sin(ms / (140.0 + i * 37) + i * 1.7) + 1) * 2.5) : 2;
			int col = i == 1 ? c2 : c1;
			FillBatch.fill(g, x + i * 3, y + 7 - bh, x + i * 3 + 2, y + 7, mul(col, a));
		}
	}

	private static void note(GuiGraphicsExtractor g, int x, int y, int c) {
		FillBatch.fill(g, x + 5, y, x + 6, y + 8, c);
		FillBatch.fill(g, x + 6, y, x + 9, y + 1, c);
		FillBatch.fill(g, x + 8, y + 1, x + 9, y + 3, c);
		FillBatch.fill(g, x + 2, y + 7, x + 6, y + 10, c);
		FillBatch.fill(g, x + 1, y + 8, x + 2, y + 9, c);
	}

	private static String fmt(long ms) {
		long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	private static void at(GuiGraphicsExtractor g, Font f, String s, int x, int y, int color) {
		if ((color >>> 24) < 0x0C) {
			return; // nearly invisible text would be drawn fully opaque
		}
		FillBatch.flush(g).text(f, s, x, y, color, true);
	}

	/** Rectangle with the four corner pixels cut off. */
	private static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		if (h <= 2 || w <= 2) {
			FillBatch.fill(g, x, y, x + w, y + h, c);
			return;
		}
		FillBatch.fill(g, x + 1, y, x + w - 1, y + h, c);
		FillBatch.fill(g, x, y + 1, x + 1, y + h - 1, c);
		FillBatch.fill(g, x + w - 1, y + 1, x + w, y + h - 1, c);
	}

	/** Horizontal gradient from c1 to c2, in 2-pixel steps. */
	private static void grad(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		for (int i = 0; i < w; i += 2) {
			int s = Math.min(2, w - i);
			FillBatch.fill(g, x + i, y, x + i + s, y + h, lerp(c1, c2, w <= 1 ? 0 : i / (float) (w - 1)));
		}
	}

	private static int lerp(int a, int b, float t) {
		int r = 0;
		for (int sh = 0; sh <= 24; sh += 8) {
			int ca = (a >>> sh) & 0xFF;
			int cb = (b >>> sh) & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << sh;
		}
		return r;
	}

	private static int alpha(int c, int a) {
		return (c & 0xFFFFFF) | (a << 24);
	}

	/** Multiplies the alpha of c by a (fade in / out). */
	private static int mul(int c, float a) {
		int al = Math.round(((c >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
		return (c & 0xFFFFFF) | (Math.max(al, 0) << 24);
	}
}
