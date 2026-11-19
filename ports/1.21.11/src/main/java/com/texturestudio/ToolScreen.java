package com.texturestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base of the Studio tools (crosshair, block outline, viewmodel, pixel editor, pack browser): a window in the Pin
 * Visuals style with simple controls. Everything is drawn by hand; clicks and drags are read from the mouse state
 * every frame (works the same in every Minecraft version). Opening slides and fades in.
 */
abstract class ToolScreen extends Screen {

	/** What a control does when it is pressed and dragged (mouse x, y). */
	interface Drag {
		void at(int x, int y);
	}

	interface Paint {
		void paint(GuiGraphics g, boolean hover, boolean pressed);
	}

	record Ctl(int x, int y, int w, int h, Runnable click, Drag drag, Paint paint) {
		boolean inside(int px, int py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	static final int[] PRESETS = { 0xFFFFFFFF, 0xFF0B0B10, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF4ADE80, 0xFF22D3EE,
			0xFF3B82F6, 0xFFA855F7, 0xFFF472B6 };

	protected final TextureStudioScreen home;
	protected final Screen back;
	protected final MixConfig cfg;
	protected int wx;
	protected int wy;
	protected int ww;
	protected int wh;
	protected int mx;
	protected int my;
	private final List<Ctl> ctls = new ArrayList<>();
	private final List<EditBox> boxes = new ArrayList<>();
	private Ctl active;
	private boolean leftDown;
	private boolean dirty;
	private final long opened = System.nanoTime();

	protected ToolScreen(String title, TextureStudioScreen home, Screen back) {
		super(Component.literal(title));
		this.home = home;
		this.back = back;
		this.cfg = home.config;
	}

	// ------------------------------------------------------------------ for the tools

	/** Adds the controls (window position is already set). */
	protected abstract void build();

	/** Draws the preview / content area (after the window frame and before the controls). */
	protected void drawContent(GuiGraphics g) {
	}

	/** Small line under the title. */
	protected String subtitle() {
		return "";
	}

	/** true = the game stays visible next to a narrow window (live preview of HUD / hand). */
	protected boolean liveView() {
		return false;
	}

	/** true = no window frame and no dimming (the screen paints everything itself). */
	protected boolean plain() {
		return false;
	}

	protected boolean inWorld() {
		return minecraft != null && minecraft.level != null && minecraft.player != null;
	}

	protected void onScroll(int dir) {
	}

	/** Settings changed: apply now, save the file when the mouse is released. */
	protected void changed() {
		Tweaks.load(cfg);
		dirty = true;
	}

	protected void rebuild() {
		for (EditBox b : boxes) {
			removeWidget(b);
		}
		boxes.clear();
		ctls.clear();
		active = null;
		build();
		if (!plain()) {
			closeButton();
		}
	}

	protected Ctl add(int x, int y, int w, int h, Runnable click, Drag drag, Paint paint) {
		Ctl c = new Ctl(x, y, w, h, click, drag, paint);
		ctls.add(c);
		return c;
	}

	protected EditBox editBox(int x, int y, int w, String hint, String value, Consumer<String> changed) {
		EditBox b = new EditBox(home.uiFont, x + 5, y + 4, w - 10, 12, Component.literal(hint));
		b.setBordered(false);
		b.setTextShadow(false);
		b.setHint(Component.literal(hint).withColor(home.FAINT));
		b.setTextColor(home.TEXT);
		b.setMaxLength(64);
		b.setValue(value);
		b.setResponder(changed::accept);
		add(x, y, w, 16, () -> setFocused(b), null,
				(g, h, p) -> {
					TextureStudioScreen.rrect(g, x, y, w, 16, 5, home.FIELD);
					TextureStudioScreen.outline(g, x, y, w, 16, 5, b.isFocused() ? home.accent() : home.BORDER);
				});
		addWidget(b);
		boxes.add(b);
		return b;
	}

	protected void button(int x, int y, int w, int h, String label, boolean primary, Runnable run) {
		add(x, y, w, h, run, null, (g, hov, p) -> {
			int acc = home.accent();
			if (primary) {
				if (hov) {
					home.softGlow(g, x - 6, y - 6, w + 12, h + 12, acc, 0x70);
				}
				TextureStudioScreen.hgrad(g, x, y, w, h, 5, acc, home.acc3());
				TextureStudioScreen.outline(g, x, y, w, h, 5, TextureStudioScreen.alpha(0xFFFFFF, hov ? 0x90 : 0x50));
				home.ctext(g, label, x + w / 2, y + (h - 8) / 2, home.onAccent());
			} else {
				TextureStudioScreen.rrect(g, x, y, w, h, 5, hov ? home.CARD_HI : home.CARD);
				TextureStudioScreen.outline(g, x, y, w, h, 5, hov ? acc : home.BORDER);
				home.ctext(g, label, x + w / 2, y + (h - 8) / 2, hov ? home.TEXT : home.SOFT);
			}
		});
	}

	/** On/off switch with a label on the left. */
	protected void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
		add(x, y, w, 14, () -> {
			set.accept(!get.getAsBoolean());
			changed();
		}, null, (g, hov, p) -> {
			home.text(g, label, x, y + 3, hov ? home.TEXT : home.SOFT);
			boolean on = get.getAsBoolean();
			int sw = 22;
			int sx = x + w - sw;
			TextureStudioScreen.rrect(g, sx, y + 1, sw, 11, 5, on ? home.accent() : home.TRACK);
			TextureStudioScreen.rrect(g, on ? sx + sw - 10 : sx + 1, y + 2, 9, 9, 4, 0xFFFFFFFF);
		});
	}

