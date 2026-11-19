package com.texturestudio;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Presets: save the current setup under a name, load or delete saved ones, or pick a ready-made one. */
final class PresetsScreen extends ToolScreen {

	private String name = "";
	private String message = "";
	private long messageAt;
	private int scroll;

	PresetsScreen(TextureStudioScreen home, Screen back) {
		super("Presets", home, back);
	}

	@Override
	protected String subtitle() {
		return "Every feature and setting of All Settings in one click";
	}

	@Override
	protected int preferredWidth() {
		return 380;
	}

	@Override
	protected int preferredHeight() {
		return 310;
	}

	private void say(String s) {
		message = s;
		messageAt = System.currentTimeMillis();
	}

	@Override
	protected void build() {
		int x = wx + 12;
		int w = ww - 24;
		int y = top();
		int bw = 100;
		EditBox box = editBox(x, y, w - bw - 6, "Name, e.g. My PvP", name, s -> name = s);
		box.setMaxLength(32);
		button(x + w - bw, y, bw, 16, "Save current", true, () -> {
			String n = name.trim().isEmpty() ? "My preset" : name.trim();
			try {
				Presets.save(n);
				say("Saved \"" + Presets.fileName(n) + "\"");
				name = "";
			} catch (Exception e) {
				say("Could not save: " + e.getMessage());
			}
			rebuild();
		});
		y += 26;
		int rowH = 22;
		int rows = Math.max(1, (wy + wh - 34 - y) / rowH);
		List<String> saved = Presets.saved();
		int total = Presets.BUILT_IN.length + saved.size();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - rows)));
		for (int i = 0; i < rows && scroll + i < total; i++) {
			int idx = scroll + i;
			int ry = y + i * rowH;
			boolean builtIn = idx < Presets.BUILT_IN.length;
			String label = builtIn ? Presets.BUILT_IN[idx] : saved.get(idx - Presets.BUILT_IN.length);
			final int fi = idx;
			add(x, ry, w, rowH - 3, null, null, (g, hov, p) -> {
				TextureStudioScreen.rrect(g, x, ry, w, rowH - 3, 5, hov ? home.CARD_HI : home.CARD);
				TextureStudioScreen.outline(g, x, ry, w, rowH - 3, 5, home.BORDER);
				home.text(g, label, x + 8, ry + 6, home.TEXT);
				if (builtIn) {
					home.text(g, "ready-made", x + 12 + home.uiFont.width(label), ry + 6, home.FAINT);
				}
			});
			int lx = x + w - 6 - 50;
			button(lx, ry + 2, 50, rowH - 7, "Load", true, () -> {
				try {
					if (builtIn) {
						Presets.loadBuiltIn(fi);
					} else {
						Presets.load(label);
					}
					say("Loaded \"" + label + "\"");
				} catch (Exception e) {
					say("Could not load: " + e.getMessage());
				}
			});
			if (!builtIn) {
				button(lx - 56, ry + 2, 50, rowH - 7, "Delete", false, () -> {
					try {
						Presets.delete(label);
						say("Deleted \"" + label + "\"");
					} catch (Exception e) {
						say("Could not delete: " + e.getMessage());
					}
					rebuild();
				});
			}
		}
	}

	@Override
	protected void onScroll(int dir) {
		scroll += dir;
		rebuild();
	}

	@Override
	protected void drawOver(GuiGraphics g) {
		if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 4000) {
			int mw = home.uiFont.width(message) + 16;
			int mx0 = wx + (ww - mw) / 2;
			int my0 = wy + wh - 26;
			TextureStudioScreen.rrect(g, mx0, my0, mw, 16, 5, TextureStudioScreen.alpha(home.CARD, 0xF0));
			TextureStudioScreen.outline(g, mx0, my0, mw, 16, 5, home.accent());
			home.ctext(g, message, mx0 + mw / 2, my0 + 4, home.TEXT);
		}
	}
}
