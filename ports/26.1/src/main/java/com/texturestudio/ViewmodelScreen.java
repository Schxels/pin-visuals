package com.texturestudio;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Studio -> Viewmodel: size, position and angle of the item in your hand (first person). */
final class ViewmodelScreen extends ToolScreen {

	ViewmodelScreen(TextureStudioScreen home, Screen back) {
		super("Viewmodel", home, back);
	}

	@Override
	protected boolean liveView() {
		return true;
	}

	@Override
	protected String subtitle() {
		return inWorld() ? "Hold an item - changes show live" : "Join a world to see it live";
	}

	@Override
	protected int preferredHeight() {
		return 290;
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
		toggle(x, y, w, "Custom viewmodel", () -> cfg.vmOn, v -> cfg.vmOn = v);
		y += 20;
		int step = 25;
		intSlider(x, y, w, "Size", "%", 30, 200, () -> cfg.vmScale, v -> cfg.vmScale = v);
		y += step;
		intSlider(x, y, w, "Left / right", "", -100, 100, () -> cfg.vmX, v -> cfg.vmX = v);
		y += step;
		intSlider(x, y, w, "Up / down", "", -100, 100, () -> cfg.vmY, v -> cfg.vmY = v);
		y += step;
		intSlider(x, y, w, "Near / far", "", -100, 100, () -> -cfg.vmZ, v -> cfg.vmZ = -v);
		y += step;
		intSlider(x, y, w, "Tilt", "°", -180, 180, () -> cfg.vmRotX, v -> cfg.vmRotX = v);
		y += step;
		intSlider(x, y, w, "Turn", "°", -180, 180, () -> cfg.vmRotY, v -> cfg.vmRotY = v);
		y += step;
		intSlider(x, y, w, "Roll", "°", -180, 180, () -> cfg.vmRotZ, v -> cfg.vmRotZ = v);
		y += step + 2;
		int half = (w - 6) / 2;
		button(x, y, half, 18, "Reset", false, () -> {
			cfg.vmX = 0;
			cfg.vmY = 0;
			cfg.vmZ = 0;
			cfg.vmRotX = 0;
			cfg.vmRotY = 0;
			cfg.vmRotZ = 0;
			cfg.vmScale = 100;
			changed();
		});
		button(x + half + 6, y, w - half - 6, 18, "Done", true, this::onClose);
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g) {
	}
}