	/** Slider: label left, value right, track below (height 22). */
	protected void slider(int x, int y, int w, String label, Supplier<String> value, DoubleSupplier frac,
			DoubleConsumer set) {
		Drag d = (px, py) -> {
			set.accept(Math.max(0, Math.min(1, (px - x) / (double) Math.max(1, w))));
			changed();
		};
		add(x, y, w, 22, null, d, (g, hov, p) -> {
			home.text(g, label, x, y, hov || p ? home.TEXT : home.SOFT);
			home.rtext(g, value.get(), x + w, y, home.DIM);
			int ty = y + 14;
			double f = Math.max(0, Math.min(1, frac.getAsDouble()));
			TextureStudioScreen.rrect(g, x, ty, w, 4, 2, home.TRACK);
			int fw = (int) Math.round(w * f);
			if (fw > 0) {
				TextureStudioScreen.hgrad(g, x, ty, fw, 4, 2, home.accent(), home.acc3());
			}
			int kx = x + fw - 4;
			if (hov || p) {
				home.softGlow(g, kx - 5, ty - 7, 18, 18, home.accent(), 0x80);
			}
			TextureStudioScreen.rrect(g, kx, ty - 2, 8, 8, 4, 0xFFFFFFFF);
		});
	}

	/** Integer slider from lo to hi. */
	protected void intSlider(int x, int y, int w, String label, String unit, int lo, int hi, IntSupplier get,
			IntConsumer set) {
		slider(x, y, w, label, () -> get.getAsInt() + unit, () -> (get.getAsInt() - lo) / (double) (hi - lo),
				f -> set.accept((int) Math.round(lo + f * (hi - lo))));
	}

	/** Row of buttons where one is selected. */
	protected void segment(int x, int y, int w, int h, String[] labels, IntSupplier get, IntConsumer set) {
		int n = labels.length;
		int gap = 3;
		int bw = (w - gap * (n - 1)) / n;
		for (int i = 0; i < n; i++) {
			final int idx = i;
			int bx = x + i * (bw + gap);
			add(bx, y, bw, h, () -> {
				set.accept(idx);
				changed();
			}, null, (g, hov, p) -> {
				boolean on = get.getAsInt() == idx;
				if (on) {
					TextureStudioScreen.hgrad(g, bx, y, bw, h, 4, home.accent(), home.acc3());
				} else {
					TextureStudioScreen.rrect(g, bx, y, bw, h, 4, hov ? home.CARD_HI : home.CARD);
					TextureStudioScreen.outline(g, bx, y, bw, h, 4, hov ? home.accent() : home.BORDER);
				}
				home.ctext(g, home.uiFont.width(labels[idx]) > bw - 4 ? labels[idx].substring(0, 1) : labels[idx],
						bx + bw / 2, y + (h - 8) / 2, on ? home.onAccent() : home.SOFT);
			});
		}
	}

