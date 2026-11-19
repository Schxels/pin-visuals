package com.texturestudio;

import com.texturestudio.PinSettings.Cat;
import com.texturestudio.PinSettings.Feature;
import com.texturestudio.PinSettings.S;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Pin Visuals menu: white window, categories on the left, feature cards with a switch in the middle, settings
 * that fold out of a card, a search field on top.
 * <p>
 * Everything is drawn by hand every frame ("immediate mode"): while drawing, each control also records its click
 * zone, so layout, scrolling and animations never get out of step. All rectangles go through {@link FillBatch}
 * (one GUI element per run of fills), which also clips them to the scrolling list and scales / fades them while
 * the window opens and closes.
 * <p>
 * Reusable controls (all animated): {@link #toggle}, {@link #sliderRow}, {@link #colorRow} (colour field, hue bar,
 * alpha bar, hex input, presets), {@link #choiceRow} (dropdown), {@link #keyRow} (key binding), {@link #textRow},
 * plus a reset button per setting and per feature.
 */
final class PinMenuScreen extends Screen {

	// ---- palette: taken from the colour theme of the main menu (Themes), so both menus always match
	private static final int WHITE = 0xFFFFFFFF;
	private int WIN = 0xFF1A1230;
	private int CARD = 0xFF241A42;
	private int SIDE = 0xFF1E1638;
	private int FIELD = 0xFF1A1330;
	private int LINE = 0xFF2E2354;
	private int BORDER = 0xFF3B2D6B;
	private int BORDER_HI = 0xFF5B4A99;
	private int TRACK = 0xFF2E2354;
	private int TEXT = 0xFFF3F0FF;
	private int SOFT = 0xFFD0CCE0;
	private int DIM = 0xFF9C98B0;
	private int FAINT = 0xFF6A6680;
	private int HOVER = 0xFFFFFFFF;
	private int DIMMER = 0x99000000;
	private boolean light;
	/** Second colour of the theme gradient (accent -> acc2). */
	private int acc2 = 0xFFD946EF;
	/** The main menu whose theme (colours, light / dark) this menu shows. */
	private TextureStudioScreen theme;

	// ---- one text size per level
	private static final float T_TITLE = 1.35f;
	private static final float T_CAT = 1.15f;
	private static final float T_FEATURE = 1.0f;
	private static final float T_SETTING = 0.9f;

	private static final int HEAD = 36; // height of a folded feature card
	private static final int GAP = 8;

	// ---- 7x7 pixel icons
	private static final String[] I_RENDER = { "..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.." };
	private static final String[] I_PLAYER = { "..###..", "..###..", "..###..", ".#####.", "#.###.#", "..#.#..", "..#.#.." };
	private static final String[] I_PACKS = { "###.###", "###.###", "###.###", ".......", "###.###", "###.###", "###.###" };
	private static final String[] I_HUD = { "##...##", "#.....#", "...#...", "..###..", "...#...", "#.....#", "##...##" };
	private static final String[] I_MISC = { ".#.....", "###.###", ".#.....", ".......", ".....#.", "###.###", ".....#." };
	private static final String[] I_SEARCH = { ".###...", "#...#..", "#...#..", "#...#..", ".####..", "....##.", ".....##" };
	private static final String[] I_CLOSE = { "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#" };
	private static final String[] I_RESET = { "..###.#", ".#...##", "#...###", "#......", "#.....#", ".#...#.", "..###.." };
	private static final String[] I_DOWN = { ".......", ".......", "#.....#", ".#...#.", "..#.#..", "...#...", "......." };
	private static final String[] I_UP = { ".......", "...#...", "..#.#..", ".#...#.", "#.....#", ".......", "......." };

	private static final String[] I_PIN = { "#####..", "#....#.", "#....#.", "#####..", "#......", "#......", "#......" };
	private static final String[] I_STAR = { "...#...", "..###..", "#######", ".#####.", "..###..", ".##.##.", "##...##" };
	private static final String[] I_RIGHT = { "..#....", "...#...", "....#..", ".....#.", "....#..", "...#...", "..#...." };
	private static final String[] I_LEFT = { "....#..", "...#...", "..#....", ".#.....", "..#....", "...#...", "....#.." };
	private static final String[] I_EYE = { ".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......." };
	private static final String[] I_BOX = { "#######", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", "#######" };
	private static final String[] I_FIRE = { "...#...", "..##...", "..###..", ".####.#", ".#####.", "#######", ".#####." };
	private static final String[] I_BATTERY = { ".......", "######.", "#....##", "#.##.##", "#....##", "######.", "......." };
	private static final String[] I_SPARK = { "#..#..#", ".#.#.#.", "..###..", "###.###", "..###..", ".#.#.#.", "#..#..#" };
	private static final String[] I_KNIFE = { "......#", ".....##", "....##.", "...##..", "..##...", ".#.....", "#......" };
	private static final String[] I_RING = { "..###..", ".#...#.", "#.....#", "#.....#", "#.....#", ".#...#.", "..###.." };
	private static final String[] I_MUSIC = { "..####.", "..#..#.", "..#..#.", "..#..#.", "###.###", "###.###", "......." };
	private static final String[] I_SHIELD = { "#######", "#.....#", "#.....#", "#.....#", ".#...#.", "..#.#..", "...#..." };
	private static final String[] I_PICK = { "#####..", "....##.", "...#.#.", "..#..#.", ".#.....", "#......", "......." };
	private static final String[] I_CAPE = { ".#####.", "#######", "#######", "#######", "#######", "##.#.##", "#.....#" };
	private static final String[] I_DROP = { ".......", ".......", ".......", "..###..", ".#####.", "#######", "......." };
	private static final String[] I_WAVE = { ".......", "##.....", "..##.##", "....#..", "##.....", "..##.##", "....#.." };

	private static String[] icon(Cat c) {
		return switch (c) {
			case RENDER -> I_RENDER;
			case PLAYER -> I_PLAYER;
			case TEXTURES -> I_PACKS;
			case HUD -> I_HUD;
			case MISC -> I_MISC;
		};
	}

	/** What a pressed control does while the mouse is dragged. */
	private interface Drag {
		void at(int x, int y);
	}

	private record Zone(int x, int y, int w, int h, String key, Runnable click, Drag drag, Runnable right) {
		boolean inside(int px, int py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private final Screen parent;
	private Font font;
	private int acc = Features.DEFAULT_ACCENT;

	// window
	private int wx;
	private int wy;
	private int ww;
	private int wh;
	private int sideW;
	private boolean sideLabels;
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	// state
	private Cat cat = Cat.RENDER;
	private final Set<String> expanded = new HashSet<>();
	private EditBox search;
	private float scroll;
	private float scrollTarget;
	private int contentH;
	private float openT;
	private boolean closing;
	private boolean animating;
	private float winScale = 1f;
	private float winAlpha = 1f;

	// per frame
	private int mx;
	private int my;
	private float dt = 0.016f;
	private long lastNanos = System.nanoTime();
	private boolean leftDown;
	private final List<Zone> zones = new ArrayList<>();
	private final List<Zone> popupZones = new ArrayList<>();
	private boolean inPopup;
	private Zone active;
	private String activeKey = "";
	private final Map<String, Float> anims = new HashMap<>();
	private final List<int[]> clips = new ArrayList<>();

	// grid + side panel
	private static final int TILE = 38;
	private static final int OK = 0xFF34D399;
	private static final Set<String> NEW = Set.of("dura_guard", "item_physics");
	private boolean narrow;
	private int detX;
	private int detW;
	private int dTop;
	private int dH;
	private Feature sel;
	private boolean showDetail;
	private boolean favView;
	private int filter;
	private float dScroll;
	private float dTarget;
	private int dContentH;
	private Map<Feature, List<S>> lastMatch = new HashMap<>();
	private int[] editClipArea;

	// dropdown
	private S popup;
	private int popX;
	private int popY;
	private int popW;
	private boolean popupSeen;

	// colour picker
	private S picker;
	private float pickH;
	private float pickS;
	private float pickV;
	private int pickLast;
	private boolean pickInit;

	// key capture
	private S capture;
	private boolean captureArmed;

	// typing (number, hex, text)
	private EditBox edit;
	private S editS;
	private int editX;
	private int editY;
	private int editW = 60;
	private int resetX;
	private boolean editSeen;

	PinMenuScreen(Screen parent) {
		super(Component.literal("Pin Visuals"));
		this.parent = parent;
	}

	// ================================================================== lifecycle

	@Override
	protected void init() {
		Features.reload();
		font = MenuFonts.get("nunito");
		for (Feature f : PinSettings.FEATURES) {
			if ("pack_manager".equals(f.id)) {
				f.action = () -> {
					if (!itemsReady()) {
						return; // before the first world the manager cannot draw its item icons
					}
					Features.flush();
					Nav.open(new TextureStudioScreen(this));
				};
			} else if ("presets".equals(f.id)) {
				f.action = () -> {
					Features.flush();
					Nav.open(new PresetsScreen(theme != null ? theme : new TextureStudioScreen(this), this));
				};
			} else if ("pack_browser".equals(f.id)) {
				f.action = () -> {
					Features.flush();
					Nav.open(new PackBrowserScreen(new TextureStudioScreen(this), this));
				};
			}
		}
		theme = null;
		applyTheme();
		layout();
		stopEdit();
		String old = search == null ? "" : search.getValue();
		search = new EditBox(font, 0, 0, 40, 12, Component.literal("Search"));
		search.setBordered(false);
		search.setTextShadow(false);
		search.setHint(Component.literal((ww - sideW) / 3 >= 165 ? "Search features and settings" : "Search").withColor(FAINT));
		search.setTextColor(TEXT);
		search.setMaxLength(40);
		search.setValue(old);
		search.setResponder(s -> {
			scroll = 0;
			scrollTarget = 0;
			sel = null;
			showDetail = false;
		});
		addWidget(search);
		popup = null;
		capture = null;
	}

	/** Colours of the current theme of the main menu (dark or light, tinted by the chosen colour theme). */
	private void applyTheme() {
		if (theme == null) {
			theme = parent instanceof TextureStudioScreen t ? t : new TextureStudioScreen(null);
			theme.prepareStyle();
		}
		theme.ensureThemePalette();
		light = theme.lightMenu();
		WIN = theme.WIN;
		CARD = theme.CARD;
		SIDE = theme.SIDEBAR;
		FIELD = theme.FIELD;
		LINE = theme.LINE;
		BORDER = theme.BORDER;
		BORDER_HI = theme.BORDER_HI;
		TRACK = theme.TRACK;
		TEXT = theme.TEXT;
		SOFT = theme.SOFT;
		DIM = theme.DIM;
		FAINT = theme.FAINT;
		HOVER = theme.HOVER;
		DIMMER = theme.DIMMER;
		acc = theme.accent();
		acc2 = theme.acc3();
		if (search != null && FAINT != hintColor) {
			hintColor = FAINT;
			search.setHint(Component.literal((ww - sideW) / 3 >= 165 ? "Search features and settings" : "Search").withColor(FAINT));
			search.setTextColor(TEXT);
		}
	}

	private int hintColor;

	/** Window size follows the screen, so every GUI scale and resolution gets a sensible layout. */
	private void layout() {
		ww = Math.min(width - 16, 720);
		wh = Math.min(height - 16, 410);
		wx = (width - ww) / 2;
		wy = (height - wh) / 2;
		sideLabels = ww >= 420;
		sideW = sideLabels ? Math.max(120, Math.min(148, ww / 5)) : 34;
		int mainX = wx + sideW;
		int mainW = ww - sideW;
		narrow = mainW < 430;
		detW = narrow ? mainW : Math.max(170, Math.min(240, Math.round(mainW * 0.37f)));
		detX = narrow ? mainX : wx + ww - detW;
		listX = mainX + 12;
		listW = (narrow ? wx + ww : detX) - 8 - listX;
		listY = wy + 68;
		listH = wy + wh - 8 - listY;
	}

	/** Item stacks only work once the game has loaded a world's data (same check as the title screen). */
	private static boolean itemsReady() {
		try {
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private boolean inWorld() {
		return minecraft != null && minecraft.level != null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** No blur in a world: changes show live in the game behind the menu. */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (inWorld()) {
			return;
		}
		super.renderBackground(g, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		if (capture != null) {
			// Esc while waiting for a key: no key
			capture.set(0);
			capture = null;
			return;
		}
		if (popup != null) {
			popup = null;
			return;
		}
		if (closing) {
			return;
		}
		stopEdit();
		Features.flush();
		if (Features.menuAnim.b()) {
			closing = true;
		} else {
			Nav.open(parent);
		}
	}

	@Override
	public void removed() {
		Features.flush();
		super.removed();
	}

	// ================================================================== small helpers

	private static int alpha(int argb, int a) {
		return TextureStudioScreen.alpha(argb, a);
	}

	private static int mix(int a, int b, float t) {
		return TextureStudioScreen.mix(a, b, t);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Eased value for a key: moves towards the target a bit every frame (no hard jumps). */
	private float anim(String key, float target, float speed) {
		Float cur = anims.get(key);
		float v = cur == null || !Features.menuAnim.b() ? target : cur;
		v += (target - v) * Math.min(1f, dt * speed);
		if (Math.abs(target - v) < 0.004f) {
			v = target;
		}
		anims.put(key, v);
		return v;
	}

	private float peek(String key) {
		Float v = anims.get(key);
		return v == null ? 0f : v;
	}

	private void pushClip(int x0, int y0, int x1, int y1) {
		if (!clips.isEmpty()) {
			int[] c = clips.get(clips.size() - 1);
			x0 = Math.max(x0, c[0]);
			y0 = Math.max(y0, c[1]);
			x1 = Math.min(x1, c[2]);
			y1 = Math.min(y1, c[3]);
		}
		x1 = Math.max(x0, x1);
		y1 = Math.max(y0, y1);
		clips.add(new int[] { x0, y0, x1, y1 });
		FillBatch.clip(x0, y0, x1, y1);
	}

	private void popClip() {
		if (!clips.isEmpty()) {
			clips.remove(clips.size() - 1);
		}
		if (clips.isEmpty()) {
			FillBatch.noClip();
		} else {
			int[] c = clips.get(clips.size() - 1);
			FillBatch.clip(c[0], c[1], c[2], c[3]);
		}
	}

	private int[] clip() {
		return clips.isEmpty() ? null : clips.get(clips.size() - 1);
	}

	/** Is the mouse over this rectangle (and is the rectangle's part under the mouse visible and free)? */
	private boolean over(int x, int y, int w, int h) {
		if (closing || mx < x || mx >= x + w || my < y || my >= y + h) {
			return false;
		}
		int[] c = clip();
		if (c != null && (mx < c[0] || mx >= c[2] || my < c[1] || my >= c[3])) {
			return false;
		}
		if (active != null) {
			return false;
		}
		return inPopup || popup == null;
	}

	private void zone(int x, int y, int w, int h, String key, Runnable click, Drag drag, Runnable right) {
		int[] c = clip();
		int x1 = x + w;
		int y1 = y + h;
		if (c != null) {
			x = Math.max(x, c[0]);
			y = Math.max(y, c[1]);
			x1 = Math.min(x1, c[2]);
			y1 = Math.min(y1, c[3]);
		}
		if (x1 <= x || y1 <= y) {
			return;
		}
		(inPopup ? popupZones : zones).add(new Zone(x, y, x1 - x, y1 - y, key, click, drag, right));
	}

	private void zone(int x, int y, int w, int h, String key, Runnable click) {
		zone(x, y, w, h, key, click, null, null);
	}

	private float tw(String s, float scale) {
		return font.width(s) * scale;
	}

	private String fit(String s, float maxW, float scale) {
		if (tw(s, scale) <= maxW) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, Math.round(maxW / scale) - font.width(".."))) + "..";
	}

	/** Text at one of the four sizes; follows the open/close animation and the clip of the scrolling list. */
	private void text(GuiGraphics g, String s, float x, float y, float scale, int color) {
		if (s.isEmpty()) {
			return;
		}
		int a = Math.round((color >>> 24) * winAlpha);
		if (a < 8) {
			return;
		}
		int[] c = clip();
		if (c != null) {
			float h = 9 * scale;
			if (y + h <= c[1] || y >= c[3] || x >= c[2]) {
				return;
			}
			// while the window is still scaling there is no scissor: leave out lines that would stick out
			if (animating && (y < c[1] - 1 || y + h > c[3] + 1)) {
				return;
			}
		}
		float px = wx + ww / 2f;
		float py = wy + wh / 2f;
		float sx = animating ? px + (x - px) * winScale : x;
		float sy = animating ? py + (y - py) * winScale : y;
		float sc = animating ? scale * winScale : scale;
		FillBatch.flush(g);
		boolean scissor = c != null && !animating;
		if (scissor) {
			g.enableScissor(c[0], c[1], c[2], c[3]);
		}
		g.pose().pushMatrix();
		g.pose().translate(sx, sy);
		g.pose().scale(sc, sc);
		g.drawString(font, s, 0, 0, (a << 24) | (color & 0xFFFFFF), false);
		g.pose().popMatrix();
		if (scissor) {
			g.disableScissor();
		}
	}

	private void rtext(GuiGraphics g, String s, float rightX, float y, float scale, int color) {
		text(g, s, rightX - tw(s, scale), y, scale, color);
	}

	private void ctext(GuiGraphics g, String s, float centerX, float y, float scale, int color) {
		text(g, s, centerX - tw(s, scale) / 2f, y, scale, color);
	}

	private static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		TextureStudioScreen.rrect(g, x, y, w, h, r, color);
	}

	/** Rounded box with a 1 px border. */
	private static void box(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int border) {
		TextureStudioScreen.rrect(g, x, y, w, h, r, fill);
		TextureStudioScreen.outline(g, x, y, w, h, r, border);
	}

	// ================================================================== frame

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		mx = mouseX;
		my = mouseY;
		applyTheme();

		// open / close: fade and a slight scale
		boolean animate = Features.menuAnim.b();
		if (closing) {
			openT = animate ? Math.max(0f, openT - dt / 0.13f) : 0f;
			if (openT <= 0f) {
				Nav.open(parent);
				return;
			}
		} else {
			openT = animate ? Math.min(1f, openT + dt / 0.2f) : 1f;
		}
		float e = 1f - (1f - openT) * (1f - openT) * (1f - openT);
		animating = e < 0.999f;
		winScale = 0.94f + 0.06f * e;
		winAlpha = e;

		if (leftDown && active != null && active.drag() != null) {
			active.drag().at(mx, my);
		}
		dScroll += (dTarget - dScroll) * (animate ? Math.min(1f, dt * 16f) : 1f);
		if (Math.abs(dTarget - dScroll) < 0.3f) {
			dScroll = dTarget;
		}
		scroll += (scrollTarget - scroll) * (animate ? Math.min(1f, dt * 16f) : 1f);
		if (Math.abs(scrollTarget - scroll) < 0.3f) {
			scroll = scrollTarget;
		}

		zones.clear();
		popupZones.clear();
		clips.clear();
		popupSeen = false;
		editSeen = false;
		FillBatch.begin();
		try {
			FillBatch.fill(g, 0, 0, width, height, alpha(light ? 0xE8ECF4 : 0x08070E,
					Math.round((inWorld() ? (DIMMER >>> 24) : 0xFF) * e)));
			if (animating) {
				FillBatch.transform(winScale, wx + ww / 2f, wy + wh / 2f, winAlpha);
			}
			drawWindow(g);
			drawSidebar(g);
			drawHeader(g);
			drawList(g);
			drawDetail(g);
			if (popup != null && !popupSeen) {
				popup = null;
			}
			drawPopup(g);
		} finally {
			clips.clear();
			FillBatch.noClip();
			FillBatch.noTransform();
			FillBatch.end(g);
		}
		if (!animating) {
			search.render(g, mouseX, mouseY, delta);
			if (edit != null) {
				if (!editSeen) {
					stopEdit();
				} else {
					edit.setX(editX);
					edit.setY(editY);
					edit.setWidth(editW);
					int[] ec = editClipArea != null ? editClipArea : new int[] { wx, wy, wx + ww, wy + wh };
					g.enableScissor(ec[0], ec[1], ec[2], ec[3]);
					edit.render(g, mouseX, mouseY, delta);
					g.disableScissor();
				}
			}
		}
	}

	private void drawWindow(GuiGraphics g) {
		TextureStudioScreen.shadow(g, wx, wy + 3, ww, wh, 9, 9, light ? 0x08 : 0x1C);
		TextureStudioScreen.glow(g, wx, wy, ww, wh, 9, 3, acc, light ? 0x04 : 0x0A);
		TextureStudioScreen.vgrad(g, wx, wy, ww, wh, 9, 9, mix(WIN, acc, light ? 0.02f : 0.05f), WIN);
		// sidebar: a darker strip with the window's rounded left corners
		int sideC = mix(SIDE, light ? 0xFFFFFFFF : 0xFF000000, light ? 0.3f : 0.25f);
		rr(g, wx, wy, sideW + 9, wh, 9, sideC);
		FillBatch.fill(g, wx + sideW, wy, wx + sideW + 9, wy + wh, mix(WIN, acc, light ? 0.02f : 0.04f));
		FillBatch.fill(g, wx + sideW, wy, wx + sideW + 1, wy + wh, LINE);
		// header line over the main area
		FillBatch.fill(g, wx + sideW + 1, wy + 40, wx + ww - 1, wy + 41, LINE);
		TextureStudioScreen.outline(g, wx, wy, ww, wh, 9, mix(BORDER, acc, 0.25f));
		// thin bright line on top
		TextureStudioScreen.hgrad(g, wx + 9, wy, ww / 2 - 9, 1, 0, alpha(acc, 0), mix(acc, 0xFFFFFFFF, 0.45f));
		TextureStudioScreen.hgrad(g, wx + ww / 2, wy, ww / 2 - 9, 1, 0, mix(acc, 0xFFFFFFFF, 0.45f), alpha(acc, 0));
	}

	/** Small capital label ("CATEGORIES", "SETTINGS"). */
	private void label(GuiGraphics g, String s, float x, float y) {
		text(g, s, x, y, 0.72f, FAINT);
	}

	/** Switched-on / switchable features of a category. */
	private int[] count(List<Feature> list) {
		int on = 0;
		int all = 0;
		for (Feature f : list) {
			if (f.hasToggle()) {
				all++;
				if (f.isOn()) {
					on++;
				}
			}
		}
		return new int[] { on, all };
	}

	private static List<Feature> ofCat(Cat c) {
		List<Feature> out = new ArrayList<>();
		for (Feature f : PinSettings.FEATURES) {
			if (f.cat == c) {
				out.add(f);
			}
		}
		return out;
	}

	private static Set<String> favs() {
		return Features.cfg.featureFavs;
	}

	private static List<Feature> favList() {
		List<Feature> out = new ArrayList<>();
		for (Feature f : PinSettings.FEATURES) {
			if (favs().contains(f.id)) {
				out.add(f);
			}
		}
		return out;
	}

	/** One row of the sidebar; returns nothing, registers its own click zone. */
	private void navRow(GuiGraphics g, String key, String[] ico, String label, String badge, boolean on, int x,
			int y, int w, int ih, Runnable click) {
		boolean hov = over(x, y, w, ih);
		float t = anim("nav:" + key, on ? 1f : 0f, 14f);
		float h = anim("navh:" + key, hov ? 1f : 0f, 14f);
		if (t > 0.01f) {
			rr(g, x, y, w, ih, 6, alpha(acc, Math.round((light ? 0x2A : 0x38) * t)));
			rr(g, wx + 3, y + 5, 2, ih - 10, 1, alpha(acc, Math.round(255 * t)));
		}
		if (h > 0.01f && !on) {
			rr(g, x, y, w, ih, 6, alpha(HOVER, Math.round((light ? 0x0C : 0x10) * h)));
		}
		int col = mix(mix(DIM, SOFT, h), light ? acc : 0xFFE9DDFF, t);
		int ix = sideLabels ? x + 8 : x + (w - 7) / 2;
		TextureStudioScreen.glyph(g, ico, ix, y + (ih - 7) / 2, col);
		if (sideLabels) {
			text(g, fit(label, w - 48, T_FEATURE * 0.95f), x + 22, y + (ih - 8) / 2f, T_FEATURE * 0.95f,
					mix(mix(SOFT, TEXT, h), TEXT, t));
			if (badge != null && !badge.isEmpty()) {
				float bs = 0.72f;
				int bw = Math.round(tw(badge, bs)) + 8;
				int bx = x + w - 5 - bw;
				int by = y + (ih - 10) / 2;
				rr(g, bx, by, bw, 10, 3, t > 0.5f ? alpha(acc, 0x55) : alpha(HOVER, light ? 0x10 : 0x0C));
				ctext(g, badge, bx + bw / 2f, by + 2.5f, bs, t > 0.5f ? (light ? acc : 0xFFE9DDFF) : FAINT);
			}
		}
		zone(x, y, w, ih, "nav", click);
	}

	private void drawSidebar(GuiGraphics g) {
		int x = wx + 8;
		int w = sideW - 16;
		// mark + title
		TextureStudioScreen.hgrad(g, wx + (sideLabels ? 12 : 9), wy + 12, 16, 16, 5, acc, acc2);
		TextureStudioScreen.glyph(g, I_PIN, wx + (sideLabels ? 16 : 13), wy + 16, WHITE);
		if (sideLabels) {
			text(g, fit("Pin Visuals", sideW - 40, T_FEATURE * 1.1f), wx + 33, wy + 11, T_FEATURE * 1.1f, TEXT);
			text(g, "v2.0.0", wx + 33, wy + 22.5f, 0.72f, FAINT);
		}
		int y = wy + (sideLabels ? 46 : 40);
		if (sideLabels) {
			label(g, "CATEGORIES", x + 4, y);
			y += 10;
		}
		int rows = Cat.values().length + 1;
		int room = wh - (y - wy) - (sideLabels ? 62 : 10);
		int step = Math.max(14, Math.min(23, room / rows));
		int ih = step - 3;
		boolean searching = !searchText().isEmpty();
		for (Cat c : Cat.values()) {
			final Cat cc = c;
			int[] n = count(ofCat(c));
			String badge = n[1] == 0 ? String.valueOf(ofCat(c).size()) : n[0] + "/" + n[1];
			navRow(g, "c" + c.name(), icon(c), c.label, badge, c == cat && !favView && !searching, x, y, w, ih, () -> {
				cat = cc;
				favView = false;
				search.setValue("");
				showList();
			});
			y += step;
		}
		FillBatch.fill(g, x + 4, y + 1, x + w - 4, y + 2, LINE);
		y += 6;
		navRow(g, "fav", I_STAR, "Favorites", String.valueOf(favs().size()), favView && !searching, x, y, w, ih, () -> {
			favView = true;
			search.setValue("");
			showList();
		});
		if (!sideLabels) {
			return;
		}
		// presets card + open key
		int py = wy + wh - 50;
		if (py > y + ih + 8) {
			Feature presets = byId("presets");
			boolean hov = over(x, py, w, 28);
			float h = anim("presets", hov ? 1f : 0f, 14f);
			box(g, x, py, w, 28, 7, mix(CARD, HOVER, (light ? 0.04f : 0.03f) * h), mix(LINE, BORDER_HI, h));
			TextureStudioScreen.hgrad(g, x + 5, py + 5, 18, 18, 5, alpha(acc, 0xCC), alpha(acc2, 0xCC));
			TextureStudioScreen.glyph(g, I_STAR, x + 10, py + 10, WHITE);
			int[] n = count(PinSettings.FEATURES);
			text(g, "Presets", x + 28, py + 6, T_SETTING, TEXT);
			text(g, fit(n[0] + " features on", w - 42, 0.75f), x + 28, py + 16, 0.75f, DIM);
			TextureStudioScreen.glyph(g, I_RIGHT, x + w - 11, py + 10, mix(FAINT, SOFT, h));
			if (presets != null && presets.action != null) {
				zone(x, py, w, 28, "presets", presets.action);
			}
		}
		String k = TextureStudioClient.openKeyName();
		text(g, "Open menu", x + 4, wy + wh - 15, 0.75f, FAINT);
		float ks = 0.8f;
		int kw = Math.max(12, Math.round(tw(k, ks)) + 8);
		int kx = x + w - 2 - kw;
		box(g, kx, wy + wh - 18, kw, 12, 3, FIELD, BORDER);
		ctext(g, k, kx + kw / 2f, wy + wh - 15, ks, SOFT);
	}

	private static Feature byId(String id) {
		for (Feature f : PinSettings.FEATURES) {
			if (f.id.equals(id)) {
				return f;
			}
		}
		return null;
	}

	/** Back to the list (scroll to the top, nothing open). */
	private void showList() {
		scroll = 0;
		scrollTarget = 0;
		popup = null;
		picker = null;
		sel = null;
		showDetail = false;
	}

	private String searchText() {
		return search == null ? "" : norm(search.getValue().trim());
	}

	/** Lower case, and British / American spelling count the same ("colour" = "color", "grey" = "gray"). */
	private static String norm(String s) {
		return s.toLowerCase(Locale.ROOT).replace("colour", "color").replace("grey", "gray");
	}

	private static final String[] FILTERS = { "All", "On", "Off", "Changed" };

	private boolean passes(Feature f, int flt) {
		return switch (flt) {
			case 1 -> f.hasToggle() && f.isOn();
			case 2 -> f.hasToggle() && !f.isOn();
			case 3 -> !f.allDefault();
			default -> true;
		};
	}

	private void drawHeader(GuiGraphics g) {
		int hx = wx + sideW + 14;
		int right = wx + ww - 12;
		// close
		int cs = 18;
		int cx = right - cs;
		int cy = wy + 11;
		float ch = anim("close", over(cx, cy, cs, cs) ? 1f : 0f, 14f);
		box(g, cx, cy, cs, cs, 5, mix(alpha(CARD, 0), mix(CARD, 0xFFEF4444, 0.45f), ch), mix(LINE, 0xFFEF4444, ch));
		TextureStudioScreen.glyph(g, I_CLOSE, cx + 6, cy + 6, mix(DIM, TEXT, ch));
		zone(cx, cy, cs, cs, "close", this::onClose);
		// search
		int sw = Math.max(70, Math.min(170, (ww - sideW) / 3));
		int sx = cx - 8 - sw;
		int sy = wy + 11;
		boolean focus = search.isFocused();
		float sf = anim("search", focus ? 1f : over(sx, sy, sw, 18) ? 0.5f : 0f, 14f);
		box(g, sx, sy, sw, 18, 6, mix(FIELD, CARD, sf), mix(BORDER, acc, sf));
		TextureStudioScreen.glyph(g, I_SEARCH, sx + 6, sy + 6, mix(FAINT, acc, sf));
		search.setX(sx + 18);
		search.setY(sy + 5);
		search.setWidth(sw - 24);
		zone(sx, sy, sw, 18, "search", () -> setFocused(search));
		// title + "14 features · 5 on"
		String q = searchText();
		String title = !q.isEmpty() ? "Search" : favView ? "Favorites" : cat.label;
		float ts = T_CAT * 1.12f;
		text(g, fit(title, sx - hx - 8, ts), hx, wy + 9, ts, TEXT);
		List<Feature> base = listed(new HashMap<>(), 0);
		int[] n = count(base);
		String a = base.size() + (base.size() == 1 ? " feature" : " features");
		float ss = 0.78f;
		text(g, a, hx, wy + 25, ss, DIM);
		if (n[1] > 0) {
			float ax = hx + tw(a, ss);
			text(g, "  ·  ", ax, wy + 25, ss, FAINT);
			text(g, n[0] + " on", ax + tw("  ·  ", ss), wy + 25, ss, n[0] > 0 ? OK : FAINT);
		}
	}

	/** Features of the list, and for each the settings matched by the search. */
	private List<Feature> listed(Map<Feature, List<S>> match, int flt) {
		String q = searchText();
		List<Feature> out = new ArrayList<>();
		for (Feature f : PinSettings.FEATURES) {
			if (q.isEmpty()) {
				if ((favView ? favs().contains(f.id) : f.cat == cat) && passes(f, flt)) {
					out.add(f);
				}
				continue;
			}
			if (!passes(f, flt)) {
				continue;
			}
			if (norm(f.name).contains(q) || norm(f.desc).contains(q) || norm(f.cat.label).contains(q)) {
				out.add(f);
				continue;
			}
			List<S> hit = new ArrayList<>();
			for (S s : f.settings) {
				if (s.shown() && norm(s.name).contains(q)) {
					hit.add(s);
				}
			}
			if (!hit.isEmpty()) {
				out.add(f);
				match.put(f, hit);
			}
		}
		return out;
	}

	private void drawChips(GuiGraphics g) {
		int x = listX;
		int y = wy + 48;
		float s = 0.8f;
		for (int i = 0; i < FILTERS.length; i++) {
			final int idx = i;
			String n = String.valueOf(listed(new HashMap<>(), i).size());
			int w = Math.round(tw(FILTERS[i], s) + tw(n, s)) + 18;
			if (x + w > listX + listW) {
				break;
			}
			boolean on = filter == i;
			float h = anim("chiph:" + i, over(x, y, w, 13) ? 1f : 0f, 14f);
			float t = anim("chip:" + i, on ? 1f : 0f, 14f);
			box(g, x, y, w, 13, 6, alpha(acc, Math.round(255 * t)), mix(mix(BORDER, BORDER_HI, h), acc, t));
			int tc = mix(mix(DIM, TEXT, h), WHITE, t);
			text(g, FILTERS[i], x + 7, y + 3, s, tc);
			text(g, n, x + 11 + tw(FILTERS[i], s), y + 3, s, alpha(tc, 0x99));
			zone(x, y, w, 13, "chip", () -> {
				filter = idx;
				scroll = 0;
				scrollTarget = 0;
			});
			x += w + 4;
		}
	}

	private void drawList(GuiGraphics g) {
		Map<Feature, List<S>> match = new HashMap<>();
		List<Feature> list = listed(match, filter);
		lastMatch = match;
		if (!narrow && !list.isEmpty() && (sel == null || !list.contains(sel))) {
			sel = list.get(0);
			dScroll = 0;
			dTarget = 0;
		}
		if (narrow && showDetail) {
			return;
		}
		drawChips(g);
		pushClip(listX - 3, listY, listX + listW + 3, listY + listH);
		int cols = listW >= 300 ? 2 : 1;
		int gap = 6;
		int tileW = (listW - 7 - gap * (cols - 1)) / cols;
		int y0 = listY + 2 - Math.round(scroll);
		for (int i = 0; i < list.size(); i++) {
			int c = i % cols;
			int r = i / cols;
			tile(g, list.get(i), listX + c * (tileW + gap), y0 + r * (TILE + gap), tileW);
		}
		int rowsN = (list.size() + cols - 1) / cols;
		contentH = rowsN * (TILE + gap) + 6;
		if (list.isEmpty()) {
			ctext(g, favView && searchText().isEmpty() ? "No favorites yet" : "Nothing found", listX + listW / 2f,
					listY + listH / 2f - 10, T_FEATURE, DIM);
			ctext(g, favView && searchText().isEmpty() ? "Open a feature and press the star" : "Try another word or filter",
					listX + listW / 2f, listY + listH / 2f + 3, T_SETTING, FAINT);
		}
		popClip();
		// soft fade at the bottom edge of the list
		int max = Math.max(0, contentH - listH);
		if (max > 0 && scroll < max - 1) {
			for (int i = 0; i < 8; i++) {
				FillBatch.fill(g, listX - 3, listY + listH - 8 + i, listX + listW - 5, listY + listH - 7 + i,
						alpha(WIN, i * 28));
			}
		}
		if (scrollTarget > max) {
			scrollTarget = max;
		}
		scrollbar(g, "scroll", listX + listW - 2, listY, listH, contentH, scroll, max, v -> {
			scrollTarget = v;
			scroll = v;
		});
	}

	private interface FloatSet {
		void set(float v);
	}

	/** Slim scrollbar (only when the content is longer than the view). */
	private void scrollbar(GuiGraphics g, String key, int bx, int top, int h, int content, float pos, int max,
			FloatSet to) {
		if (max <= 0) {
			return;
		}
		int th = Math.max(18, h * h / Math.max(1, content));
		int ty = top + Math.round((h - th) * (pos / max));
		boolean hov = over(bx - 3, top, 9, h) || key.equals(activeKey);
		float t = anim(key, hov ? 1f : 0f, 12f);
		rr(g, bx, ty, 3, th, 1, mix(TRACK, DIM, t));
		zone(bx - 3, top, 9, h, key, null, (px, py) -> {
			float f = (py - top - th / 2f) / Math.max(1, h - th);
			to.set(Math.max(0, Math.min(max, f * max)));
		}, null);
	}

	/** One feature tile of the grid: icon, name, short text, switch (or button). */
	private void tile(GuiGraphics g, Feature f, int x, int y, int w) {
		int h = TILE;
		int[] c = clip();
		if (c != null && (y + h < c[1] - 4 || y > c[3] + 4)) {
			return;
		}
		boolean on = f.isOn();
		boolean selected = f == sel && (!narrow || showDetail);
		boolean hov = over(x, y, w, h);
		float hv = anim("th:" + f.id, hov ? 1f : 0f, 14f);
		float st = anim("ts:" + f.id, selected ? 1f : 0f, 12f);
		float ot = anim("to:" + f.id, on ? 1f : 0f, 12f);
		int base = mix(CARD, HOVER, (light ? 0.05f : 0.035f) * hv);
		if (st > 0.02f) {
			TextureStudioScreen.glow(g, x, y, w, h, 7, 3, acc, Math.round(0x16 * st));
		}
		rr(g, x, y, w, h, 7, base);
		if (st > 0.02f) {
			TextureStudioScreen.hgrad(g, x, y, w * 2 / 3, h, 7, alpha(acc, Math.round((light ? 0x22 : 0x30) * st)),
					alpha(acc, 0));
		}
		TextureStudioScreen.outline(g, x, y, w, h, 7, mix(mix(LINE, BORDER_HI, hv * 0.8f), acc, st * 0.85f));
		// icon
		int is = 22;
		int ix = x + 8;
		int iy = y + (h - is) / 2;
		rr(g, ix, iy, is, is, 6, alpha(HOVER, light ? 0x10 : 0x0A));
		if (ot > 0.02f) {
			TextureStudioScreen.hgrad(g, ix, iy, is, is, 6, alpha(acc, Math.round(0x90 * ot)), alpha(acc2, Math.round(0x70 * ot)));
		}
		TextureStudioScreen.glyph(g, featureIcon(f), ix + 7, iy + 7, mix(DIM, WHITE, ot));
		// right side
		int rx = x + w - 8;
		if (f.hasToggle()) {
			rx -= 26;
			toggle(g, "f:" + f.id, rx, y + (h - 14) / 2, on);
		} else if (f.action != null) {
			int bw = Math.round(tw(f.actionLabel, T_SETTING)) + 14;
			rx -= bw;
			float bh = anim("act:" + f.id, over(rx, y + (h - 16) / 2, bw, 16) ? 1f : 0f, 14f);
			TextureStudioScreen.hgrad(g, rx, y + (h - 16) / 2, bw, 16, 5, mix(acc, 0xFF000000, 0.12f * bh),
					mix(acc2, 0xFF000000, 0.12f * bh));
			ctext(g, f.actionLabel, rx + bw / 2f, y + (h - 16) / 2 + 4.5f, T_SETTING, WHITE);
		}
		// name + text
		int tx = ix + is + 8;
		float nameW = rx - 6 - tx;
		String tag = NEW.contains(f.id) ? "NEW" : null;
		float ns = T_FEATURE * 0.95f;
		String name = fit(f.name, nameW - (tag != null ? 22 : 0), ns);
		text(g, name, tx, y + 8, ns, TEXT);
		if (tag != null) {
			float gx = tx + tw(name, ns) + 4;
			rr(g, Math.round(gx), y + 8, 18, 9, 2, alpha(0xFFFBBF24, 0x2A));
			ctext(g, tag, gx + 9, y + 10, 0.62f, 0xFFFBBF24);
		}
		text(g, fit(f.desc, nameW, 0.78f), tx, y + 21, 0.78f, DIM);
		// clicks: the tile opens the feature, the switch / button do their own thing (registered last = wins)
		zone(x, y, w, h, "tile", () -> {
			if (sel != f) {
				dScroll = 0;
				dTarget = 0;
				picker = null;
			}
			sel = f;
			showDetail = true;
			popup = null;
		});
		if (f.hasToggle()) {
			zone(rx - 4, y + 2, 34, h - 4, "toggle", () -> {
				f.toggle();
				Features.flush();
			});
		} else if (f.action != null) {
			int bw = Math.round(tw(f.actionLabel, T_SETTING)) + 14;
			zone(rx, y + (h - 16) / 2, bw, 16, "action", f.action);
		}
	}

	/** Word wrap for the description in the side panel. */
	private List<String> wrap(String s, float maxW, float scale) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String tryLine = line.length() == 0 ? word : line + " " + word;
			if (tw(tryLine, scale) > maxW && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(tryLine);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	/** The side panel: everything about the chosen feature. */
	private void drawDetail(GuiGraphics g) {
		if (narrow && !showDetail) {
			return;
		}
		int x = detX;
		int y = wy + 41;
		int w = detW;
		int bottom = wy + wh;
		if (!narrow) {
			TextureStudioScreen.vgrad(g, x, y, w, bottom - y, 0, 9, alpha(0xFF000000, light ? 0x06 : 0x26),
					alpha(0xFF000000, light ? 0x06 : 0x26));
			FillBatch.fill(g, x, y, x + 1, bottom - 1, LINE);
		}
		Feature f = sel;
		if (f == null) {
			ctext(g, "Pick a feature", x + w / 2f, y + (bottom - y) / 2f - 4, T_SETTING, FAINT);
			return;
		}
		int pad = 12;
		int ix = x + pad;
		int iw = w - 2 * pad;
		int yy = y + 10;
		if (narrow) {
			boolean bh = over(ix - 2, yy - 2, 50, 14);
			int bc = bh ? TEXT : DIM;
			TextureStudioScreen.glyph(g, I_LEFT, ix, yy + 1, bc);
			text(g, "Back", ix + 10, yy + 1, T_SETTING, bc);
			zone(ix - 2, yy - 2, 50, 14, "back", () -> {
				showDetail = false;
				picker = null;
				popup = null;
			});
			yy += 16;
		}
		// head: icon, name, state, switch
		boolean on = f.isOn();
		TextureStudioScreen.glow(g, ix, yy, 26, 26, 7, 3, acc, 0x14);
		TextureStudioScreen.hgrad(g, ix, yy, 26, 26, 7, acc, acc2);
		TextureStudioScreen.glyph2(g, featureIcon(f), ix + 6, yy + 6, 2, WHITE);
		int rx = ix + iw;
		if (f.hasToggle()) {
			rx -= 26;
			toggle(g, "d:" + f.id, rx, yy + 6, on);
			zone(rx - 4, yy + 2, 34, 22, "toggle", () -> {
				f.toggle();
				Features.flush();
			});
		}
		text(g, fit(f.name, rx - ix - 40, T_CAT), ix + 33, yy + 3, T_CAT, TEXT);
		if (f.hasToggle()) {
			text(g, on ? "ACTIVE" : "OFF", ix + 33, yy + 16, 0.7f, on ? OK : FAINT);
		} else {
			text(g, f.cat.label.toUpperCase(Locale.ROOT), ix + 33, yy + 16, 0.7f, FAINT);
		}
		yy += 33;
		List<String> lines = wrap(f.desc, iw, 0.8f);
		for (int i = 0; i < Math.min(4, lines.size()); i++) {
			text(g, lines.get(i), ix, yy + i * 10, 0.8f, DIM);
		}
		yy += Math.min(4, lines.size()) * 10 + 4;
		if (f.action != null && !f.hasToggle()) {
			float bh = anim("dact:" + f.id, over(ix, yy, iw, 20) ? 1f : 0f, 14f);
			TextureStudioScreen.hgrad(g, ix, yy, iw, 20, 6, mix(acc, 0xFF000000, 0.12f * bh), mix(acc2, 0xFF000000, 0.12f * bh));
			ctext(g, f.actionLabel, ix + iw / 2f, yy + 6, T_SETTING, WHITE);
			zone(ix, yy, iw, 20, "action", f.action);
			yy += 26;
		}
		FillBatch.fill(g, x + 1, yy, x + w - 1, yy + 1, LINE);
		// footer
		int fy = bottom - 30;
		FillBatch.fill(g, x + 1, fy, x + w - 1, fy + 1, LINE);
		int bw = (iw - 6) / 2;
		boolean def = f.allDefault();
		footButton(g, "freset", I_RESET, "Reset", ix, fy + 7, bw, false, !def, () -> {
			f.resetAll();
			pickInit = false;
			Features.flush();
		});
		boolean fav = favs().contains(f.id);
		footButton(g, "ffav", I_STAR, fav ? "Favorite" : "Add to favorites", ix + bw + 6, fy + 7, bw, fav, true, () -> {
			if (!favs().remove(f.id)) {
				favs().add(f.id);
			}
			Features.cfg.save();
		});
		// settings (scroll)
		int top = yy + 1;
		dTop = top;
		dH = fy - top;
		List<S> only = lastMatch.get(f);
		List<S> rows = shownSettings(f, only);
		pushClip(x + 1, top, x + w - 1, fy);
		editClipArea = new int[] { x + 1, top, x + w - 1, fy };
		int ry = top + 8 - Math.round(dScroll);
		if (rows.isEmpty()) {
			text(g, "No settings", ix, ry + 2, T_SETTING, FAINT);
			ry += 14;
		} else {
			label(g, only != null ? "MATCHING SETTINGS" : "SETTINGS", ix, ry);
			ry += 10;
			for (S s : rows) {
				ry += row(g, f, s, ix, ry, iw - 4) + 2;
			}
		}
		dContentH = ry + Math.round(dScroll) - top + 6;
		popClip();
		int max = Math.max(0, dContentH - dH);
		if (dTarget > max) {
			dTarget = max;
		}
		scrollbar(g, "dscroll", x + w - 5, top + 2, dH - 4, dContentH, dScroll, max, v -> {
			dTarget = v;
			dScroll = v;
		});
	}

	private void footButton(GuiGraphics g, String key, String[] ico, String label, int x, int y, int w,
			boolean lit, boolean enabled, Runnable click) {
		boolean hov = enabled && over(x, y, w, 16);
		float h = anim(key, hov ? 1f : 0f, 14f);
		box(g, x, y, w, 16, 5, mix(alpha(CARD, 0x80), CARD, h), lit ? alpha(acc, 0xCC) : mix(BORDER, BORDER_HI, h));
		int col = !enabled ? FAINT : lit ? (light ? acc : 0xFFE9DDFF) : mix(SOFT, TEXT, h);
		float s = 0.8f;
		String l = fit(label, w - 20, s);
		float lw = tw(l, s) + 10;
		float lx = x + (w - lw) / 2f;
		TextureStudioScreen.glyph(g, ico, Math.round(lx), y + 5, lit ? 0xFFFBBF24 : col);
		text(g, l, lx + 10, y + 5, s, col);
		if (enabled) {
			zone(x, y, w, 16, key, click);
		}
	}

	/** A pixel icon for every feature (category icon when there is none of its own). */
	private static String[] featureIcon(Feature f) {
		return switch (f.id) {
			case "block_outline", "block_place" -> I_BOX;
			case "atmosphere", "motion_blur" -> I_WAVE;
			case "kill_effects", "pop_chams", "no_totem_particles", "no_explosion_particles", "fewer_hit_particles" ->
				I_SPARK;
			case "viewmodel", "knife", "swing" -> I_KNIFE;
			case "hurt_cam", "low_shield" -> I_SHIELD;
			case "zoom" -> I_SEARCH;
			case "freelook" -> I_EYE;
			case "block_break" -> I_BATTERY;
			case "item_physics", "hide_far_items" -> I_DROP;
			case "low_fire" -> I_FIRE;
			case "cape", "wings", "hat" -> I_CAPE;
			case "aura", "trail" -> I_RING;
			case "dura_guard" -> I_PICK;
			case "crosshair" -> I_HUD;
			case "spotify", "lyrics" -> I_MUSIC;
			case "presets" -> I_STAR;
			default -> icon(f.cat);
		};
	}

	private List<S> shownSettings(Feature f, List<S> only) {
		List<S> out = new ArrayList<>();
		for (S s : only != null ? only : f.settings) {
			if (s.shown()) {
				out.add(s);
			}
		}
		return out;
	}

	private int rowHeight(S s, int w) {
		return switch (s.type) {
			case NUM -> 32;
			case COLOR -> 22 + Math.round(pickerHeight(s, w) * smooth(peek("pick:" + s.id)));
			default -> 22;
		};
	}

	// ================================================================== setting rows

	/** Draws a setting (label, reset button, control) and returns the row height. */
	private int row(GuiGraphics g, Feature f, S s, int x, int y, int w) {
		int h = rowHeight(s, w);
		int[] c = clip();
		if (c != null && (y + h < c[1] || y > c[3])) {
			return h;
		}
		boolean hov = over(x - 4, y, w + 8, Math.min(h, 22));
		float hv = anim("row:" + s.id, hov ? 1f : 0f, 14f);
		text(g, s.name, x, y + 7, T_SETTING, mix(SOFT, TEXT, hv));
		// reset button next to the label (only when the value is not the default)
		float rv = anim("rs:" + s.id, s.isDefault() ? 0f : 1f, 12f);
		if (rv > 0.02f) {
			int ix = x + Math.round(tw(s.name, T_SETTING)) + 6;
			boolean ih = over(ix - 3, y + 3, 13, 15);
			float it = anim("rsh:" + s.id, ih ? 1f : 0f, 14f);
			TextureStudioScreen.glyph(g, I_RESET, ix, y + 7, alpha(mix(FAINT, acc, it), Math.round(255 * rv)));
			resetX = ix - 3;
		} else {
			resetX = Integer.MIN_VALUE;
		}
		final int rzx = resetX;
		switch (s.type) {
			case BOOL -> {
				toggle(g, "s:" + s.id, x + w - 26, y + 4, s.b());
				zone(x - 4, y, w + 8, 22, "bool", () -> s.set(s.b() ? 0 : 1));
			}
			case NUM -> sliderRow(g, s, x, y, w);
			case CHOICE -> choiceRow(g, s, x, y, w);
			case COLOR -> colorRow(g, s, x, y, w);
			case KEY -> keyRow(g, s, x, y, w);
			case TEXT -> textRow(g, s, x, y, w);
		}
		if (rzx != Integer.MIN_VALUE) {
			// registered last so it wins over the row's own click zone
			zone(rzx, y + 3, 13, 15, "reset", () -> {
				s.reset();
				pickInit = false;
			});
		}
		return h;
	}

	/** Toggle switch (26 x 14): the knob slides and the track fades to the accent colour. */
	private void toggle(GuiGraphics g, String key, int x, int y, boolean on) {
		float t = smooth(anim("tg:" + key, on ? 1f : 0f, 13f));
		float h = anim("tgh:" + key, over(x - 4, y - 4, 34, 22) ? 1f : 0f, 14f);
		rr(g, x, y, 26, 14, 7, mix(TRACK, BORDER_HI, h));
		if (t > 0.01f) {
			TextureStudioScreen.hgrad(g, x, y, 26, 14, 7, alpha(acc, Math.round(255 * t)), alpha(acc2, Math.round(255 * t)));
		}
		int kx = x + 2 + Math.round(12 * t);
		rr(g, kx, y + 3, 10, 10, 5, 0x22000000);
		rr(g, kx, y + 2, 10, 10, 5, mix(light ? 0xFFFFFFFF : 0xFFA9A4BC, WHITE, t));
	}

	/** Slider with the live value on the right; a click on the number lets you type it. */
	private void sliderRow(GuiGraphics g, S s, int x, int y, int w) {
		String key = "sl:" + s.id;
		boolean pressed = key.equals(activeKey);
		int ty = y + 22;
		boolean hov = over(x - 2, ty - 6, w + 4, 16) || pressed;
		float hv = anim("slh:" + s.id, hov ? 1f : 0f, 14f);
		double span = Math.max(1e-9, s.max - s.min);
		float frac = anim("slv:" + s.id, (float) ((s.get() - s.min) / span), pressed ? 40f : 16f);
		frac = Math.max(0f, Math.min(1f, frac));
		rr(g, x, ty, w, 4, 2, TRACK);
		int fw = Math.round(w * frac);
		if (fw > 0) {
			TextureStudioScreen.hgrad(g, x, ty, fw, 4, 2, acc, mix(acc, acc2, frac));
		}
		int ks = 8 + Math.round(2 * hv);
		int kx = x + fw - ks / 2;
		int ky = ty + 2 - ks / 2;
		rr(g, kx - 1, ky, ks + 2, ks + 2, (ks + 2) / 2, 0x1A000000);
		rr(g, kx, ky, ks, ks, ks / 2, acc);
		rr(g, kx + 2, ky + 2, ks - 4, ks - 4, (ks - 4) / 2, WHITE);
		zone(x - 2, ty - 6, w + 4, 16, key, null, (px, py) -> {
			double f = Math.max(0, Math.min(1, (px - x) / (double) Math.max(1, w)));
			s.set(s.min + f * (s.max - s.min));
		}, null);

		// value (click to type)
		String val = s.format();
		int vw = Math.max(34, Math.round(tw(val, T_SETTING)) + 10);
		int vx = x + w - vw;
		if (editS == s) {
			editField(g, vx - 14, y + 3, vw + 14);
		} else {
			float vh = anim("slt:" + s.id, over(vx, y + 3, vw, 15) ? 1f : 0f, 14f);
			if (vh > 0.01f) {
				rr(g, vx, y + 3, vw, 15, 4, alpha(FIELD, Math.round(255 * vh)));
			}
			rtext(g, val, x + w - 5, y + 7, T_SETTING, mix(DIM, acc, vh));
			zone(vx, y + 3, vw, 15, "num", () -> startEdit(s,
					String.format(Locale.ROOT, "%." + s.decimals() + "f", s.get()), 12));
		}
	}

	/** Dropdown: shows the current choice; a click opens the list. */
	private void choiceRow(GuiGraphics g, S s, int x, int y, int w) {
		int bw = Math.max(70, Math.min(130, w / 2));
		int bx = x + w - bw;
		int by = y + 3;
		boolean open = popup == s;
		float hv = anim("ch:" + s.id, open ? 1f : over(bx, by, bw, 16) ? 0.6f : 0f, 14f);
		box(g, bx, by, bw, 16, 5, mix(FIELD, CARD, hv), mix(BORDER, acc, hv));
		text(g, fit(s.format(), bw - 22, T_SETTING), bx + 7, by + 4, T_SETTING, TEXT);
		TextureStudioScreen.glyph(g, open ? I_UP : I_DOWN, bx + bw - 13, by + 4, mix(DIM, acc, hv));
		if (open) {
			popupSeen = true;
			popX = bx;
			popY = by;
			popW = bw;
		}
		zone(bx, by, bw, 16, "choice", () -> {
			if (popup == s) {
				popup = null;
			} else {
				popup = s;
				anims.put("pop", 0f);
			}
		});
	}

	/** The open dropdown list, drawn above everything else. */
	private void drawPopup(GuiGraphics g) {
		S s = popup;
		if (s == null) {
			return;
		}
		inPopup = true;
		try {
			float t = smooth(anim("pop", 1f, 16f));
			int n = s.options.length;
			int ph = n * 16 + 6;
			int py = popY + 18;
			if (py + ph > wy + wh - 6) {
				py = Math.max(wy + 6, popY - ph - 2);
			}
			py -= Math.round((1f - t) * 4);
			int a = Math.round(255 * t);
			TextureStudioScreen.shadow(g, popX, py + 2, popW, ph, 6, 5, Math.round(0x0A * t));
			rr(g, popX, py, popW, ph, 6, alpha(CARD, a));
			TextureStudioScreen.outline(g, popX, py, popW, ph, 6, alpha(BORDER, a));
			for (int i = 0; i < n; i++) {
				final int idx = i;
				int oy = py + 3 + i * 16;
				boolean sel = s.i() == i;
				boolean hov = over(popX + 3, oy, popW - 6, 16);
				if (hov) {
					rr(g, popX + 3, oy, popW - 6, 16, 4, alpha(FIELD, a));
				}
				text(g, fit(s.options[i], popW - 24, T_SETTING), popX + 9, oy + 4, T_SETTING,
						alpha(sel ? acc : hov ? TEXT : SOFT, a));
				if (sel) {
					rr(g, popX + popW - 13, oy + 6, 4, 4, 2, alpha(acc, a));
				}
				zone(popX + 3, oy, popW - 6, 16, "option", () -> {
					s.set(idx);
					popup = null;
					Features.flush();
				});
			}
		} finally {
			inPopup = false;
		}
	}

	/** Key binding: click, then press a key (Esc, Backspace or a right click = no key). */
	private void keyRow(GuiGraphics g, S s, int x, int y, int w) {
		boolean cap = capture == s;
		String label = cap ? "Press a key" : s.format();
		int bw = Math.max(46, Math.round(tw(label, T_SETTING)) + 16);
		int bx = x + w - bw;
		int by = y + 3;
		float hv = anim("key:" + s.id, cap ? 1f : over(bx, by, bw, 16) ? 0.6f : 0f, 14f);
		box(g, bx, by, bw, 16, 5, mix(FIELD, CARD, hv), mix(BORDER, acc, hv));
		ctext(g, label, bx + bw / 2f, by + 4, T_SETTING, cap ? acc : s.i() > 0 ? TEXT : DIM);
		zone(bx, by, bw, 16, "key", () -> {
			setFocused(null);
			capture = s;
			captureArmed = false;
		}, null, () -> {
			s.set(0);
			capture = null;
		});
	}

	/** While a key field waits, the next key press is taken (Esc cancels, Backspace / Delete clears). */
	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (capture != null) {
			int k = event.key();
			if (k != com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
				boolean clear = k == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE
						|| k == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE;
				capture.set(clear ? 0 : k);
			}
			capture = null;
			Features.flush();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Text field (e.g. the friends list). */
	private void textRow(GuiGraphics g, S s, int x, int y, int w) {
		int bw = Math.max(90, Math.min(200, w * 3 / 5));
		int bx = x + w - bw;
		if (editS == s) {
			editField(g, bx, y + 3, bw);
			return;
		}
		float hv = anim("tx:" + s.id, over(bx, y + 3, bw, 16) ? 0.6f : 0f, 14f);
		box(g, bx, y + 3, bw, 16, 5, mix(FIELD, CARD, hv), mix(BORDER, acc, hv));
		boolean empty = s.text().isEmpty();
		text(g, fit(empty ? s.hint : s.text(), bw - 12, T_SETTING), bx + 6, y + 7, T_SETTING, empty ? FAINT : TEXT);
		zone(bx, y + 3, bw, 16, "text", () -> startEdit(s, s.text(), 256));
	}

	// ---- colour picker

	private int pickerHeight(S s, int w) {
		return 4 + 64 + 6 + 8 + (s.alpha ? 14 : 0) + 6 + 14 + 8;
	}

	/** Colour: swatch + hex on the row; the picker (colour field, hue, alpha, presets) folds out below. */
	private void colorRow(GuiGraphics g, S s, int x, int y, int w) {
		int col = s.color();
		boolean open = picker == s;
		float pa = smooth(anim("pick:" + s.id, open ? 1f : 0f, 11f));
		// swatch
		int sx = x + w - 24;
		int sy = y + 4;
		float hv = anim("sw:" + s.id, open ? 1f : over(sx, sy, 24, 14) ? 0.6f : 0f, 14f);
		swatch(g, sx, sy, 24, 14, 4, col);
		TextureStudioScreen.outline(g, sx, sy, 24, 14, 4, mix(BORDER, acc, hv));
		zone(sx, sy, 24, 14, "swatch", () -> {
			picker = picker == s ? null : s;
			pickInit = false;
		});
		// hex (click to type)
		String hex = PinSettings.hex(col, s.alpha);
		int hw = Math.round(tw(hex, T_SETTING)) + 10;
		int hx = sx - 6 - hw;
		if (editS == s) {
			editField(g, hx - 10, y + 3, hw + 10);
		} else {
			float hh = anim("hex:" + s.id, over(hx, y + 3, hw, 15) ? 1f : 0f, 14f);
			if (hh > 0.01f) {
				rr(g, hx, y + 3, hw, 15, 4, alpha(FIELD, Math.round(255 * hh)));
			}
			text(g, hex, hx + 5, y + 7, T_SETTING, mix(DIM, acc, hh));
			zone(hx, y + 3, hw, 15, "hex", () -> startEdit(s, hex, 9));
		}
		if (pa <= 0.01f) {
			return;
		}
		int ph = Math.round(pickerHeight(s, w) * pa);
		pushClip(x - 2, y + 22, x + w + 2, y + 22 + ph);
		if (open) {
			if (!pickInit || col != pickLast) {
				pickH = TextureStudioScreen.hueOf(col);
				float r = ((col >> 16) & 255) / 255f;
				float gg = ((col >> 8) & 255) / 255f;
				float b = (col & 255) / 255f;
				float max = Math.max(r, Math.max(gg, b));
				float min = Math.min(r, Math.min(gg, b));
				pickV = max;
				pickS = max <= 0f ? 0f : (max - min) / max;
				pickLast = col;
				pickInit = true;
			}
		}
		float h = open ? pickH : TextureStudioScreen.hueOf(col);
		int top = y + 26;
		int svW = Math.min(w, 240);
		int svH = 64;
		// colour field: saturation to the right, brightness upwards
		int cell = 4;
		for (int cy = 0; cy < svH; cy += cell) {
			float v = 1f - (cy + cell / 2f) / svH;
			for (int cx = 0; cx < svW; cx += cell) {
				float sat = (cx + cell / 2f) / svW;
				FillBatch.fill(g, x + cx, top + cy, x + Math.min(svW, cx + cell), top + Math.min(svH, cy + cell),
						TextureStudioScreen.hsv(h, sat, v));
			}
		}
		TextureStudioScreen.outline(g, x - 1, top - 1, svW + 2, svH + 2, 2, BORDER);
		if (open) {
			int mxp = x + Math.round(pickS * svW);
			int myp = top + Math.round((1f - pickV) * svH);
			rr(g, mxp - 4, myp - 4, 9, 9, 4, 0x40000000);
			rr(g, mxp - 3, myp - 3, 7, 7, 3, WHITE);
			rr(g, mxp - 2, myp - 2, 5, 5, 2, col | 0xFF000000);
		}
		zone(x, top, svW, svH, "sv:" + s.id, null, (px, py) -> {
			pickS = Math.max(0f, Math.min(1f, (px - x) / (float) svW));
			pickV = Math.max(0f, Math.min(1f, 1f - (py - top) / (float) svH));
			applyPick(s);
		}, null);
		// hue bar
		int hy = top + svH + 6;
		for (int i = 0; i < svW; i += 3) {
			FillBatch.fill(g, x + i, hy, x + Math.min(svW, i + 3), hy + 8,
					TextureStudioScreen.hsv((i + 1.5f) / svW, 0.9f, 1f));
		}
		TextureStudioScreen.outline(g, x - 1, hy - 1, svW + 2, 10, 2, BORDER);
		int hk = x + Math.round(h * svW);
		rr(g, hk - 3, hy - 2, 6, 12, 2, 0x40000000);
		rr(g, hk - 2, hy - 1, 4, 10, 2, WHITE);
		zone(x, hy - 2, svW, 12, "hue:" + s.id, null, (px, py) -> {
			pickH = Math.max(0f, Math.min(0.999f, (px - x) / (float) svW));
			if (pickS < 0.02f && pickV > 0.98f) {
				pickS = 0.85f; // from white: give the hue something to show
			}
			applyPick(s);
		}, null);
		int ny = hy + 14;
		// alpha bar
		if (s.alpha) {
			for (int i = 0; i < svW; i += 4) {
				boolean dark = (i / 4) % 2 == 0;
				FillBatch.fill(g, x + i, ny, x + Math.min(svW, i + 4), ny + 4, dark ? 0xFFD5D9E2 : WHITE);
				FillBatch.fill(g, x + i, ny + 4, x + Math.min(svW, i + 4), ny + 8, dark ? WHITE : 0xFFD5D9E2);
				FillBatch.fill(g, x + i, ny, x + Math.min(svW, i + 4), ny + 8,
						alpha(col, Math.round(255f * (i + 2f) / svW)));
			}
			TextureStudioScreen.outline(g, x - 1, ny - 1, svW + 2, 10, 2, BORDER);
			int ak = x + Math.round((col >>> 24) / 255f * svW);
			rr(g, ak - 3, ny - 2, 6, 12, 2, 0x40000000);
			rr(g, ak - 2, ny - 1, 4, 10, 2, WHITE);
			zone(x, ny - 2, svW, 12, "al:" + s.id, null, (px, py) -> {
				int a = Math.round(255 * Math.max(0f, Math.min(1f, (px - x) / (float) svW)));
				s.setColor((a << 24) | (s.color() & 0xFFFFFF));
				pickLast = s.color();
			}, null);
			ny += 14;
		}
		// presets
		int n = ToolScreen.PRESETS.length;
		int ps = Math.max(8, Math.min(14, (svW - (n - 1) * 4) / n));
		for (int i = 0; i < n; i++) {
			int pc = ToolScreen.PRESETS[i];
			int pxx = x + i * (ps + 4);
			boolean ph2 = over(pxx - 1, ny + 5, ps + 2, ps + 2);
			rr(g, pxx, ny + 6, ps, ps, 4, pc);
			TextureStudioScreen.outline(g, pxx, ny + 6, ps, ps, 4, ph2 ? acc : BORDER);
			zone(pxx - 1, ny + 5, ps + 2, ps + 2, "preset", () -> {
				s.setColor((s.alpha ? s.color() & 0xFF000000 : 0xFF000000) | (pc & 0xFFFFFF));
				pickInit = false;
			});
		}
		popClip();
	}

	private void applyPick(S s) {
		int rgb = TextureStudioScreen.hsv(pickH, pickS, pickV) & 0xFFFFFF;
		int a = s.alpha ? s.color() & 0xFF000000 : 0xFF000000;
		s.setColor(a | rgb);
		pickLast = s.color();
	}

	/** Colour swatch; see-through colours sit on a small checker. */
	private static void swatch(GuiGraphics g, int x, int y, int w, int h, int r, int col) {
		if ((col >>> 24) < 255) {
			rr(g, x, y, w, h, r, 0xFFFFFFFF);
			for (int i = 2; i < w - 2; i += 4) {
				boolean top = (i / 4) % 2 == 0;
				FillBatch.fill(g, x + i, y + (top ? 2 : h / 2), x + Math.min(w - 2, i + 4), y + (top ? h / 2 : h - 2),
						0xFFD5D9E2);
			}
		}
		rr(g, x, y, w, h, r, col);
	}

	// ---- typing a value (number, hex, text)

	/** Frame of the edit box for this frame; the box itself is drawn after the rest. */
	private void editField(GuiGraphics g, int x, int y, int w) {
		box(g, x, y, w, 16, 5, CARD, acc);
		editX = x + 5;
		editY = y + 4;
		editW = Math.max(10, w - 10);
		int[] c = clip();
		editSeen = c == null || (y >= c[1] && y + 16 <= c[3]);
	}

	private void startEdit(S s, String value, int maxLen) {
		stopEdit();
		EditBox b = new EditBox(font, 0, 0, 180, 12, Component.literal(s.name));
		b.setBordered(false);
		b.setTextShadow(false);
		b.setTextColor(TEXT);
		b.setMaxLength(maxLen);
		b.setValue(value);
		b.setResponder(v -> applyEdit(s, v));
		addWidget(b);
		setFocused(b);
		edit = b;
		editS = s;
		editSeen = true;
		popup = null;
		capture = null;
	}

	/** Live: every valid input applies at once. */
	private void applyEdit(S s, String v) {
		switch (s.type) {
			case NUM -> {
				try {
					s.set(Double.parseDouble(v.trim().replace(',', '.')));
				} catch (NumberFormatException ignored) {
					// not a number yet
				}
			}
			case COLOR -> {
				int old = s.color();
				int c = PinSettings.parseHex(v, old);
				if (c != old) {
					s.setColor(s.alpha ? c : c | 0xFF000000);
					pickInit = false;
				}
			}
			case TEXT -> s.setText(v);
			default -> {
			}
		}
	}

	private void stopEdit() {
		if (edit != null) {
			removeWidget(edit);
			if (getFocused() == edit) {
				setFocused(null);
			}
			edit = null;
			editS = null;
			Features.flush();
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing || animating) {
			return true;
		}
		int px = (int) event.x();
		int py = (int) event.y();
		boolean left = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
		boolean right = event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;
		if (!left && !right) {
			return super.mouseClicked(event, doubleClick);
		}
		if (left) {
			leftDown = true;
		}
		// a click into the open edit box only moves its cursor
		if (edit != null && left && px >= editX - 5 && px < editX + editW + 5 && py >= editY - 4 && py < editY + 12) {
			return super.mouseClicked(event, doubleClick);
		}
		stopEdit();
		if (capture != null) {
			capture = null;
		}
		if (popup != null) {
			Zone z = find(popupZones, px, py);
			if (z != null && left && z.click() != null) {
				z.click().run();
			} else {
				popup = null;
			}
			return true;
		}
		Zone z = find(zones, px, py);
		if (z == null) {
			setFocused(null);
			return true;
		}
		if (right) {
			if (z.right() != null) {
				z.right().run();
				Features.flush();
			}
			return true;
		}
		if (!"search".equals(z.key())) {
			setFocused(null);
		}
		active = z;
		activeKey = z.key();
		if (z.click() != null) {
			z.click().run();
		}
		if (z.drag() != null) {
			z.drag().at(px, py);
		}
		if ("search".equals(z.key())) {
			super.mouseClicked(event, doubleClick); // cursor position in the search field
		}
		return true;
	}

	private static Zone find(List<Zone> list, int px, int py) {
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).inside(px, py)) {
				return list.get(i);
			}
		}
		return null;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			leftDown = false;
			active = null;
			activeKey = "";
			Features.flush(); // write the config files once the mouse is released
		}
		return super.mouseReleased(event);
	}

	/** Mouse wheel: smooth scrolling (no @Override: the signature differs between versions). */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (popup != null) {
			popup = null;
		}
		if (active != null) {
			return true;
		}
		if (narrow ? showDetail : mouseX >= detX) {
			int dmax = Math.max(0, dContentH - dH);
			dTarget = (float) Math.max(0, Math.min(dmax, dTarget - scrollY * 30));
			return true;
		}
		int max = Math.max(0, contentH - listH);
		scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - scrollY * 30));
		return true;
	}
}
