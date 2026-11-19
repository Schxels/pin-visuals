package com.texturestudio;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Custom crosshair (Studio -> Crosshair): replaces the vanilla crosshair HUD element when switched on. */
public final class Crosshair {

	private Crosshair() {
	}

	/** Wraps the vanilla crosshair HUD element: ours when switched on, else the vanilla one. */
	public static net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement wrap(
			net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement vanilla) {
		return (g, delta) -> {
			if (!draw(g, delta)) {
				vanilla.extractRenderState(g, delta);
			}
		};
	}

	/** Draws the custom crosshair in the middle of the screen if it is on, else lets vanilla draw. */
	public static boolean draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!Tweaks.crossOn) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || (!mc.options.getCameraType().isFirstPerson() && !Features.crossThird.b())) {
			return true;
		}
		int rgb = Features.crossRainbow.b() ? Features.rainbow(1, 2400L) : Tweaks.crossColor & 0xFFFFFF;
		int a = Math.max(8, Math.round(255 * Features.crossOpacity.f() / 100f));
		paint(g, g.guiWidth() / 2, g.guiHeight() / 2, Math.max(1, Features.crossScale.i()), Tweaks.crossShape,
				(a << 24) | rgb, Tweaks.crossGap, Tweaks.crossLen, Tweaks.crossThick, Tweaks.crossDot,
				Tweaks.crossOutline);
		return true;
	}

	/**
	 * Paints a crosshair centred on (cx, cy); k = size factor (the designer preview draws it bigger). Thickness t
	 * is centred: odd thickness sits exactly on the centre pixel.
	 */
	public static void paint(GuiGraphicsExtractor g, int cx, int cy, int k, int shape, int color, int gap, int len,
			int thick, boolean dot, boolean outline) {
		int c = (color >>> 24) == 0 ? 0xFF000000 | color : color;
		int t = thick * k;
		int gp = gap * k;
		int ln = len * k;
		int o = outline ? Math.max(1, k) : 0;
		int lo = -(t / 2);
		int hi = lo + t;
		switch (shape) {
			case 1 -> box(g, cx + lo - k, cy + lo - k, cx + hi + k, cy + hi + k, c, o);
			case 2 -> ring(g, cx, cy, gp + ln / 2 + t, t, c, o);
			case 3 -> {
				box(g, cx + lo, cy + gp + 1, cx + hi, cy + gp + 1 + ln, c, o); // down
				box(g, cx - gp - ln, cy + lo, cx - gp, cy + hi, c, o); // left
				box(g, cx + gp + 1, cy + lo, cx + gp + 1 + ln, cy + hi, c, o); // right
			}
			case 4 -> {
				for (int i = 0; i < ln; i += Math.max(1, k)) {
					int d = gp + i + 1;
					int s = Math.max(1, k);
					box(g, cx + d, cy + d, cx + d + s, cy + d + s, c, o);
					box(g, cx - d - s, cy + d, cx - d, cy + d + s, c, o);
					box(g, cx + d, cy - d - s, cx + d + s, cy - d, c, o);
					box(g, cx - d - s, cy - d - s, cx - d, cy - d, c, o);
				}
			}
			case 5 -> {
				int r = gp + ln / 2 + t;
				box(g, cx - r, cy - r, cx + r, cy - r + t, c, o);
				box(g, cx - r, cy + r - t, cx + r, cy + r, c, o);
				box(g, cx - r, cy - r + t, cx - r + t, cy + r - t, c, o);
				box(g, cx + r - t, cy - r + t, cx + r, cy + r - t, c, o);
			}
			default -> {
				box(g, cx + lo, cy - gp - ln, cx + hi, cy - gp, c, o); // up
				box(g, cx + lo, cy + gp + 1, cx + hi, cy + gp + 1 + ln, c, o); // down
				box(g, cx - gp - ln, cy + lo, cx - gp, cy + hi, c, o); // left
				box(g, cx + gp + 1, cy + lo, cx + gp + 1 + ln, cy + hi, c, o); // right
			}
		}
		if (dot && shape != 1) {
			int ds = Math.max(1, k);
			box(g, cx + lo - (ds - k > 0 ? 0 : 0), cy + lo, cx + hi, cy + hi, c, o);
		}
	}

	private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int t, int c, int o) {
		// rows of a circle outline, filled per row
		for (int pass = o > 0 ? 0 : 1; pass < 2; pass++) {
			int col = pass == 0 ? ((c >>> 24) * 0xC0 / 255) << 24 : c;
			int rr = r + (pass == 0 ? o : 0);
			int in = Math.max(0, r - t - (pass == 0 ? o : 0));
			for (int y = -rr; y <= rr; y++) {
				int outer = (int) Math.floor(Math.sqrt(Math.max(0, rr * rr + rr - y * y)));
				int inner = Math.abs(y) < in ? (int) Math.ceil(Math.sqrt(Math.max(0, in * in - in - y * y))) : -1;
				if (inner < 0) {
					FillBatch.fill(g, cx - outer, cy + y, cx + outer + 1, cy + y + 1, col);
				} else {
					FillBatch.fill(g, cx - outer, cy + y, cx - inner + 1, cy + y + 1, col);
					FillBatch.fill(g, cx + inner, cy + y, cx + outer + 1, cy + y + 1, col);
				}
			}
		}
	}

	private static void box(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c, int o) {
		if (o > 0) {
			FillBatch.fill(g, x0 - o, y0 - o, x1 + o, y1 + o, ((c >>> 24) * 0xC0 / 255) << 24);
		}
		FillBatch.fill(g, x0, y0, x1, y1, c);
	}
}