	/** Colour presets plus a hue and a brightness slider (height 50). */
	protected void colorPicker(int x, int y, int w, IntSupplier get, IntConsumer set) {
		int n = PRESETS.length;
		int s = Math.min(16, (w - (n - 1) * 3) / n);
		for (int i = 0; i < n; i++) {
			int c = PRESETS[i];
			int sx = x + i * (s + 3);
			add(sx, y, s, s, () -> {
				set.accept(c);
				changed();
			}, null, (g, hov, p) -> {
				boolean on = (get.getAsInt() | 0xFF000000) == c;
				if (on || hov) {
					TextureStudioScreen.rrect(g, sx - 2, y - 2, s + 4, s + 4, 5, on ? 0xFFFFFFFF : home.accent());
				}
				TextureStudioScreen.rrect(g, sx, y, s, s, 4, c);
				TextureStudioScreen.outline(g, sx, y, s, s, 4, TextureStudioScreen.alpha(0x000000, 0x50));
			});
		}
		int hy = y + s + 6;
		// hue track
		Drag hue = (px, py) -> {
			float h = Math.max(0, Math.min(1, (px - x) / (float) w));
			set.accept(TextureStudioScreen.hsv(h, 0.85f, 1f));
			changed();
		};
		add(x, hy, w, 10, null, hue, (g, hov, p) -> {
			for (int i = 0; i < w; i += 2) {
				FillBatch.fill(g, x + i, hy + 2, x + Math.min(w, i + 2), hy + 8,
						TextureStudioScreen.hsv(i / (float) w, 0.85f, 1f));
			}
			TextureStudioScreen.outline(g, x - 1, hy + 1, w + 2, 8, 2, home.BORDER);
			int k = x + Math.round(TextureStudioScreen.hueOf(get.getAsInt()) * w);
			TextureStudioScreen.rrect(g, k - 2, hy, 4, 10, 1, 0xFFFFFFFF);
		});
		int ly = hy + 14;
		Drag light = (px, py) -> {
			float f = Math.max(0, Math.min(1, (px - x) / (float) w));
			int base = TextureStudioScreen.hsv(TextureStudioScreen.hueOf(get.getAsInt()), 0.85f, 1f);
			int c = f < 0.5f ? TextureStudioScreen.mix(0xFF000000, base, f * 2f)
					: TextureStudioScreen.mix(base, 0xFFFFFFFF, (f - 0.5f) * 2f);
			set.accept(c);
			changed();
		};
		add(x, ly, w, 10, null, light, (g, hov, p) -> {
			int base = TextureStudioScreen.hsv(TextureStudioScreen.hueOf(get.getAsInt()), 0.85f, 1f);
			TextureStudioScreen.hgrad(g, x, ly + 2, w / 2, 6, 0, 0xFF000000, base);
			TextureStudioScreen.hgrad(g, x + w / 2, ly + 2, w - w / 2, 6, 0, base, 0xFFFFFFFF);
			TextureStudioScreen.outline(g, x - 1, ly + 1, w + 2, 8, 2, home.BORDER);
		});
	}

	protected void label(GuiGraphics g, String s, int x, int y) {
		home.text(g, s, x, y, home.DIM);
	}

	protected void card(GuiGraphics g, int x, int y, int w, int h) {
		TextureStudioScreen.vgrad(g, x, y, w, h, 6, 6, TextureStudioScreen.mix(home.CARD, home.accent(), 0.10f),
				home.CARD);
		TextureStudioScreen.outline(g, x, y, w, h, 6, home.BORDER);
	}

	// ------------------------------------------------------------------ screen

	@Override
	protected void init() {
		home.prepareStyle();
		if (plain()) {
			wx = 0;
			wy = 0;
			ww = width;
			wh = height;
		} else if (liveView() && inWorld()) {
			ww = Math.min(260, width / 2 - 10);
			wh = Math.min(height - 20, preferredHeight());
			wx = 10;
			wy = 10;
		} else {
			ww = Math.min(width - 24, preferredWidth());
			wh = Math.min(height - 24, preferredHeight());
			wx = (width - ww) / 2;
			wy = (height - wh) / 2;
		}
		rebuild();
	}

	private void closeButton() {
		int cs = 16;
		int cx = wx + ww - cs - 8;
		int cy = wy + 8;
		add(cx, cy, cs, cs, this::onClose, null, (g, hov, p) -> {
			TextureStudioScreen.rrect(g, cx, cy, cs, cs, 5, hov ? TextureStudioScreen.mix(home.CARD, 0xFFEF4444, 0.4f)
					: home.CARD);
			TextureStudioScreen.outline(g, cx, cy, cs, cs, 5, hov ? 0xFFEF4444 : home.BORDER);
			int c = hov ? 0xFFFFFFFF : home.SOFT;
			for (int i = 0; i < 6; i++) {
				FillBatch.fill(g, cx + 5 + i, cy + 5 + i, cx + 6 + i, cy + 6 + i, c);
				FillBatch.fill(g, cx + 10 - i, cy + 5 + i, cx + 11 - i, cy + 6 + i, c);
			}
		});
	}


	protected int preferredWidth() {
		return 420;
	}

