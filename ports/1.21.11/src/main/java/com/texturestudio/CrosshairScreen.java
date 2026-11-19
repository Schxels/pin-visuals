package com.texturestudio;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Studio -> Crosshair: shape, colour, gap, length, thickness, centre dot and outline, with a big live preview. */
final class CrosshairScreen extends ToolScreen {

	private static final String[] SHAPES = { "Cross", "Dot", "Circle", "T", "X", "Box" };

	CrosshairScreen(TextureStudioScreen home, Screen back) {
		super("Crosshair", home, back);
	}

	@Override
	protected String subtitle() {
		return "Shape, colour and size - the preview is drawn bigger";
	}

	@Override
	protected int preferredWidth() {
		return 440;
	}

	@Override
	protected int preferredHeight() {
		return 300;
	}

	private int colX() {
		return wx + ww - 12 - colW();
	}

	private int colW() {
		return Math.min(220, ww - 150);
	}

	@Override
	protected void build() {
		int x = colX();
		int w = colW();
		int y = top();
		toggle(x, y, w, "Custom crosshair", () -> cfg.crossOn, v -> cfg.crossOn = v);
		y += 20;
		segment(x, y, w, 16, SHAPES, () -> cfg.crossShape, v -> cfg.crossShape = v);
		y += 22;
		intSlider(x, y, w, "Gap", " px", 0, 10, () -> cfg.crossGap, v -> cfg.crossGap = v);
		y += 25;
		intSlider(x, y, w, "Length", " px", 1, 12, () -> cfg.crossLen, v -> cfg.crossLen = v);
		y += 25;
		intSlider(x, y, w, "Thickness", " px", 1, 4, () -> cfg.crossThick, v -> cfg.crossThick = v);
		y += 25;
		int half = (w - 10) / 2;
		toggle(x, y, half, "Centre dot", () -> cfg.crossDot, v -> cfg.crossDot = v);
		toggle(x + half + 10, y, w - half - 10, "Outline", () -> cfg.crossOutline, v -> cfg.crossOutline = v);
		y += 20;
		colorPicker(x, y, w, () -> cfg.crossColor, v -> cfg.crossColor = v);
	}

	@Override
	protected void drawContent(GuiGraphics g) {
		int px = wx + 12;
		int py = top();
		int pw = colX() - 12 - px;
		int ph = wy + wh - 12 - py;
		card(g, px, py, pw, ph);
		// a little sky and ground so the colours can be judged on light and dark
		TextureStudioScreen.vgrad(g, px + 4, py + 4, pw - 8, (ph - 8) / 2, 4, 0, 0xFF7EB6FF, 0xFFB9D9FF);
		TextureStudioScreen.vgrad(g, px + 4, py + 4 + (ph - 8) / 2, pw - 8, ph - 8 - (ph - 8) / 2, 0, 4, 0xFF5E8C3A,
				0xFF3B5A24);
		int k = Math.max(2, Math.min(5, Math.min(pw, ph) / 40));
		Crosshair.paint(g, px + pw / 2, py + ph / 2, k, cfg.crossShape, cfg.crossColor, cfg.crossGap, cfg.crossLen,
				cfg.crossThick, cfg.crossDot, cfg.crossOutline);
		home.text(g, k + "x", px + 8, py + ph - 14, 0xFFFFFFFF);
		if (!cfg.crossOn) {
			home.ctext(g, "Off - vanilla crosshair in game", px + pw / 2, py + 8, 0xFF0F172A);
		}
	}
}
