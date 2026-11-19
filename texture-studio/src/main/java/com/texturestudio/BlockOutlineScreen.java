package com.texturestudio;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Studio -> Block Outline: colour, thickness and glow of the outline around the block you look at. */
final class BlockOutlineScreen extends ToolScreen {

	BlockOutlineScreen(TextureStudioScreen home, Screen back) {
		super("Block Outline", home, back);
	}

	@Override
	protected boolean liveView() {
		return true;
	}

	@Override
	protected String subtitle() {
		return inWorld() ? "Look at a block to see it live" : "Join a world to see it live";
	}

	@Override
	protected int preferredHeight() {
		return 250;
	}

	@Override
	protected int preferredWidth() {
		return 300;
	}

	@Override
	protected void build() {
		int x = wx + 12;
		int w = ww - 24;
		int y = top();
		toggle(x, y, w, "Custom block outline", () -> cfg.blockOutlineOn, v -> cfg.blockOutlineOn = v);
		y += 22;
		intSlider(x, y, w, "Thickness", "", 1, 10, () -> cfg.blockOutlineWidth, v -> cfg.blockOutlineWidth = v);
		y += 27;
		intSlider(x, y, w, "Glow", "%", 0, 100, () -> cfg.blockOutlineGlow, v -> cfg.blockOutlineGlow = v);
		y += 27;
		toggle(x, y, w, "Rainbow", () -> cfg.blockOutlineRainbow, v -> cfg.blockOutlineRainbow = v);
		y += 22;
		colorPicker(x, y, w, () -> cfg.blockOutlineColor, v -> {
			cfg.blockOutlineColor = v;
			cfg.blockOutlineRainbow = false;
		});
		y += 56;
		if (!inWorld()) {
			return;
		}
		button(x, y, w, 18, "Done", true, this::onClose);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g) {
		if (inWorld()) {
			return;
		}
		// small drawing of a block with the outline
		int s = 54;
		int bx = wx + (ww - s) / 2;
		int by = wy + wh - s - 18;
		FillBatch.fill(g, bx, by, bx + s, by + s, 0xFF7A7A82);
		FillBatch.fill(g, bx, by, bx + s, by + 8, 0xFF5E9E3A);
		int c = Tweaks.blockOutlineOn ? Tweaks.blockOutlineColorNow() : 0x66000000;
		int t = Tweaks.blockOutlineOn ? Math.max(1, Tweaks.blockOutlineWidth / 3) : 1;
		if (Tweaks.blockOutlineOn && Tweaks.blockOutlineGlow > 0) {
			home.softGlow(g, bx - 14, by - 14, s + 28, s + 28, c, Tweaks.blockOutlineGlow * 2);
		}
		TextureStudioScreen.outline(g, bx, by, s, s, 0, c);
		for (int i = 1; i < t; i++) {
			TextureStudioScreen.outline(g, bx - i, by - i, s + 2 * i, s + 2 * i, 0, c);
		}
	}
}