	protected int preferredHeight() {
		return 280;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// no super call: no blur, so the game stays visible behind live tools
		mx = mouseX;
		my = mouseY;
		if (leftDown && active != null && active.drag() != null) {
			active.drag().at(mx, my);
		}

		float t = Math.min(1f, (System.nanoTime() - opened) / 260_000_000f);
		float e = plain() ? 1f : 1f - (1f - t) * (1f - t) * (1f - t);
		boolean live = liveView() && inWorld();
		FillBatch.begin();
		try {
			if (!live && !plain()) {
				int dim = inWorld() ? 0xA0 : 0xFF;
				FillBatch.fill(g, 0, 0, width, height, TextureStudioScreen.alpha(home.lightMenu() ? 0xE8ECF4 : 0x08070E,
						Math.round(dim * e)));
			}
			boolean moved = e < 0.999f;
			if (moved) {
				g.pose().pushMatrix();
				g.pose().translate(0, Math.round((1f - e) * 14));
			}
			if (!plain()) {
				frame(g);
			}
			drawContent(g);
			List<Ctl> list = new ArrayList<>(ctls);
			for (Ctl c : list) {
				c.paint().paint(g, c.inside(mx, my) && (active == null || active == c), active == c);
			}
			drawOver(g);
			FillBatch.flush(g);
			if (moved) {
				g.pose().popMatrix();
			}
		} finally {
			FillBatch.end(g);
		}
		for (EditBox b : boxes) {
			b.render(g, mouseX, mouseY, delta);
		}
		if (e < 0.999f) {
			// fade in: the window starts covered and clears up
			FillBatch.flush(g).fill(wx - 2, wy - 2, wx + ww + 2, wy + wh + 2,
					TextureStudioScreen.alpha(home.WIN, Math.round(255 * (1f - e))));
		}
	}

	/** No blur behind live tools (the game must stay sharp) or full-screen ones. */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (plain() || (liveView() && inWorld())) {
			return;
		}
		super.renderBackground(g, mouseX, mouseY, delta);
	}

	/** Drawn after the controls (tooltips, overlays). */
	protected void drawOver(GuiGraphics g) {
	}

	private void frame(GuiGraphics g) {
		int acc = home.accent();
		TextureStudioScreen.shadow(g, wx, wy, ww, wh, 8, 8, 0x18);
		TextureStudioScreen.glow(g, wx, wy, ww, wh, 8, 4, acc, 0x12);
		TextureStudioScreen.vgrad(g, wx, wy, ww, wh, 8, 8,
				TextureStudioScreen.alpha(TextureStudioScreen.mix(home.WIN, acc, 0.12f), 0xF4),
				TextureStudioScreen.alpha(home.WIN, 0xF4));
		TextureStudioScreen.outline(g, wx, wy, ww, wh, 8, TextureStudioScreen.mix(acc, 0xFFFFFFFF, 0.2f));
		TextureStudioScreen.hgrad(g, wx + 8, wy, ww / 2 - 8, 1, 0, TextureStudioScreen.alpha(acc, 0),
				TextureStudioScreen.mix(acc, 0xFFFFFFFF, 0.5f));
		TextureStudioScreen.hgrad(g, wx + ww / 2, wy, ww / 2 - 8, 1, 0, TextureStudioScreen.mix(acc, 0xFFFFFFFF, 0.5f),
				TextureStudioScreen.alpha(acc, 0));
		home.titleText(g, getTitle().getString().toUpperCase(java.util.Locale.ROOT), wx + 12, wy + 10, 1.25f,
				home.TEXT);
		String sub = subtitle();
		if (!sub.isEmpty()) {
			home.text(g, home.uiFont.plainSubstrByWidth(sub, ww - 48), wx + 12, wy + 25, home.DIM);
		}
		FillBatch.fill(g, wx + 10, wy + 37, wx + ww - 10, wy + 38, home.LINE);
	}

	/** Content area below the title line. */
	protected int top() {
		return wy + 46;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		leftDown = true;
		int px = (int) event.x();
		int py = (int) event.y();
		for (int i = ctls.size() - 1; i >= 0; i--) {
			Ctl c = ctls.get(i);
			if (c.inside(px, py)) {
				active = c;
				if (c.click() != null) {
					c.click().run();
				}
				if (active != null && active.drag() != null) {
					active.drag().at(px, py);
				}
				super.mouseClicked(event, doubleClick); // edit boxes get their focus / cursor
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			leftDown = false;
			active = null;
			if (dirty) {
				cfg.save();
				dirty = false;
			}
		}
		return super.mouseReleased(event);
	}

	/** Mouse wheel (no @Override: the signature differs between versions). */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		onScroll(scrollY > 0 ? -1 : 1);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (dirty) {
			cfg.save();
			dirty = false;
		}
		if (back != null) {
			Nav.open(back);
		}
	}
}
