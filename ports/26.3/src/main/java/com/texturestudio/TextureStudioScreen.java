package com.texturestudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;

/**
 * Pin Visuals im dunklen "Styles"-Look: Sammlungen links (mit Item-Icons), Stil-Karten in der Mitte,
 * Inspector/Themes rechts, Loadout-Leiste oben, Aktionsleiste unten.
 * Ein Klick auf eine Karte wendet den Stil sofort an, Rechtsklick zoomt.
 *
 * Alle Klickflaechen sind normale (unsichtbar uebermalte) Buttons; gezeichnet wird alles selbst.
 */
public class TextureStudioScreen extends Screen {

	// ---- Palette (dunkel)
	// ---- Palette: wird je nach Modus (hell = weiss/blau, dunkel) in applyPalette() gesetzt
	private int WIN;
	private int SIDEBAR;
	private int PANEL;
	private int CARD;
	private int CARD_HI;
	private int SEL;
	private int FIELD;
	private int PLATE_TOP;
	private int PLATE_TOP_HI;
	private int PLATE_BOTTOM;
	private int PLATE;
	private int PLATE_CHECK;
	private int LINE;
	private int BORDER;
	private int BORDER_HI;
	private int TEXT;
	private int SOFT;
	private int DIM;
	private int FAINT;
	private int HOVER; // Grundfarbe fuer Hover-Schleier
	private int TRACK; // Schienen von Reglern/Scrollleisten
	private int DIMMER; // Abdunklung hinter dem Fenster
	private static final int GREEN = 0xFF22C55E;
	private static final int GOLD = 0xFFF5B82E;
	private static final int RED = 0xFFEF4444;
	private static final String TITLE = "Pin Visuals";
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID,
			"textures/gui/logo.png");

	/** Colour theme: a three-colour gradient (color -> c2 -> c3); glassy = see-through frosted window. */
	private record Theme(String id, String name, int color, int c2, int c3, boolean glassy) {
	}

	private static final List<Theme> THEMES = List.of(
			new Theme("blue", "Ocean", 0xFF2563EB, 0xFF06B6D4, 0xFF22D3EE, false),
			new Theme("glass", "Glass", 0xFF7DD3FC, 0xFFA5B4FC, 0xFFF0ABFC, true),
			new Theme("halloween", "Sunset", 0xFFF97316, 0xFFEC4899, 0xFF8B5CF6, false),
			new Theme("crimson", "Crimson", 0xFFEF4444, 0xFFF43F5E, 0xFFBE185D, false),
			new Theme("amethyst", "Nebula", 0xFF9B5CFF, 0xFFD946EF, 0xFF6366F1, false),
			new Theme("amber", "Citrus", 0xFFF59E0B, 0xFFFACC15, 0xFF84CC16, false),
			new Theme("sakura", "Sakura", 0xFFF472B6, 0xFFC084FC, 0xFF818CF8, false),
			new Theme("aurora", "Aurora", 0xFF34D399, 0xFF22D3EE, 0xFFA78BFA, false));

	/** t = weich animierter Hover-Wert (0..1), hover = Maus liegt gerade darauf. */
	private interface Painter {
		void paint(GuiGraphicsExtractor g, float t, boolean hover);
	}

	private record Hit(Button button, Painter painter) {
	}

	private record CardRect(int x, int y, int w, int h, PackInfo pack) {
	}

	// ---- Rundungs-Tabellen: INS[r][i] = Einzug der i-ten Zeile bei Radius r
	private static final int[][] INS = new int[9][];

	static {
		for (int r = 0; r < INS.length; r++) {
			int[] a = new int[r];
			for (int i = 0; i < r; i++) {
				double dy = r - i - 0.5;
				double dx = Math.sqrt(r * r - dy * dy);
				a[i] = (int) Math.round(r - dx);
			}
			if (r >= 2 && a[0] < 1) {
				a[0] = 1;
			}
			INS[r] = a;
		}
	}

	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	private final Screen parent;
	private final MixConfig config = MixConfig.load();
	private final PreviewCache previews = new PreviewCache();
	private final String version = FabricLoader.getInstance().getModContainer(TextureStudioClient.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	private PackIndex index;

	private final List<AbstractWidget> dynamic = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private final List<CardRect> cards = new ArrayList<>();
	private final Map<Long, Float> anim = new HashMap<>();
	private long lastNanos = System.nanoTime();
	private float dt = 0.016f;
	private EditBox search;
	private boolean searchAdded;
	/** Gewaehlte Menue-Schrift (siehe {@link MenuFonts}). */
	private Font uiFont;

	// Zustand
	private boolean themesTab;
	private boolean favoritesOnly;
	private boolean zoom;
	private PackInfo zoomPack;
	private String groupId = "crystal";
	private String focusedPack; // null = Vanilla
	private int gridScroll;
	private int listScroll;
	private String status = "";
	private boolean statusError;
	private long statusAt;
	/** 0 = nichts, 1 = Farbton-Regler, 2 = Deckkraft-Regler */
	private int dragging;

	// Pack-Sammlung (Dialog ueber dem Fenster)
	private enum Modal {
		NONE, OFFER, SELECT, PROGRESS, DONE, OPTIONS, SETTINGS
	}

	private Modal modal = Modal.NONE;
	private final List<Hit> modalHits = new ArrayList<>();
	private boolean buildingModal;
	private final Set<String> picked = new HashSet<>();
	private int pickScroll;
	private int pickRows;
	private PackLibrary.Job job;
	private boolean jobHandled;
	private int mX;
	private int mY;
	private int mW;
	private int mH;

	// pro Frame
	private int mx;
	private int my;
	private boolean hoverSet;
	private PackInfo hoverPack;

	// Layout: sidebar (nav) left, header + collection rail on top, preview panel + grid panel, loadout dock below
	private static final int HEAD_H = 24;
	private static final int SEARCH_H = 18;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int navX;
	private int navY;
	private int navW;
	private int navH;
	private boolean navLabels;
	private int mainX;
	private int mainR;
	private int mainW;
	private int headY;
	private int railY;
	private int railH;
	private boolean railCompact;
	private int dockY;
	private int dockH;
	private int prevPanelX;
	private int previewW;
	private int cx;
	private int cw;
	private int bodyTop;
	private int bodyBottom;
	private int searchX;
	private int searchY;
	private int searchW;
	private int gridPanelTop;
	private int gridPanelBottom;
	/** Collection rail: 0 = sets, 1 = extras, 2 = loadout (only equipped), 3 = cosmetics. */
	private int listMode;
	/** Tweaks dialog tab: 0 = Visuals, 1 = FPS. */
	private int optionsTab;

	// Raster-Geometrie
	private int gridTop;
	private int totalRows;
	private int rowsVisible;

	// Preview geometry (set in buildPreview, used by drawInspector)
	private int stageX;
	private int stageY;
	private int stageW;
	private int stageH;
	private int nameY;
	private int sliderTop;
	private boolean showSliders;

	// Regler-Geometrie (Themes)
	private int hueX;
	private int hueY;
	private int hueW;
	private int opX;
	private int opY;
	private int opW;

	public TextureStudioScreen(Screen parent) {
		super(Component.literal(TITLE));
		this.parent = parent;
	}

	// ================================================================== Farb-Helfer

	/** Farbthema fuer das ganze Menue: hell oder dunkel, getoent mit einer Grundfarbe; dazu ein passender Akzent. */
	private record MenuTheme(String id, String name, boolean light, int tint, String accent) {
	}

	private static final List<MenuTheme> MENU_THEMES = List.of(
			new MenuTheme("weiss", "White", true, 0xFF2563EB, "blue"),
			new MenuTheme("grau", "Gray", true, 0xFF64748B, "blue"),
			new MenuTheme("rosa", "Pink", true, 0xFFEC4899, "sakura"),
			new MenuTheme("mint", "Mint", true, 0xFF10B981, "aurora"),
			new MenuTheme("dunkel", "Dark", false, 0xFF6B7280, "sakura"),
			new MenuTheme("navy", "Navy", false, 0xFF2563EB, "blue"),
			new MenuTheme("lila", "Nebula", false, 0xFF7C3AED, "amethyst"),
			new MenuTheme("wald", "Forest", false, 0xFF16A34A, "amber"),
			new MenuTheme("glut", "Ember", false, 0xFFDC2626, "halloween"));

	private MenuTheme menuTheme() {
		String id = config.menuTheme();
		for (MenuTheme m : MENU_THEMES) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		return MENU_THEMES.get(0);
	}

	private int WIN_BORDER;

	private int paletteKey;

	/** The colour theme tints the whole menu: rebuild the palette when it changed. */
	private void ensurePalette() {
		int key = accent() * 31 + acc3();
		if (key != paletteKey) {
			applyPalette();
		}
	}

	private void applyPalette() {
		MenuTheme m = menuTheme();
		paletteKey = accent() * 31 + acc3();
		// the whole menu is tinted by the colour theme (its first and last gradient colour)
		int c = mix(accent(), acc3(), 0.4f);
		if (m.light()) {
			int w = 0xFFFFFFFF;
			WIN = mix(w, c, 0.012f);
			SIDEBAR = mix(w, c, 0.045f);
			PANEL = mix(w, c, 0.035f);
			CARD = w;
			CARD_HI = mix(w, c, 0.06f);
			SEL = mix(w, c, 0.12f);
			FIELD = mix(w, c, 0.05f);
			PLATE_TOP = mix(w, c, 0.15f);
			PLATE_TOP_HI = mix(w, c, 0.23f);
			PLATE_BOTTOM = mix(w, c, 0.03f);
			PLATE = mix(w, c, 0.05f);
			PLATE_CHECK = mix(w, c, 0.10f);
			LINE = mix(0xFFE5E7EB, c, 0.08f);
			BORDER = mix(0xFFD8DCE3, c, 0.14f);
			BORDER_HI = mix(0xFF9AA3B5, c, 0.40f);
			TEXT = mix(0xFF0F172A, c, 0.08f);
			SOFT = mix(0xFF334155, c, 0.10f);
			DIM = mix(0xFF64748B, c, 0.10f);
			FAINT = mix(0xFF94A3B8, c, 0.12f);
			HOVER = mix(c, 0xFF000000, 0.4f);
			TRACK = mix(0xFFE2E6EE, c, 0.12f);
			DIMMER = 0x66000000;
			WIN_BORDER = mix(0xFFCBD5E4, c, 0.18f);
		} else {
			int d = 0xFF08070E;
			WIN = mix(d, c, 0.10f);
			SIDEBAR = mix(d, c, 0.13f);
			PANEL = mix(d, c, 0.12f);
			CARD = mix(d, c, 0.17f);
			CARD_HI = mix(d, c, 0.25f);
			SEL = mix(d, c, 0.30f);
			FIELD = mix(d, c, 0.14f);
			PLATE_TOP = mix(d, c, 0.30f);
			PLATE_TOP_HI = mix(d, c, 0.38f);
			PLATE_BOTTOM = mix(d, c, 0.08f);
			PLATE = mix(d, c, 0.12f);
			PLATE_CHECK = mix(d, c, 0.16f);
			LINE = mix(d, c, 0.22f);
			BORDER = mix(0xFF22202E, c, 0.30f);
			BORDER_HI = mix(0xFF3C3A50, c, 0.50f);
			TEXT = 0xFFF3F0FF;
			SOFT = mix(0xFFD0CCE0, c, 0.10f);
			DIM = mix(0xFF9C98B0, c, 0.15f);
			FAINT = mix(0xFF6A6680, c, 0.20f);
			HOVER = 0xFFFFFFFF;
			TRACK = mix(0xFF262438, c, 0.25f);
			DIMMER = 0x99000000;
			WIN_BORDER = mix(0xFF1F1D29, c, 0.40f);
		}
	}

	private int accent() {
		if ("custom".equals(config.accent)) {
			return 0xFF000000 | (config.customColor & 0xFFFFFF);
		}
		for (Theme t : THEMES) {
			if (t.id().equals(config.accent)) {
				return t.color();
			}
		}
		return THEMES.get(0).color();
	}

	/** The chosen colour theme, or null for a custom colour. */
	private Theme theme() {
		if ("custom".equals(config.accent)) {
			return null;
		}
		for (Theme t : THEMES) {
			if (t.id().equals(config.accent)) {
				return t;
			}
		}
		return THEMES.get(0);
	}

	/** Third gradient colour of the theme. */
	private int acc3() {
		Theme t = theme();
		return t != null ? t.c3() : hsv(hueOf(accent()) + 0.18f, 0.6f, 1f);
	}

	/** Window opacity in percent; the glass theme is always see-through. */
	private int opacity() {
		Theme t = theme();
		return t != null && t.glassy() ? Math.min(config.opacity, 55) : config.opacity;
	}

	/** Textfarbe, die auf der Akzentfarbe gut lesbar ist. */
	private int onAccent() {
		int c = accent();
		double lum = 0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255);
		return lum > 140 ? 0xFF0B0C10 : 0xFFFFFFFF;
	}

	/** Fensterfarbe mit der eingestellten Deckkraft. */
	private int win(int argb) {
		return alpha(argb, opacity() * 255 / 100);
	}

	private static int alpha(int argb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
		int rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (aa << 24) | (rr << 16) | (gg << 8) | bb;
	}

	private static int hsv(float h, float s, float v) {
		h = ((h % 1f) + 1f) % 1f;
		float r;
		float g;
		float b;
		int i = (int) (h * 6f);
		float f = h * 6f - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	private static float hueOf(int c) {
		float r = ((c >> 16) & 255) / 255f;
		float g = ((c >> 8) & 255) / 255f;
		float b = (c & 255) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		if (d < 1e-4f) {
			return 0f;
		}
		float h;
		if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		return h < 0 ? h + 1f : h;
	}

	// ================================================================== Zeichen-Helfer

	/** Gefuelltes Rechteck mit abgerundeten Ecken. */
	private static void rrect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(Math.min(w, h) / 2, INS.length - 1)));
		if (r == 0) {
			FillBatch.fill(g, x, y, x + w, y + h, color);
			return;
		}
		int[] ins = INS[r];
		for (int i = 0; i < r; i++) {
			FillBatch.fill(g, x + ins[i], y + i, x + w - ins[i], y + i + 1, color);
			FillBatch.fill(g, x + ins[i], y + h - 1 - i, x + w - ins[i], y + h - i, color);
		}
		FillBatch.fill(g, x, y + r, x + w, y + h - r, color);
	}

	/** Abgerundetes Rechteck mit 1px Rahmen. */
	private static void rbox(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
		rrect(g, x, y, w, h, r, border);
		rrect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
	}

	/**
	 * Senkrechter Verlauf, oben/unten wahlweise abgerundet. Only the rounded rows are drawn one by one; the straight
	 * middle part is drawn in a few colour bands (every separate rectangle costs time in the GUI renderer).
	 */
	private static void vgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int rTop, int rBottom, int c1,
			int c2) {
		if (w <= 0 || h <= 0) {
			return;
		}
		rTop = Math.max(0, Math.min(rTop, Math.min(INS.length - 1, h / 2)));
		rBottom = Math.max(0, Math.min(rBottom, Math.min(INS.length - 1, h / 2)));
		float div = h <= 1 ? 1f : h - 1;
		for (int row = 0; row < rTop; row++) {
			int in = INS[rTop][row];
			FillBatch.fill(g, x + in, y + row, x + w - in, y + row + 1, mix(c1, c2, row / div));
		}
		for (int row = h - rBottom; row < h; row++) {
			int in = INS[rBottom][h - 1 - row];
			FillBatch.fill(g, x + in, y + row, x + w - in, y + row + 1, mix(c1, c2, row / div));
		}
		int mid = h - rTop - rBottom;
		if (mid <= 0) {
			return;
		}
		int bands = c1 == c2 ? 1 : Math.max(1, Math.min(10, mid / 8));
		for (int b = 0; b < bands; b++) {
			int y0 = rTop + mid * b / bands;
			int y1 = rTop + mid * (b + 1) / bands;
			FillBatch.fill(g, x, y + y0, x + w, y + y1, mix(c1, c2, (y0 + y1) / 2f / div));
		}
	}

	/** Weicher Schlagschatten aus mehreren fast durchsichtigen Lagen. */
	private static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int layers, int a) {
		for (int i = layers; i >= 1; i--) {
			rrect(g, x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, alpha(0x000000, a));
		}
	}

	private static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int layers, int color, int a) {
		for (int i = layers; i >= 1; i--) {
			rrect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, alpha(color, a));
		}
	}

	/** Kleiner Pixel-Stern (5x5). */
	private static void star(GuiGraphicsExtractor g, int cx, int cy, int color) {
		FillBatch.fill(g, cx, cy - 2, cx + 1, cy, color);
		FillBatch.fill(g, cx - 2, cy - 1, cx + 3, cy + 1, color);
		FillBatch.fill(g, cx - 1, cy + 1, cx + 2, cy + 2, color);
		FillBatch.fill(g, cx - 2, cy + 2, cx - 1, cy + 3, color);
		FillBatch.fill(g, cx + 2, cy + 2, cx + 3, cy + 3, color);
	}

	private static void dot(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		rrect(g, x, y, size, size, size / 2, color);
	}

	// ---- 7x7 pixel icons ('#' = pixel)
	private static final String[] I_STAR = { "...#...", "...#...", "..###..", "#######", ".#####.", "..#.#..", ".#...#." };
	private static final String[] I_GEAR = { "..#.#..", ".#####.", "##...##", ".#...#.", "##...##", ".#####.", "..#.#.." };
	private static final String[] I_FOLDER = { "###....", "#######", "#.....#", "#.....#", "#.....#", "#######", "......." };
	private static final String[] I_CLOSE = { "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#" };
	private static final String[] I_HEART = { ".##.##.", "#######", "#######", ".#####.", "..###..", "...#...", "......." };
	private static final String[] I_HEART_O = { ".##.##.", "#..#..#", "#.....#", ".#...#.", "..#.#..", "...#...", "......." };
	private static final String[] I_CHECK = { "......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", "......." };
	private static final String[] I_GRID = { "###.###", "###.###", "###.###", ".......", "###.###", "###.###", "###.###" };
	private static final String[] I_EXTRAS = { "...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..." };
	private static final String[] I_LAYERS = { "#######", "#.....#", "#######", ".......", "#######", "#.....#", "#######" };
	private static final String[] I_LIST = { "#.#####", ".......", "#.#####", ".......", "#.#####", ".......", "......." };
	private static final String[] I_SEARCH = { ".###...", "#...#..", "#...#..", "#...#..", ".####..", "....##.", ".....##" };
	private static final String[] I_RESET = { "..###.#", ".#...##", "#...###", "#......", "#.....#", ".#...#.", "..###.." };
	private static final String[] I_DOWN = { "...#...", "...#...", "...#...", "#..#..#", ".#.#.#.", "..###..", "#######" };

	/** Pixel icon from a pattern ('#' = pixel). */
	private static void glyph(GuiGraphicsExtractor g, String[] rows, int x, int y, int color) {
		for (int r = 0; r < rows.length; r++) {
			String s = rows[r];
			int c = 0;
			while (c < s.length()) {
				if (s.charAt(c) == '#') {
					int e = c;
					while (e < s.length() && s.charAt(e) == '#') {
						e++;
					}
					FillBatch.fill(g, x + c, y + r, x + e, y + r + 1, color);
					c = e;
				} else {
					c++;
				}
			}
		}
	}

	/** Horizontal gradient with rounded corners. */
	private static void hgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c1, int c2) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(INS.length - 1, Math.min(w, h) / 2)));
		float div = w <= 1 ? 1f : w - 1;
		for (int i = 0; i < r; i++) {
			int in = INS[r][i];
			FillBatch.fill(g, x + i, y + in, x + i + 1, y + h - in, mix(c1, c2, i / div));
			int j = w - 1 - i;
			FillBatch.fill(g, x + j, y + in, x + j + 1, y + h - in, mix(c1, c2, j / div));
		}
		int mid = w - 2 * r;
		if (mid <= 0) {
			return;
		}
		int bands = c1 == c2 ? 1 : Math.max(1, Math.min(12, mid / 8));
		for (int b = 0; b < bands; b++) {
			int x0 = r + mid * b / bands;
			int x1 = r + mid * (b + 1) / bands;
			FillBatch.fill(g, x + x0, y, x + x1, y + h, mix(c1, c2, (x0 + x1) / 2f / div));
		}
	}

	/** Horizontal gradient through three colours (c1 -> c2 -> c3) with rounded ends. */
	private static void hgrad3(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c1, int c2, int c3) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(INS.length - 1, Math.min(w, h) / 2)));
		float div = w <= 1 ? 1f : w - 1;
		for (int i = 0; i < w; i++) {
			int in = i < r ? INS[r][i] : i >= w - r ? INS[r][w - 1 - i] : 0;
			float f = i / div;
			int c = f < 0.5f ? mix(c1, c2, f * 2f) : mix(c2, c3, (f - 0.5f) * 2f);
			FillBatch.fill(g, x + i, y + in, x + i + 1, y + h - in, c);
		}
	}

	/** Gradient swatch of a colour theme (a frosted pane with a sheen for the glass theme). */
	private void swatch(GuiGraphicsExtractor g, int x, int y, int s, int r, Theme th) {
		hgrad3(g, x, y, s, s, r, th.color(), th.c2(), th.c3());
		FillBatch.fill(g, x + Math.max(1, r / 2), y + 1, x + s - Math.max(1, r / 2), y + 2, alpha(0xFFFFFF, 0x60));
		if (th.glassy()) {
			FillBatch.fill(g, x + 2, y + s - 3, x + s / 2, y + s - 2, alpha(0xFFFFFF, 0x50));
			outline(g, x, y, s, s, r, alpha(0xFFFFFF, 0xA0));
		}
	}

	/** 1px rounded outline only (so translucent fills never show a border colour through them). */
	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 1 || h <= 1) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(INS.length - 1, Math.min(w, h) / 2)));
		if (r == 0) {
			FillBatch.fill(g, x, y, x + w, y + 1, color);
			FillBatch.fill(g, x, y + h - 1, x + w, y + h, color);
			FillBatch.fill(g, x, y + 1, x + 1, y + h - 1, color);
			FillBatch.fill(g, x + w - 1, y + 1, x + w, y + h - 1, color);
			return;
		}
		int[] ins = INS[r];
		FillBatch.fill(g, x + ins[0], y, x + w - ins[0], y + 1, color);
		FillBatch.fill(g, x + ins[0], y + h - 1, x + w - ins[0], y + h, color);
		for (int i = 1; i < r; i++) {
			int a = ins[i];
			int b = Math.max(a + 1, ins[i - 1]);
			FillBatch.fill(g, x + a, y + i, x + b, y + i + 1, color);
			FillBatch.fill(g, x + w - b, y + i, x + w - a, y + i + 1, color);
			FillBatch.fill(g, x + a, y + h - 1 - i, x + b, y + h - i, color);
			FillBatch.fill(g, x + w - b, y + h - 1 - i, x + w - a, y + h - i, color);
		}
		FillBatch.fill(g, x, y + r, x + 1, y + h - r, color);
		FillBatch.fill(g, x + w - 1, y + r, x + w, y + h - r, color);
	}

	/** Filled ellipse. */
	private static void ellipse(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color) {
		if (rx <= 0 || ry <= 0) {
			return;
		}
		// big ellipses are built from strips a few pixels tall (far fewer rectangles, looks the same for soft lights)
		int step = Math.max(1, ry / 6);
		for (int dy = -ry; dy < ry; dy += step) {
			int h = Math.min(step, ry - dy);
			double yy = (dy + h / 2.0) / ry;
			int span = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - yy * yy)));
			if (span > 0) {
				FillBatch.fill(g, cx - span, cy + dy, cx + span, cy + dy + h, color);
			}
		}
	}

	/** Soft round light: two stacked translucent ellipses. */
	private static void halo(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color, int a) {
		ellipse(g, cx, cy, rx, ry, alpha(color, a));
		ellipse(g, cx, cy, Math.round(rx * 0.6f), Math.round(ry * 0.6f), alpha(color, a));
	}

	/** Thin elliptic ring (orbit) made of single pixels. */
	private static void orbit(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, float rot, int color) {
		int n = Math.max(24, (rx + ry) * 3 / 2);
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			double ex = Math.cos(a) * rx;
			double ey = Math.sin(a) * ry;
			double c = Math.cos(rot);
			double s = Math.sin(rot);
			int x = (int) Math.round(cx + ex * c - ey * s);
			int y = (int) Math.round(cy + ex * s + ey * c);
			FillBatch.fill(g, x, y, x + 1, y + 1, color);
		}
	}

	/** Rainbow bar for the accent slider, in short segments with rounded ends. */
	private static void hueBar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int r = Math.min(3, h / 2);
		for (int i = 0; i < r; i++) {
			int in = INS[r][i];
			FillBatch.fill(g, x + i, y + in, x + i + 1, y + h - in, hsv(i / (float) w, 0.72f, 1f));
			int j = w - 1 - i;
			FillBatch.fill(g, x + j, y + in, x + j + 1, y + h - in, hsv(j / (float) w, 0.72f, 1f));
		}
		int seg = 4;
		for (int i = r; i < w - r; i += seg) {
			int e = Math.min(w - r, i + seg);
			FillBatch.fill(g, x + i, y, x + e, y + h, hsv((i + e) / 2f / w, 0.72f, 1f));
		}
	}

	/** Small 4-point sparkle. */
	private static void sparkle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		FillBatch.fill(g, cx, cy - r, cx + 1, cy + r + 1, color);
		FillBatch.fill(g, cx - r, cy, cx + r + 1, cy + 1, color);
		if (r >= 3) {
			FillBatch.fill(g, cx - 1, cy - 1, cx + 2, cy + 2, color);
		}
	}

	/** Glassy panel: faint accent glow, gradient fill and a 1px accent-tinted outline. */
	private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float accentAmount) {
		int acc = accent();
		glow(g, x, y, w, h, r, 1, acc, 0x10);
		vgrad(g, x + 1, y + 1, w - 2, h - 2, r - 1, r - 1, win(mix(PANEL, acc, 0.06f)), win(PANEL));
		outline(g, x, y, w, h, r, mix(BORDER, acc, accentAmount));
	}

	private void textShadow(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		FillBatch.flush(g).text(uiFont, s, x, y, color, !menuTheme().light());
	}

	private void ctextShadow(GuiGraphicsExtractor g, String s, int centerX, int y, int color) {
		textShadow(g, s, centerX - uiFont.width(s) / 2, y, color);
	}

	/** Scaled text with a shadow on dark themes. */
	private void bigShadow(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		FillBatch.flush(g).text(uiFont, s, 0, 0, color, !menuTheme().light());
		g.pose().popMatrix();
	}

	private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		FillBatch.flush(g).text(uiFont, s, x, y, color, false);
	}

	private void bold(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		FillBatch.flush(g).text(uiFont, s, x, y, color, false);
		if (!MenuFonts.smooth(config.font)) {
			FillBatch.flush(g).text(uiFont, s, x + 1, y, color, false);
		}
	}

	private void ctext(GuiGraphicsExtractor g, String s, int centerX, int y, int color) {
		FillBatch.flush(g).text(uiFont, s, centerX - uiFont.width(s) / 2, y, color, false);
	}

	private void rtext(GuiGraphicsExtractor g, String s, int rightX, int y, int color) {
		FillBatch.flush(g).text(uiFont, s, rightX - uiFont.width(s), y, color, false);
	}

	/** Vergroesserter Text (z. B. Ueberschriften). */
	private void bigText(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		FillBatch.flush(g).text(uiFont, s, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private String fit(String s, int max) {
		if (uiFont.width(s) <= max) {
			return s;
		}
		while (s.length() > 1 && uiFont.width(s + "...") > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** Reiter-Ueberschrift mit Unterstrich in Akzentfarbe, wenn aktiv. */
	private void tab(GuiGraphicsExtractor g, String label, int x, int y, boolean active, float t) {
		int c = active ? accent() : mix(DIM, TEXT, t);
		text(g, label, x, y, c);
		if (active) {
			FillBatch.fill(g, x, y + 11, x + uiFont.width(label), y + 12, accent());
		}
	}

	/** Dunkler Knopf mit Text. */
	private void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, float t, int border,
			int textColor) {
		rbox(g, x, y, w, h, 3, mix(FIELD, CARD_HI, t), mix(border, BORDER_HI, t));
		ctext(g, label, x + w / 2, y + (h - 8) / 2, textColor);
	}

	/** Item fuer eine Sammlung; null, solange die Item-Komponenten noch nicht geladen sind (z. B. im Hauptmenue). */
	private static ItemStack iconStack(String groupId) {
		ItemStack cached = ICONS.get(groupId);
		if (cached != null) {
			return cached;
		}
		try {
			ItemStack s = createIcon(groupId);
			ICONS.put(groupId, s);
			return s;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static ItemStack createIcon(String groupId) {
		{
			String id = groupId;
			Item item = switch (id) {
				case "crystal" -> Items.END_CRYSTAL;
				case "fx_kill" -> Items.WITHER_SKELETON_SKULL;
				case "fx_lyrics" -> Items.JUKEBOX;
				case "fx_armor" -> Items.NETHERITE_HELMET;
				case "fx_spotify" -> Items.MUSIC_DISC_CAT;
				case "fx_hit" -> Items.IRON_SWORD;
				case "fx_pop" -> Items.TOTEM_OF_UNDYING;
				case "obsidian" -> Items.OBSIDIAN;
				case "anchor" -> Items.RESPAWN_ANCHOR;
				case "armor" -> Items.DIAMOND_CHESTPLATE;
				case "tools" -> Items.DIAMOND_SWORD;
				case "gaps" -> Items.GOLDEN_APPLE;
				case "totem" -> Items.TOTEM_OF_UNDYING;
				case "hud" -> Items.CLOCK;
				case "inventory" -> Items.CHEST;
				case "fire" -> Items.FIRE_CHARGE;
				case "sky" -> Items.SUNFLOWER;
				case "deepslate" -> Items.DEEPSLATE;
				case "stone" -> Items.STONE;
				case "snow" -> Items.SNOW_BLOCK;
				case "grass" -> Items.GRASS_BLOCK;
				case "shield" -> Items.SHIELD;
				case "mace" -> Items.MACE;
				case "cobwebs" -> Items.COBWEB;
				case "trident" -> Items.TRIDENT;
				case "windcharge" -> Items.WIND_CHARGE;
				case "xpbottle" -> Items.EXPERIENCE_BOTTLE;
				case "cos_cape" -> Items.BANNER.blue();
				case "cos_hat" -> Items.GOLDEN_HELMET;
				case "cos_wings" -> Items.ELYTRA;
				case "x_block" -> Items.DIRT;
				case "x_item" -> Items.DIAMOND;
				case "x_entity" -> Items.ZOMBIE_HEAD;
				case "x_gui" -> Items.PAINTING;
				case "x_particle" -> Items.BLAZE_POWDER;
				default -> Items.BOOK;
			};
			return new ItemStack(item);
		}
	}

	/** Item-Icon einer Sammlung in beliebiger Groesse. */
	private void icon(GuiGraphicsExtractor g, Group gr, int x, int y, int size) {
		ItemStack stack = iconStack(gr.id());
		if (stack == null) {
			int in = Math.max(1, size / 6);
			rrect(g, x + in, y + in, size - 2 * in, size - 2 * in, 2, gr.color());
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		float s = size / 16f;
		g.pose().scale(s, s);
		FillBatch.flush(g).fakeItem(stack, 0, 0);
		g.pose().popMatrix();
	}

	// ================================================================== Daten-Helfer

	private Group group() {
		return Group.byId(groupId);
	}

	private String activeOf(Group gr) {
		String id = config.styles.get(gr.id());
		if (id == null) {
			return null;
		}
		PackInfo p = index.byId(id);
		return p != null && !index.textures(gr.id(), p).isEmpty() ? id : null;
	}

	/** Ist in dieser Sammlung etwas ausgewaehlt (Stil aus einem Pack bzw. getragenes Cosmetic)? */
	private boolean isOn(Group gr) {
		Cosmetics.Kind k = gr.cosmetic();
		if (k != null) {
			return !Cosmetics.NONE.equals(Cosmetics.worn(k));
		}
		return activeOf(gr) != null;
	}

	/** Anzahl der Eintraege (inkl. Vanilla bzw. "Keins"). */
	private int count(Group gr) {
		Cosmetics.Kind k = gr.cosmetic();
		return k != null ? Cosmetics.list(k).size() + 1 : index.styles(gr.id()).size() + 1;
	}

	private String packName(String id) {
		if (id == null) {
			return "Vanilla";
		}
		PackInfo p = index.byId(id);
		return p == null ? id : p.displayName();
	}

	private String favKey(Group gr, String packId) {
		return gr.id() + "|" + packId;
	}

	private int favCount(Group gr) {
		int n = 0;
		for (PackInfo p : index.styles(gr.id())) {
			if (config.favorites.contains(favKey(gr, p.id()))) {
				n++;
			}
		}
		return n;
	}

	private Hit hit(int x, int y, int w, int h, Runnable action, Painter painter) {
		Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, h).build();
		dynamic.add(b);
		addWidget(b); // input only: everything is drawn by the painter, the vanilla button would be painted over anyway
		if (buildingModal) {
			Hit mh = new Hit(b, painter);
			modalHits.add(mh);
			return mh;
		}
		Hit hit = new Hit(b, painter);
		hits.add(hit);
		return hit;
	}

	private float ease(long key, boolean hover) {
		float v = anim.getOrDefault(key, 0f);
		float target = hover ? 1f : 0f;
		v += (target - v) * Math.min(1f, dt * 16f);
		if (Math.abs(target - v) < 0.01f) {
			v = target;
		}
		anim.put(key, v);
		return v;
	}

	// ================================================================== Aufbau

	private static final String TITLE_CAPS = "PIN VISUALS";
	private static final String[] I_SHIRT = { "##...##", "###.###", "#######", ".#####.", ".#####.", ".#####.", ".#####." };
	private static final String[] I_PALETTE = { "..###..", ".#...#.", "#.#...#", "#...#.#", "#.....#", ".#..##.", "..##..." };
	private static final String[] I_SLIDERS = { "..#....", "#######", "..#....", ".......", "....#..", "#######", "....#.." };
	private static final String[] NAV_LABELS = { "Sets", "Cosmetics", "Extras", "Loadout", "Favorites", "Themes",
			"Tweaks", "Settings", "Get Packs" };
	private static final String[][] NAV_ICONS = { I_GRID, I_SHIRT, I_EXTRAS, I_LAYERS, I_HEART, I_PALETTE, I_SLIDERS,
			I_GEAR, I_DOWN };

	/** Pixel font for titles (Silkscreen). */
	private Font titleFont;
	/** Tooltip text for the hovered icon-only button (drawn last). */
	private String navTip;
	private int railScroll;
	private String railFollow;

	private void layout() {
		pw = Math.min(width - 12, 860);
		ph = Math.min(height - 12, 490);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		navX = px + 8;
		navY = py + 8;
		navH = ph - 16;
		navLabels = navH >= 34 + NAV_LABELS.length * 28 + 20;
		navW = navLabels ? 56 : 30;
		mainX = navX + navW + 8;
		mainR = px + pw - 8;
		mainW = mainR - mainX;
		headY = py + 8;
		railCompact = ph < 300;
		railH = railCompact ? 26 : 36;
		railY = headY + HEAD_H + 6;
		dockH = ph >= 280 ? 30 : 0;
		dockY = py + ph - 8 - dockH;
		bodyTop = railY + railH + 6;
		bodyBottom = dockH > 0 ? dockY - 6 : py + ph - 8;
		previewW = Math.max(130, Math.min(230, mainW * 5 / 12));
		prevPanelX = mainX;
		cx = mainX + previewW + 6;
		cw = mainR - cx;
		gridPanelTop = bodyTop;
		gridPanelBottom = bodyBottom;
	}

	@Override
	protected void init() {
		applyPalette();
		uiFont = MenuFonts.get(config.font);
		// with the smooth Nunito the titles use it too (like the rest), otherwise the pixel title font
		titleFont = MenuFonts.smooth(config.font) ? uiFont : MenuFonts.get("silkscreen");
		if (index == null) {
			index = PackIndex.scan(minecraft.getResourcePackDirectory());
			focusedPack = activeOf(group());
			// Erster Start: anbieten, die mitgelieferten Packs zu installieren
			if (!config.packOfferDone && !PackLibrary.entries().isEmpty()) {
				modal = Modal.OFFER;
			}
		}
		layout();

		String old = search == null ? "" : search.getValue();
		search = new EditBox(uiFont, 0, py, 40, 12, Component.literal("Search"));
		search.setBordered(false);
		search.setHint(Component.literal("Search").withColor(FAINT));
		search.setTextColor(TEXT);
		search.setValue(old);
		search.setResponder(s -> {
			gridScroll = 0;
			rebuild();
		});
		searchAdded = false;

		rebuild();
	}

	private void rebuild() {
		for (AbstractWidget w : dynamic) {
			removeWidget(w);
		}
		dynamic.clear();
		hits.clear();
		modalHits.clear();
		cards.clear();

		if (zoom) {
			if (searchAdded) {
				removeWidget(search);
				searchAdded = false;
			}
			hit(0, 0, width, height, () -> {
				zoom = false;
				rebuild();
			}, (g, t, h) -> {
			});
			return;
		}
		if (!searchAdded) {
			addWidget(search);
			searchAdded = true;
		}

		buildHeader();
		buildNav();
		buildRail();
		buildCenter();
		buildGridBar();
		buildPreview();
		buildDock();

		if (modal != Modal.NONE) {
			for (Hit h : hits) {
				h.button().active = false; // alles dahinter ist gesperrt
			}
			if (searchAdded) {
				removeWidget(search);
				searchAdded = false;
			}
			buildingModal = true;
			try {
				buildModal();
			} finally {
				buildingModal = false;
			}
		}
	}

	private void selectGroup(Group gr) {
		groupId = gr.id();
		gridScroll = 0;
		focusedPack = activeOf(gr);
		rebuild();
	}

	/** Square icon button in the header. */
	private void iconButton(int x, int y, int s, String[] icon, java.util.function.BooleanSupplier on, boolean danger,
			Runnable action) {
		hit(x, y, s, s, action, (g, t, h) -> {
			int acc = accent();
			boolean active = on.getAsBoolean();
			if (active || t > 0.01f) {
				softGlow(g, x - 5, y - 5, s + 10, s + 10, danger ? RED : acc, (int) (0x70 * Math.max(t, active ? 1f : 0f)));
			}
			int fill = active ? mix(CARD, acc, 0.35f) : mix(CARD, danger ? mix(CARD, RED, 0.35f) : CARD_HI, t);
			rrect(g, x, y, s, s, 6, alpha(fill, 0xD0));
			outline(g, x, y, s, s, 6, active ? mix(acc, 0xFFFFFFFF, 0.3f)
					: alpha(mix(mix(BORDER, acc, 0.5f), danger ? RED : acc, t), 0x90 + (int) (t * 0x6F)));
			glyph(g, icon, x + (s - 7) / 2, y + (s - 7) / 2, active ? 0xFFFFFFFF : mix(SOFT, TEXT, t));
		});
	}

	private String subtitle() {
		Theme th = theme();
		return index.packs.size() + " packs · " + (th == null ? "Custom" : th.name());
	}

	private void buildHeader() {
		int s = 20;
		int y = headY + (HEAD_H - s) / 2;
		int x2 = mainR - s;
		int x1 = x2 - 5 - s;
		iconButton(x1, y, s, I_FOLDER, () -> false, false, () -> openFolder(minecraft.getResourcePackDirectory()));
		iconButton(x2, y, s, I_CLOSE, () -> false, true, this::onClose);

		int titleW = Math.max(Math.round(titleFont.width(TITLE_CAPS) * 1.25f), uiFont.width(subtitle()));
		int titleEnd = mainX + 2 + titleW + 14;
		searchW = Math.max(60, x1 - 10 - titleEnd);
		searchX = x1 - 10 - searchW;
		searchY = headY + (HEAD_H - SEARCH_H) / 2;
		search.setX(searchX + 21);
		search.setY(searchY + 5);
		search.setWidth(searchW - 28);
		search.setHeight(12);
		String hint = group().cosmetic() != null ? "Search cosmetics..." : "Search styles and packs...";
		search.setHint(Component.literal(uiFont.width(hint) <= searchW - 30 ? hint
				: uiFont.width("Search") <= searchW - 30 ? "Search" : "").withColor(FAINT));
		search.setTextColor(TEXT);
	}

	private List<Group> currentList() {
		List<Group> l = new ArrayList<>();
		switch (listMode) {
			case 1 -> {
				for (Group gr : Group.extras()) {
					if (gr.cosmetic() != null || !index.styles(gr.id()).isEmpty()) {
						l.add(gr);
					}
				}
			}
			case 2 -> {
				for (Group gr : Group.main()) {
					if (isOn(gr)) {
						l.add(gr);
					}
				}
			}
			case 3 -> {
				for (Group gr : Group.main()) {
					if (gr.cosmetic() != null) {
						l.add(gr);
					}
				}
			}
			default -> {
				for (Group gr : Group.main()) {
					if (gr.cosmetic() == null) {
						l.add(gr);
					}
				}
			}
		}
		return l;
	}

	private void setListMode(int mode) {
		listMode = mode;
		railScroll = 0;
		railFollow = null;
		List<Group> l = currentList();
		boolean has = false;
		for (Group gr : l) {
			has |= gr.id().equals(groupId);
		}
		if (!has && !l.isEmpty()) {
			selectGroup(l.get(0));
		} else {
			rebuild();
		}
	}

	// ---------------------------------------------------------------- sidebar

	private boolean navOn(int i) {
		return switch (i) {
			case 0 -> !themesTab && listMode == 0;
			case 1 -> !themesTab && listMode == 3;
			case 2 -> !themesTab && listMode == 1;
			case 3 -> !themesTab && listMode == 2;
			case 4 -> favoritesOnly;
			case 5 -> themesTab;
			case 6 -> modal == Modal.OPTIONS;
			case 7 -> modal == Modal.SETTINGS;
			default -> modal == Modal.SELECT || modal == Modal.PROGRESS;
		};
	}

	private void navAction(int i) {
		switch (i) {
			case 0, 1, 2, 3 -> {
				themesTab = false;
				setListMode(i == 1 ? 3 : i == 2 ? 1 : i == 3 ? 2 : 0);
			}
			case 4 -> {
				favoritesOnly = !favoritesOnly;
				gridScroll = 0;
				rebuild();
			}
			case 5 -> {
				themesTab = !themesTab;
				rebuild();
			}
			case 6 -> {
				modal = Modal.OPTIONS;
				rebuild();
			}
			case 7 -> {
				modal = Modal.SETTINGS;
				rebuild();
			}
			default -> {
				if (job != null && !job.finished) {
					modal = Modal.PROGRESS;
				} else {
					openSelect();
				}
				rebuild();
			}
		}
	}

	private void buildNav() {
		int itemH = navLabels ? Math.max(28, Math.min(34, (navH - 34 - 20) / NAV_LABELS.length)) : 20;
		int x = navX + 3;
		int w = navW - 6;
		int y = navY + 34;
		boolean lib = !PackLibrary.entries().isEmpty();
		for (int i = 0; i < NAV_LABELS.length; i++) {
			if (i == 8 && !lib) {
				break;
			}
			if (i == 5 || i == 8) {
				int sy = y + 2;
				hit(x, sy, 1, 1, () -> {
				}, (g, t, h) -> {
					hgrad(g, x + 2, sy, w / 2 - 2, 1, 0, alpha(accent(), 0), alpha(mix(accent(), 0xFFFFFFFF, 0.3f), 0x90));
					hgrad(g, x + w / 2, sy, w / 2 - 2, 1, 0, alpha(mix(accent(), 0xFFFFFFFF, 0.3f), 0x90), alpha(accent(), 0));
				});
				y += 6;
			}
			final int idx = i;
			final int iy = y;
			hit(x, iy, w, itemH - 2, () -> navAction(idx), (g, t, h) -> paintNav(g, idx, x, iy, w, itemH - 2, t, h));
			y += itemH;
		}
	}

	private void paintNav(GuiGraphicsExtractor g, int i, int x, int y, int w, int h, float t, boolean hover) {
		boolean on = navOn(i);
		int acc = accent();
		if (on) {
			softGlow(g, x - 8, y - 6, w + 16, h + 12, acc2(), 0x90);
			hgrad3(g, x, y, w, h, 6, mix(acc, 0xFF000000, 0.05f), acc2(), acc3());
			outline(g, x, y, w, h, 6, alpha(0xFFFFFF, 0x60));
		} else if (t > 0.01f) {
			rrect(g, x, y, w, h, 6, alpha(acc, (int) (t * 0x38)));
		}
		int c = on ? 0xFFFFFFFF : mix(DIM, TEXT, t);
		if (navLabels) {
			int top = y + Math.max(3, (h - 26) / 2);
			glyph2(g, NAV_ICONS[i], x + (w - 14) / 2, top, 2, c);
			ctextShadow(g, fit(NAV_LABELS[i], w - 2), x + w / 2, top + 17, c);
		} else {
			glyph2(g, NAV_ICONS[i], x + (w - 14) / 2, y + (h - 14) / 2, 2, c);
			if (hover) {
				navTip = NAV_LABELS[i];
			}
		}
	}

	/** Pixel icon drawn k times bigger (one rectangle per run, so it stays batched). */
	private static void glyph2(GuiGraphicsExtractor g, String[] rows, int x, int y, int k, int color) {
		for (int r = 0; r < rows.length; r++) {
			String s = rows[r];
			int c = 0;
			while (c < s.length()) {
				if (s.charAt(c) == '#') {
					int e = c;
					while (e < s.length() && s.charAt(e) == '#') {
						e++;
					}
					FillBatch.fill(g, x + c * k, y + r * k, x + e * k, y + (r + 1) * k, color);
					c = e;
				} else {
					c++;
				}
			}
		}
	}

	// ---------------------------------------------------------------- collection rail

	private int railVisible;
	private int railSize;

	private void buildRail() {
		List<Group> list = currentList();
		int x0 = mainX + 4;
		int x1 = mainR - 4;
		int y = railY + 3;
		int h = railH - 6;
		railSize = list.size();
		if (list.isEmpty()) {
			String msg = listMode == 2 ? "Nothing equipped yet - pick a style first" : "Nothing here yet";
			hit(x0, y, 1, 1, () -> {
			}, (g, t, hv) -> text(g, fit(msg, x1 - x0 - 12), x0 + 8, y + (h - 8) / 2, FAINT));
			railVisible = 0;
			return;
		}
		int n = list.size();
		int minW = railCompact ? 26 : 46;
		int avail = x1 - x0;
		int chipW;
		int start;
		boolean arrows = n * minW > avail;
		int aw = 14;
		if (!arrows) {
			railScroll = 0;
			railVisible = n;
			chipW = Math.min(railCompact ? 34 : 74, avail / n);
			start = x0 + (avail - n * chipW) / 2;
		} else {
			int inner = avail - 2 * (aw + 3);
			railVisible = Math.max(1, inner / minW);
			chipW = inner / railVisible;
			start = x0 + aw + 3 + (inner - railVisible * chipW) / 2;
			int sel = -1;
			for (int i = 0; i < n; i++) {
				if (list.get(i).id().equals(groupId)) {
					sel = i;
				}
			}
			if (sel >= 0 && !groupId.equals(railFollow)) {
				if (sel < railScroll) {
					railScroll = sel;
				} else if (sel >= railScroll + railVisible) {
					railScroll = sel - railVisible + 1;
				}
			}
			railScroll = Math.max(0, Math.min(railScroll, n - railVisible));
			int rs = railScroll;
			railArrow(x0, y, aw, h, -1, rs > 0, () -> {
				railScroll = Math.max(0, railScroll - railVisible);
				rebuild();
			});
			railArrow(x1 - aw, y, aw, h, 1, rs + railVisible < n, () -> {
				railScroll = Math.min(n - railVisible, railScroll + railVisible);
				rebuild();
			});
		}
		railFollow = groupId;
		for (int i = 0; i < railVisible; i++) {
			int idx = railScroll + i;
			if (idx >= n) {
				break;
			}
			Group gr = list.get(idx);
			int x = start + i * chipW;
			int w = chipW - 3;
			hit(x, y, w, h, () -> {
				themesTab = false;
				selectGroup(gr);
			}, (g, t, hv) -> paintChip(g, gr, x, y, w, h, t, hv));
		}
	}

	private void railArrow(int x, int y, int w, int h, int dir, boolean enabled, Runnable action) {
		Hit hh = hit(x, y, w, h, action, (g, t, hv) -> {
			if (t > 0.01f) {
				rrect(g, x, y, w, h, 5, alpha(accent(), (int) (t * 0x30)));
			}
			chevron(g, x + w / 2, y + h / 2, dir, enabled ? mix(SOFT, TEXT, t) : alpha(FAINT, 0x60));
		});
		hh.button().active = enabled;
	}

	private void paintChip(GuiGraphicsExtractor g, Group gr, int x, int y, int w, int h, float t, boolean hover) {
		boolean sel = gr.id().equals(groupId);
		int acc = accent();
		if (sel) {
			softGlow(g, x - 8, y - 6, w + 16, h + 12, acc, 0x80);
			vgrad(g, x, y, w, h, 6, 6, alpha(mix(acc, 0xFFFFFFFF, 0.08f), 0xA8), alpha(mix(acc, 0xFF000000, 0.35f), 0x80));
			outline(g, x, y, w, h, 6, alpha(mix(acc, 0xFFFFFFFF, 0.55f), 0xE0));
			hgrad(g, x + w / 4, y + h - 2, w / 2, 2, 1, mix(acc, 0xFFFFFFFF, 0.4f), mix(acc2(), 0xFFFFFFFF, 0.4f));
		} else if (t > 0.01f) {
			rrect(g, x, y, w, h, 6, alpha(acc, (int) (t * 0x30)));
		}
		int lift = Math.round(t);
		if (railCompact) {
			icon(g, gr, x + (w - 16) / 2, y + (h - 16) / 2 - lift, 16);
			if (hover) {
				navTip = gr.label();
			}
		} else {
			icon(g, gr, x + (w - 16) / 2, y + 2 - lift, 16);
			ctextShadow(g, fit(gr.label(), w - 4), x + w / 2, y + h - 10, sel ? 0xFFFFFFFF : mix(DIM, TEXT, t));
		}
		if (isOn(gr)) {
			softGlow(g, x + w - 10, y, 8, 8, GREEN, 0x90);
			dot(g, x + w - 8, y + 2, 4, GREEN);
		}
	}

	// ---------------------------------------------------------------- grid

	private List<PackInfo> filteredStyles(Group gr) {
		String q = search.getValue().trim().toLowerCase();
		List<PackInfo> out = new ArrayList<>();
		if (!favoritesOnly && (q.isEmpty() || "vanilla".contains(q))) {
			out.add(null); // Vanilla
		}
		for (PackInfo p : index.styles(gr.id())) {
			if (!q.isEmpty() && !p.displayName().toLowerCase().contains(q)) {
				continue;
			}
			if (favoritesOnly && !config.favorites.contains(favKey(gr, p.id()))) {
				continue;
			}
			out.add(p);
		}
		return out;
	}

	private int shownCount;

	private void buildCenter() {
		Group gr = group();
		if (gr.cosmetic() != null) {
			buildCosmeticCenter(gr.cosmetic());
			return;
		}

		List<PackInfo> styles = filteredStyles(gr);
		shownCount = styles.size();
		int[] geo = gridGeometry(styles.size());
		int cols = geo[0];
		int cardW = geo[1];
		int cardH = geo[2];
		int gap = geo[3];
		int gx = geo[4];
		for (int r = 0; r < rowsVisible; r++) {
			for (int c = 0; c < cols; c++) {
				int idx = (gridScroll + r) * cols + c;
				if (idx >= styles.size()) {
					break;
				}
				PackInfo p = styles.get(idx);
				int x = gx + c * (cardW + gap);
				int y = gridTop + r * (cardH + gap);
				cards.add(new CardRect(x, y, cardW, cardH, p));
				hit(x, y, cardW, cardH, () -> selectStyle(p == null ? null : p.id()),
						(g, t, h) -> paintCard(g, gr, p, x, y, cardW, cardH, t, h));
			}
		}
	}

	/**
	 * Card grid inside the grid panel (below its title row, above the pager). Sets gridTop, rowsVisible, totalRows and
	 * snaps gridScroll to a page. Returns {cols, cardW, cardH, gap, x}.
	 */
	private int[] gridGeometry(int count) {
		int pad = 8;
		int gap = 6;
		int gw = cw - 2 * pad;
		int top = gridPanelTop + 26;
		int gh = gridPanelBottom - 22 - top;
		int cols = Math.max(1, (gw + gap) / (66 + gap));
		int cardW = (gw - gap * (cols - 1)) / cols;
		int cardH = Math.max(50, Math.min(92, Math.round(cardW * 0.88f)));
		if (gh < cardH) {
			cardH = Math.max(40, gh);
		}
		rowsVisible = Math.max(1, (gh + gap) / (cardH + gap));
		int left = gh - (rowsVisible * cardH + (rowsVisible - 1) * gap);
		if (left > cardH * 0.35f) {
			int r = rowsVisible + 1;
			int h2 = (gh - (r - 1) * gap) / r;
			if (h2 >= 48) {
				rowsVisible = r;
				cardH = h2;
			}
		}
		int spare = gh - (rowsVisible * cardH + (rowsVisible - 1) * gap);
		cardH += Math.min(10, spare / rowsVisible);
		totalRows = Math.max(1, (count + cols - 1) / cols);
		int pages = Math.max(1, (totalRows + rowsVisible - 1) / rowsVisible);
		int page = Math.max(0, Math.min(pages - 1, gridScroll / rowsVisible));
		gridScroll = page * rowsVisible;
		gridTop = top;
		int usedW = cols * cardW + (cols - 1) * gap;
		return new int[] { cols, cardW, cardH, gap, cx + (cw - usedW) / 2 };
	}

	private int pageCount() {
		return Math.max(1, (totalRows + rowsVisible - 1) / Math.max(1, rowsVisible));
	}

	private void goPage(int page) {
		int p = Math.max(0, Math.min(pageCount() - 1, page));
		if (p * rowsVisible != gridScroll) {
			gridScroll = p * rowsVisible;
			rebuild();
		}
	}

	// ================================================================== Cosmetics

	private void buildCosmeticCenter(Cosmetics.Kind kind) {
		String q = search.getValue().trim().toLowerCase();
		List<Cosmetics.Item> items = new ArrayList<>();
		if (q.isEmpty() || "none".contains(q)) {
			items.add(null);
		}
		for (Cosmetics.Item it : Cosmetics.list(kind)) {
			if (q.isEmpty() || it.name().toLowerCase().contains(q)) {
				items.add(it);
			}
		}

		int[] geo = gridGeometry(items.size());
		int cols = geo[0];
		int cardW = geo[1];
		int cardH = geo[2];
		int gap = geo[3];
		int gx = geo[4];
		for (int r = 0; r < rowsVisible; r++) {
			for (int c = 0; c < cols; c++) {
				int idx = (gridScroll + r) * cols + c;
				if (idx >= items.size()) {
					break;
				}
				Cosmetics.Item it = items.get(idx);
				int x = gx + c * (cardW + gap);
				int y = gridTop + r * (cardH + gap);
				hit(x, y, cardW, cardH, () -> {
					Cosmetics.wear(config, kind, it == null ? null : it.id());
					if (kind == Cosmetics.Kind.KILL && it != null) {
						KillEffects.preview(it.id());
					}
					if (kind == Cosmetics.Kind.POP && it != null) {
						PopChams.preview();
					}
					setStatus(it == null ? (Cosmetics.isEffect(kind) ? "Turned off" : "Removed")
							: it.name() + (Cosmetics.isEffect(kind) ? " on" : " equipped"), false);
					rebuild();
				}, (g, t, h) -> paintCosmeticCard(g, kind, it, x, y, cardW, cardH, t));
			}
		}
	}

	private void paintCosmeticCard(GuiGraphicsExtractor g, Cosmetics.Kind kind, Cosmetics.Item it, int x, int y0, int w,
			int h, float t) {
		boolean active = Objects.equals(it == null ? Cosmetics.NONE : it.id(), Cosmetics.worn(kind));
		int y = cardFrame(g, x, y0, w, h, t, active, false);
		int areaH = h - 18;
		drawCosmetic(g, kind, it, x + 9, y + 6, w - 18, areaH - 6);
		cardName(g, it == null ? "None" : it.name(), x, y, w, h, active, t);
		if (active) {
			checkBadge(g, x + w - 14, y + 3);
		}
	}

	/** Card background shared by style and cosmetic cards. Returns the (hover-lifted) top y. */
	private int cardFrame(GuiGraphicsExtractor g, int x, int y0, int w, int h, float t, boolean active, boolean focused) {
		int y = y0 - Math.round(t * 2);
		int acc = accent();
		boolean light = menuTheme().light();
		int op = opacity();
		softGlow(g, x - 4, y + 2, w + 8, h + 8, 0xFF000000, light ? 0x18 : 0x60);
		if (active || focused) {
			softGlow(g, x - 9, y - 9, w + 18, h + 18, acc, active ? 0xB0 : 0x60);
		} else if (t > 0.01f) {
			softGlow(g, x - 9, y - 9, w + 18, h + 18, acc, (int) (t * 0x70));
		}
		vgrad(g, x, y, w, h, 7, 7, alpha(mix(CARD_HI, acc, active ? 0.32f : 0.14f + t * 0.12f), 0xE0 * op / 100),
				alpha(mix(CARD, 0xFF000000, light ? 0f : 0.2f), 0xEC * op / 100));
		// thumbnail well with a soft light in the pack's accent
		int areaH = h - 18;
		int wx = x + 4;
		int wy = y + 4;
		int ww = w - 8;
		int wh = areaH - 2;
		if (light) {
			vgrad(g, wx, wy, ww, wh, 5, 5, mix(PLATE_TOP, acc, 0.06f), PLATE_BOTTOM);
		} else {
			rrect(g, wx, wy, ww, wh, 5, alpha(mix(0xFF05020F, acc, 0.10f), 0xD0));
		}
		softGlow(g, wx + ww / 2 - ww * 2 / 5, wy + wh / 2 - wh * 2 / 5, ww * 4 / 5, wh * 4 / 5, acc,
				active ? 0x90 : 0x40 + (int) (t * 0x40));
		// glowing bar along the bottom edge
		hgrad(g, x + 10, y + h - 2, w - 20, 1, 0, alpha(acc, active ? 0xFF : 0x70 + (int) (t * 0x80)),
				alpha(acc2(), active ? 0xFF : 0x70 + (int) (t * 0x80)));
		int border = active ? mix(acc, 0xFFFFFFFF, 0.6f)
				: focused ? alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0xD0)
						: alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0x40 + (int) (t * 0x90));
		outline(g, x, y, w, h, 7, border);
		return y;
	}

	private void cardName(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, boolean active, float t) {
		String s = fit(name, w - 8);
		int c = active ? (menuTheme().light() ? TEXT : 0xFFFFFFFF) : mix(SOFT, TEXT, t);
		int tx = x + (w - uiFont.width(s)) / 2;
		textShadow(g, s, tx, y + h - 13, c);
		if ((active || t > 0.5f) && !MenuFonts.smooth(config.font)) {
			textShadow(g, s, tx + 1, y + h - 13, c); // bold
		}
	}

	/** Accent square with a white check mark (selected card). */
	private void checkBadge(GuiGraphicsExtractor g, int x, int y) {
		softGlow(g, x - 6, y - 6, 23, 23, GREEN, 0xB0);
		rrect(g, x, y, 11, 11, 5, GREEN);
		outline(g, x, y, 11, 11, 5, mix(GREEN, 0xFFFFFFFF, 0.45f));
		glyph(g, I_CHECK, x + 2, y + 2, 0xFFFFFFFF);
	}

	/** Bookmark ribbon with a star (favorite). */
	private void ribbon(GuiGraphicsExtractor g, int x, int y) {
		int acc = accent();
		FillBatch.fill(g, x, y, x + 9, y + 9, acc);
		FillBatch.fill(g, x, y + 9, x + 3, y + 12, acc);
		FillBatch.fill(g, x + 6, y + 9, x + 9, y + 12, acc);
		FillBatch.fill(g, x + 3, y + 9, x + 6, y + 10, acc);
		star(g, x + 4, y + 4, 0xFFFFFFFF);
	}

	/** Flache Vorschau eines Cosmetics (Umhang von hinten, Hut auf dem Kopf von vorne, Fluegel ausgebreitet). */
	private void drawCosmetic(GuiGraphicsExtractor g, Cosmetics.Kind kind, Cosmetics.Item it, int x, int y, int w, int h) {
		if (it == null) {
			int r = Math.max(6, Math.min(w, h) / 4);
			int cxm = x + w / 2;
			int cym = y + h / 2;
			for (int i = -r; i <= r; i++) {
				int span = (int) Math.round(Math.sqrt(r * r - i * i));
				FillBatch.fill(g, cxm - span, cym + i, cxm - span + 2, cym + i + 1, FAINT);
				FillBatch.fill(g, cxm + span - 2, cym + i, cxm + span, cym + i + 1, FAINT);
				FillBatch.fill(g, cxm + i - 1, cym - i, cxm + i + 1, cym - i + 1, FAINT);
			}
			return;
		}
		switch (kind) {
			case CAPE -> {
				int ch = h;
				int cwid = Math.round(ch * 10 / 16f);
				if (cwid > w) {
					cwid = w;
					ch = Math.round(w * 16 / 10f);
				}
				int ox = x + (w - cwid) / 2;
				int oy = y + (h - ch) / 2;
				FillBatch.fill(g, ox + 2, oy + 2, ox + cwid + 2, oy + ch + 2, 0x30000000);
				FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, Cosmetics.capeTexture(it.id()), ox, oy, 2, 2, cwid, ch, 20, 32,
						128, 64);
			}
			case HAT -> drawHatPreview(g, Cosmetics.hat(it.id()), x, y, w, h);
			case WINGS -> drawWingPreview(g, Cosmetics.wing(it.id()), x, y, w, h);
			case KILL -> drawKillPreview(g, it, x, y, w, h);
			case LYRICS -> drawLyricsPreview(g, it, x, y, w, h);
			case ARMOR -> drawArmorPreview(g, it, x, y, w, h);
			case SPOTIFY -> drawSpotifyPreview(g, it, x, y, w, h);
			case HIT -> drawHitPreview(g, it, x, y, w, h);
			case POP -> drawPopPreview(g, it, x, y, w, h);
		}
	}

	/** Pop chams card: a glowing player silhouette that rises and fades, again and again. */
	private void drawPopPreview(GuiGraphicsExtractor g, Cosmetics.Item it, int x, int y, int w, int h) {
		long ms = System.currentTimeMillis();
		int c = PopChams.color(it.id(), ms);
		float f = (ms % 1600L) / 1600f;
		float sc = Math.max(0.5f, Math.min(w / 18f, h / 36f)); // size of one model pixel
		int a = Math.round(0xC0 * (1f - f));
		softGlow(g, x + w / 2 - Math.round(16 * sc), y + 2, Math.round(32 * sc), h - 4, 0xFF000000 | c,
				Math.max(0x20, a / 2));
		g.pose().pushMatrix();
		g.pose().translate(x + (w - 16 * sc) / 2f, y + (h - 32 * sc) / 2f + ((1f - f) * 3 - 1) * sc);
		g.pose().scale(sc, sc);
		drawPopFigure(g, c, a);
		g.pose().popMatrix();
	}

	private static void drawPopFigure(GuiGraphicsExtractor g, int c, int a) {
		int u = 1;
		int fx = 0;
		int fy = 0;
		int col = (Math.max(0x18, a) << 24) | c;
		FillBatch.fill(g, fx + 4 * u, fy, fx + 12 * u, fy + 8 * u, col); // head
		FillBatch.fill(g, fx + 4 * u, fy + 8 * u, fx + 12 * u, fy + 20 * u, col); // body
		FillBatch.fill(g, fx, fy + 8 * u, fx + 4 * u, fy + 20 * u, col); // arms
		FillBatch.fill(g, fx + 12 * u, fy + 8 * u, fx + 16 * u, fy + 20 * u, col);
		FillBatch.fill(g, fx + 4 * u, fy + 20 * u, fx + 8 * u, fy + 32 * u, col); // legs
		FillBatch.fill(g, fx + 8 * u, fy + 20 * u, fx + 12 * u, fy + 32 * u, col);
		int edge = (Math.max(0x30, Math.min(0xFF, a + 0x30)) << 24) | 0xFFFFFF;
		FillBatch.fill(g, fx + 4 * u, fy, fx + 12 * u, fy + Math.max(1, u / 2), edge);
	}

	/** Spotify card: the real overlay, scaled to fit (with the song that is playing, or a sample). */
	private void drawSpotifyPreview(GuiGraphicsExtractor g, Cosmetics.Item it, int x, int y, int w, int h) {
		boolean real = Lyrics.hasTrack();
		String title = real ? Lyrics.title() : "Your Song";
		String artist = real ? (Lyrics.artist().isEmpty() ? "Advertisement" : Lyrics.artist()) : "Artist";
		long dur = real ? Lyrics.durationMs() : 200_000;
		long pos = real ? Lyrics.position() : (System.currentTimeMillis() / 10) % dur;
		int cw = SpotifyHud.width(font, it.id(), title, artist);
		int ch = SpotifyHud.height(it.id());
		if (w < cw * 0.7f) {
			// small card: a simple sketch of the layout that stays readable
			boolean dark = "dark".equals(it.id());
			int c1 = dark ? 0xFF1DB954 : 0xFF22D3EE;
			int c2 = dark ? 0xFF1ED760 : 0xFFA855F7;
			boolean min = "minimal".equals(it.id());
			int ph = min ? Math.max(12, h / 4) : Math.max(20, h * 2 / 3);
			int pw = w - 4;
			int px = x + 2;
			int py = y + (h - ph) / 2;
			rrect(g, px, py, pw, ph, 3, dark ? 0xF0121212 : 0xC010081E);
			if (!dark) {
				outline(g, px, py, pw, ph, 3, alpha(c2, 0x70));
			}
			float prog = ((System.currentTimeMillis() / 40) % 100) / 100f;
			if (min) {
				for (int i = 0; i < 3; i++) {
					int bh = 2 + (int) Math.round((Math.sin(System.currentTimeMillis() / (140.0 + i * 37) + i) + 1) * 1.5);
					FillBatch.fill(g, px + 3 + i * 3, py + ph / 2 + 2 - bh, px + 5 + i * 3, py + ph / 2 + 2, i == 1 ? c2 : c1);
				}
				rrect(g, px + 14, py + ph / 2 - 2, (pw - 18) * 3 / 5, 3, 1, 0xFFFFFFFF);
				hgrad(g, px + 2, py + ph - 3, Math.max(2, Math.round((pw - 4) * prog)), 1, 0, c1, c2);
				return;
			}
			int cs = ph - 6;
			hgrad(g, px + 3, py + 3, cs, cs, 2, c1, c2);
			int tx = px + cs + 7;
			int tw = px + pw - 4 - tx;
			rrect(g, tx, py + 4, tw * 4 / 5, 3, 1, 0xFFFFFFFF);
			rrect(g, tx, py + 10, tw / 2, 3, 1, 0xFF9A9AB0);
			rrect(g, tx, py + ph - 7, tw, 2, 1, 0x50FFFFFF);
			hgrad(g, tx, py + ph - 7, Math.max(2, Math.round(tw * prog)), 2, 1, c1, c2);
			return;
		}
		float sc = Math.min(1.5f, Math.min((w - 4) / (float) cw, (h - 4) / (float) ch));
		g.pose().pushMatrix();
		g.pose().translate(x + (w - cw * sc) / 2f, y + (h - ch * sc) / 2f);
		g.pose().scale(sc, sc);
		SpotifyHud.draw(g, font, it.id(), 0, 0, title, artist, pos, dur, !real || Lyrics.playing(),
				real ? SpotifyHud.cover() : null, 1f);
		g.pose().popMatrix();
	}

	/** Hit colour card: the player's face flashing in the colour. */
	private void drawHitPreview(GuiGraphicsExtractor g, Cosmetics.Item it, int x, int y, int w, int h) {
		long ms = System.currentTimeMillis();
		int c = HitColor.color(it.id(), ms);
		int s = Math.max(8, Math.min(w, h) * 3 / 4 / 8 * 8);
		int hx = x + (w - s) / 2;
		int hy = y + (h - s) / 2;
		softGlow(g, hx - s / 3, hy - s / 3, s + s * 2 / 3, s + s * 2 / 3, c | 0xFF000000, 0x70);
		Identifier skin = skinTexture();
		if (skin != null) {
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 8, 8, s, s, 8, 8, 64, 64);
		} else {
			FillBatch.fill(g, hx, hy, hx + s, hy + s, 0xFF8B6B4A);
		}
		// flash like a hit: strong, then fading out
		float f = (ms % 1100L) / 1100f;
		float k = f < 0.45f ? 1f : Math.max(0f, 1f - (f - 0.45f) / 0.25f);
		int al = Math.round(((c >>> 24) & 0xFF) * k);
		if (al > 0) {
			FillBatch.fill(g, hx, hy, hx + s, hy + s, (c & 0xFFFFFF) | (al << 24));
		}
	}

	private static final ItemStack[] ARMOR_DEMO = { new ItemStack(Items.DIAMOND_HELMET),
			new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS),
			new ItemStack(Items.DIAMOND_BOOTS) };
	private static final float[] ARMOR_DEMO_F = { 0.94f, 0.71f, 0.42f, 0.12f };

	/** Armor HUD card: four armor pieces with sample durability, drawn like the real HUD style. */
	private void drawArmorPreview(GuiGraphicsExtractor g, Cosmetics.Item it, int x, int y, int w, int h) {
		boolean compact = "compact".equals(it.id());
		int rowH = Math.max(9, Math.min(18, h / 4));
		float sc = rowH / 18f;
		int panelW = Math.round((compact ? 46 : 58) * sc);
		int px = x + (w - panelW) / 2;
		int py = y + (h - rowH * 4) / 2;
		if ("glass".equals(it.id())) {
			rrect(g, px - 4, py - 3, panelW + 8, rowH * 4 + 5, 4, 0x90100A1E);
			outline(g, px - 4, py - 3, panelW + 8, rowH * 4 + 5, 4, 0x707DD3FC);
		}
		for (int i = 0; i < 4; i++) {
			int ry = py + i * rowH;
			float f = ARMOR_DEMO_F[i];
			int col = ArmorHud.color(f);
			g.pose().pushMatrix();
			g.pose().translate(px, ry);
			g.pose().scale(sc, sc);
			FillBatch.flush(g).fakeItem(ARMOR_DEMO[i], 0, 0);
			g.pose().popMatrix();
			int tx = px + Math.round(19 * sc);
			// text shrinks with the rows so small cards do not overlap
			String label = compact ? Integer.toString(Math.round(f * 407)) : Math.round(f * 100) + "%";
			g.pose().pushMatrix();
			g.pose().translate(tx, ry + (compact ? 4 : 1) * sc);
			g.pose().scale(Math.min(1f, sc), Math.min(1f, sc));
			text(g, label, 0, 0, col);
			g.pose().popMatrix();
			if (!compact) {
				int bw = panelW - Math.round(20 * sc);
				int by = ry + Math.round(11 * sc);
				FillBatch.fill(g, tx, by, tx + bw, by + 3, 0xA0000000);
				FillBatch.fill(g, tx, by, tx + Math.max(1, Math.round(bw * f)), by + 2, col);
			}
		}
	}

	private static final Map<String, ItemStack> KILL_ICONS = new HashMap<>();

	private static ItemStack killIcon(String id) {
		return KILL_ICONS.computeIfAbsent(id, k -> {
			Item item = switch (k) {
				case "lightning" -> Items.TRIDENT;
				case "firework" -> Items.FIREWORK_ROCKET;
				case "souls" -> Items.SOUL_LANTERN;
				case "hearts" -> Items.POPPY;
				case "ender" -> Items.ENDER_EYE;
				case "crimson" -> Items.REDSTONE;
				default -> Items.BLAZE_POWDER;
			};
			return new ItemStack(item);
		});
	}

	/** Kill effect card: an animated burst in the effect's colours around its item. */
	private void drawKillPreview(GuiGraphicsExtractor g, Cosmetics.Item it, int x, int y, int w, int h) {
		int[] pal = KillEffects.palette(it.id());
		int ccx = x + w / 2;
		int ccy = y + h / 2;
		int s = Math.min(w, h);
		softGlow(g, ccx - s / 2, ccy - s / 2, s, s, pal[0], 0x90);
		long ms = System.currentTimeMillis();
		float cyc = (ms % 1600L) / 1600f;
		int n = 14;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + (i % 2) * 0.2;
			float r = s * (0.12f + 0.38f * ((cyc + (i % 3) * 0.17f) % 1f));
			int px = ccx + (int) Math.round(Math.cos(a) * r);
			int py = ccy + (int) Math.round(Math.sin(a) * r * 0.85f);
			int sz = i % 3 == 0 ? 3 : 2;
			int fade = (int) (0xFF * (1f - ((cyc + (i % 3) * 0.17f) % 1f)));
			FillBatch.fill(g, px, py, px + sz, py + sz, alpha(pal[i % pal.length], Math.max(0x30, fade)));
		}
		int is = Math.max(16, Math.min(32, s / 2 / 16 * 16));
		ItemStack st = killIcon(it.id());
		g.pose().pushMatrix();
		g.pose().translate(ccx - is / 2f, ccy - is / 2f);
		g.pose().scale(is / 16f, is / 16f);
		FillBatch.flush(g).fakeItem(st, 0, 0);
		g.pose().popMatrix();
	}

	/** Lyrics card: a little night sky with glowing placeholder lines (no real text), the middle one half sung. */
	private void drawLyricsPreview(GuiGraphicsExtractor g, Cosmetics.Item it, int x, int y, int w, int h) {
		int[] col = Lyrics.colors(it.id());
		vgrad(g, x, y, w, h, 5, 5, 0xFF0B1030, 0xFF1E2A5A);
		long seed = it.id().hashCode();
		for (int i = 0; i < 10; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sx = x + 3 + (int) ((seed >>> 33) % Math.max(1, w - 6));
			int sy = y + 3 + (int) ((seed >>> 13) % Math.max(1, h - 6));
			FillBatch.fill(g, sx, sy, sx + 1, sy + 1, 0x90FFFFFF);
		}
		int lw = w * 3 / 4;
		int lx = x + (w - lw) / 2;
		int my = y + h / 2 - 2;
		float sung = (System.currentTimeMillis() % 3000L) / 3000f;
		if (col[1] != 0) {
			softGlow(g, lx - 6, my - 6, lw + 12, 16, col[1], 0x80);
		}
		rrect(g, lx, my, lw, 4, 2, alpha(col[0], 0x60));
		int done = Math.round(lw * sung);
		if (done > 2) {
			hgrad(g, lx, my, done, 4, 2, col[0], col[2]);
		}
		int w2 = lw * 2 / 3;
		rrect(g, x + (w - w2) / 2, my - 9, w2, 3, 1, alpha(col[0], 0x40));
		rrect(g, x + (w - w2) / 2, my + 9, w2, 3, 1, alpha(col[0], 0x40));
		glyph(g, I_NOTE, x + 4, y + 4, col[0]);
	}

	private static final String[] I_NOTE = { "...####", "...#..#", "...#..#", "...#..#", ".###.##", "####.##", ".##...." };

	private Identifier skinTexture() {
		try {
			if (minecraft.player != null) {
				return minecraft.player.getSkin().body().texturePath();
			}
		} catch (RuntimeException ignored) {
			// ohne Skin: grauer Kopf
		}
		return null;
	}

	private void drawHatPreview(GuiGraphicsExtractor g, Cosmetics.Hat hat, int x, int y, int w, int h) {
		// Ausdehnung des Huts (vorne gesehen) bestimmen, damit Kopf + Hut in die Flaeche passen
		float minX = -4;
		float maxX = 4;
		float minY = -8;
		for (Cosmetics.Box b : hat.boxes()) {
			minX = Math.min(minX, b.x0());
			maxX = Math.max(maxX, b.x1());
			minY = Math.min(minY, b.y0());
		}
		float spanX = maxX - minX;
		float spanY = 0 - minY;
		float k = Math.min(w / spanX, h / spanY);
		k = Math.max(1f, (float) Math.floor(k * 2) / 2f);
		float ox = x + w / 2f;
		float oy = y + (h + spanY * k) / 2f; // y = 0 (Kinn) liegt unten
		int hx = Math.round(ox - 4 * k);
		int hy = Math.round(oy - 8 * k);
		int hs = Math.round(8 * k);
		Identifier skin = skinTexture();
		if (skin != null) {
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 8, 8, hs, hs, 8, 8, 64, 64);
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, skin, hx, hy, 40, 8, hs, hs, 8, 8, 64, 64);
		} else {
			rrect(g, hx, hy, hs, hs, 2, mix(BORDER_HI, 0xFFC8A27A, 0.5f));
		}
		List<Cosmetics.Box> boxes = new ArrayList<>(hat.boxes());
		boxes.sort((a, b) -> Float.compare(b.z1(), a.z1())); // hinten zuerst
		for (Cosmetics.Box b : boxes) {
			int x0 = Math.round(ox + b.x0() * k);
			int x1 = Math.max(x0 + 1, Math.round(ox + b.x1() * k));
			int y0 = Math.round(oy + b.y0() * k);
			int y1 = Math.max(y0 + 1, Math.round(oy + b.y1() * k));
			FillBatch.fill(g, x0, y0, x1, y1, b.color());
			FillBatch.fill(g, x0, y0, x1, y0 + 1, mix(b.color(), 0xFFFFFFFF, 0.25f)); // Lichtkante
		}
	}

	private void drawWingPreview(GuiGraphicsExtractor g, Cosmetics.Wing wing, int x, int y, int w, int h) {
		// everything in model pixels: body 8x12, head 8x8 above it, wings start 3 px below the shoulders
		float cell = wing.cell();
		float wingW = wing.w() * cell;
		float above = (wing.rootY() * cell) - 3; // how far the wing reaches above the shoulders
		float below = (wing.h() - wing.rootY()) * cell + 3;
		float top = Math.min(-8, -above);
		float bottom = Math.max(12, below);
		float totalW = 2 * wingW + 8 - 2;
		float totalH = bottom - top;
		float k = Math.min(w / totalW, h / totalH);
		float ox = x + (w - totalW * k) / 2f + (wingW - 1) * k; // left body edge
		float oy = y + (h - totalH * k) / 2f - top * k; // shoulder height
		rrect(g, Math.round(ox), Math.round(oy), Math.round(8 * k), Math.round(12 * k), 1,
				mix(BORDER_HI, 0xFF3B82F6, 0.35f));
		rrect(g, Math.round(ox), Math.round(oy - 8 * k), Math.round(8 * k), Math.round(8 * k), 1,
				mix(BORDER_HI, 0xFF5B3A1E, 0.6f));
		// the wing is baked into a small texture once and drawn as two images (right + mirrored left) instead of
		// thousands of single-pixel rectangles
		Identifier[] tex = wingTexture(wing);
		if (tex == null) {
			return;
		}
		int ww = wing.w();
		int wh = wing.layers().get(0).px().length;
		float s = cell * k;
		float wingTop = oy + (3 - wing.rootY() * cell) * k;
		// right wing starts at the right shoulder, the (pre-mirrored) left wing ends at the left shoulder
		float[] lefts = { ox + 7 * k, ox + 1 * k - ww * s };
		for (int i = 0; i < 2; i++) {
			FillBatch.flush(g);
			g.pose().pushMatrix();
			g.pose().translate(lefts[i], wingTop);
			g.pose().scale(s, s);
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, tex[i], 0, 0, 0, 0, ww, wh, ww, wh, ww, wh);
			g.pose().popMatrix();
		}
	}

	private final Map<Cosmetics.Wing, Identifier[]> wingTextures = new HashMap<>();
	private int wingCounter;

	/**
	 * All layers of a wing merged into one texture (one pixel per wing cell), plus a mirrored copy for the left
	 * side (mirrored drawing is culled by the GUI). Created on first use, released when the menu closes.
	 */
	private Identifier[] wingTexture(Cosmetics.Wing wing) {
		Identifier[] cached = wingTextures.get(wing);
		if (cached != null) {
			return cached;
		}
		try {
			int ww = wing.w();
			int wh = wing.layers().get(0).px().length;
			Identifier[] ids = new Identifier[2];
			for (int side = 0; side < 2; side++) {
				com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(ww, wh, true);
				for (Cosmetics.Layer layer : wing.layers()) {
					int[][] px = layer.px();
					for (int r = 0; r < Math.min(wh, px.length); r++) {
						for (int c = 0; c < Math.min(ww, px[r].length); c++) {
							if (px[r][c] != 0) {
								img.setPixel(side == 0 ? c : ww - 1 - c, r, px[r][c]);
							}
						}
					}
				}
				Identifier id = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID,
						"wingpreview/w" + (wingCounter++));
				minecraft.getTextureManager().register(id,
						new net.minecraft.client.renderer.texture.DynamicTexture(() -> "pin visuals wing preview", img));
				ids[side] = id;
			}
			wingTextures.put(wing, ids);
			return ids;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Spieler-Vorschau (dreht sich langsam), mit allen getragenen Cosmetics. */
	private boolean drawPlayer(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
		if (minecraft.player == null) {
			return false;
		}
		try {
			var player = minecraft.player;
			net.minecraft.client.renderer.entity.state.EntityRenderState st = minecraft.getEntityRenderDispatcher()
					.getRenderer(player).createRenderState(player, 1f);
			st.shadowPieces.clear();
			st.outlineColor = 0;
			float spin = (System.currentTimeMillis() % 9000L) / 9000f * 360f;
			if (st instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState ls) {
				ls.bodyRot = 180f + spin;
				ls.yRot = 0f;
				ls.xRot = 0f;
				ls.boundingBoxWidth /= ls.scale;
				ls.boundingBoxHeight /= ls.scale;
				ls.scale = 1f;
			}
			org.joml.Quaternionf rot = new org.joml.Quaternionf().rotateZ((float) Math.PI);
			org.joml.Quaternionf cam = new org.joml.Quaternionf().rotateX(12f * (float) Math.PI / 180f);
			rot.mul(cam);
			int scale = Math.max(10, Math.round((y2 - y1) / 2.9f));
			org.joml.Vector3f tr = new org.joml.Vector3f(0f, st.boundingBoxHeight / 2f + 0.0625f, 0f);
			FillBatch.flush(g).entity(st, scale, tr, rot, cam, x1, y1, x2, y2);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private void drawCosmeticInspector(GuiGraphicsExtractor g, Cosmetics.Kind kind) {
		int ipx = prevPanelX + 9;
		int ipw = previewW - 18;
		drawStage(g, null);
		Cosmetics.Item it = Cosmetics.item(kind, Cosmetics.worn(kind));
		boolean effect = Cosmetics.isEffect(kind);
		if (effect || !drawPlayer(g, stageX + 2, stageY + 2, stageX + stageW - 2, stageY + stageH - 2)) {
			drawCosmetic(g, kind, it, stageX + 12, stageY + 12, stageW - 24, stageH - 24);
		}
		String name = it == null ? (effect ? "Off" : "None") : it.name();
		float sc = titleFont.width(name) * 1.25f <= ipw ? 1.25f : 1f;
		titleText(g, fitTitle(name, ipw, sc), ipx, nameY, sc, menuTheme().light() ? TEXT : 0xFFFFFFFF);
		String sub;
		if (kind == Cosmetics.Kind.KILL) {
			sub = "Plays when you kill a player";
		} else if (kind == Cosmetics.Kind.LYRICS) {
			sub = Lyrics.statusLine();
		} else if (kind == Cosmetics.Kind.ARMOR) {
			sub = "Left of the hotbar, red = almost broken";
		} else if (kind == Cosmetics.Kind.SPOTIFY) {
			sub = SpotifyHud.statusLine();
		} else if (kind == Cosmetics.Kind.HIT) {
			sub = "Flash colour when mobs and players get hit";
		} else if (kind == Cosmetics.Kind.POP) {
			sub = "Ghost when an enemy pops a totem";
		} else {
			sub = minecraft.player == null ? "Preview needs a world" : "Only visible to you";
		}
		text(g, fit(sub, ipw), ipx, nameY + 14, DIM);
	}

	/** Dark stage for the big preview: gradient, glow and a little pedestal light at the bottom. */
	private void previewBox(GuiGraphicsExtractor g, int x, int y, int w, int h, Group gr) {
		int acc = accent();
		glow(g, x, y, w, h, 6, 3, acc, 0x0E);
		if (gr != null && isSky(gr)) {
			plate(g, gr, x, y, w, h, 6, 6, 0f);
		} else {
			vgrad(g, x, y, w, h, 6, 6, mix(PLATE_BOTTOM, acc, 0.10f), mix(PLATE_TOP, acc, 0.12f));
			halo(g, x + w / 2, y + h / 2, w * 2 / 5, h * 2 / 5, acc, 0x12);
			halo(g, x + w / 2, y + h - h / 7, w / 3, Math.max(3, h / 14), acc, 0x30);
			ellipse(g, x + w / 2, y + h - h / 7, w / 5, Math.max(2, h / 28), alpha(mix(acc, 0xFFFFFFFF, 0.4f), 0x50));
		}
		outline(g, x, y, w, h, 6, mix(mix(BORDER, acc, 0.5f), 0xFFFFFFFF, 0.05f));
	}

	private void paintCard(GuiGraphicsExtractor g, Group gr, PackInfo p, int x, int y0, int w, int h, float t,
			boolean hover) {
		String id = p == null ? null : p.id();
		boolean active = Objects.equals(id, activeOf(gr));
		boolean focused = Objects.equals(id, focusedPack);
		if (hover) {
			hoverSet = true;
			hoverPack = p;
		}
		int y = cardFrame(g, x, y0, w, h, t, active, focused);
		int areaH = h - 18;
		if (isSky(gr)) {
			plate(g, gr, x + 4, y + 4, w - 8, areaH - 2, 4, 2, t);
			drawPics(g, gr, p, x + 4, y + 4, w - 8, areaH - 2);
		} else {
			drawPics(g, gr, p, x + 10, y + 6, w - 20, areaH - 6);
		}
		cardName(g, packName(id), x, y, w, h, active, t);

		boolean fav = p != null && config.favorites.contains(favKey(gr, p.id()));
		if (active) {
			checkBadge(g, x + w - 14, y + 3);
		}
		if (fav) {
			ribbon(g, active ? x + w - 26 : x + w - 13, y);
		}
	}

	/** Left big panel: tags + heart, the stage, name, accent swatches, opacity and Apply / reset. */
	private void buildPreview() {
		int x = prevPanelX + 9;
		int w = previewW - 18;
		if (themesTab) {
			int bx = prevPanelX + previewW - 20;
			int by = bodyTop + 7;
			hit(bx, by, 12, 12, () -> {
				themesTab = false;
				rebuild();
			}, (g, t, h) -> glyph(g, I_CLOSE, bx + 2, by + 2, mix(FAINT, TEXT, t)));
			buildThemes(x, w, bodyTop + 24);
			return;
		}

		Cosmetics.Kind kind = group().cosmetic();
		int top = bodyTop + 8;
		int btnH = 20;
		int btnY = bodyBottom - 9 - btnH;
		int slidersH = 44;
		int nameBlock = 26;
		stageX = x;
		stageW = w;
		stageY = top + 17;
		sliderTop = btnY - 7 - slidersH;
		stageH = sliderTop - 6 - nameBlock - stageY;
		showSliders = stageH >= 62;
		if (!showSliders) {
			sliderTop = btnY;
			stageH = btnY - 6 - nameBlock - stageY;
		}
		stageH = Math.max(30, stageH);
		nameY = stageY + stageH + 6;

		if (kind == null) {
			// heart = favorite
			int hx = x + w - 14;
			int hy = top - 1;
			hit(hx, hy, 14, 14, () -> toggleFavorite(focusedPack), (g, t, h) -> {
				boolean fav = focusedPack != null && config.favorites.contains(favKey(group(), focusedPack));
				int pink = 0xFFF472B6;
				if (fav || t > 0.01f) {
					softGlow(g, hx - 6, hy - 6, 26, 26, pink, fav ? 0x90 : (int) (t * 0x60));
				}
				glyph(g, fav ? I_HEART : I_HEART_O, hx + 3, hy + 4,
						fav ? pink : focusedPack == null ? FAINT : mix(SOFT, pink, t));
			});
			// click the stage to zoom
			hit(stageX, stageY, stageW, stageH, () -> {
				zoomPack = focusedPack == null ? null : index.byId(focusedPack);
				zoom = true;
				rebuild();
			}, (g, t, h) -> {
			});
		}

		if (kind == Cosmetics.Kind.LYRICS || kind == Cosmetics.Kind.SPOTIFY) {
			// lyrics: "visible through blocks", Spotify overlay: "top right"; instead of the theme row
			boolean lyr = kind == Cosmetics.Kind.LYRICS;
			int sy = showSliders ? sliderTop + 6 : sliderTop - 30;
			hit(x - 2, sy - 2, w + 4, 28, () -> {
				if (lyr) {
					config.lyricsThroughBlocks = !config.lyricsThroughBlocks;
				} else {
					config.spotifyRight = !config.spotifyRight;
				}
				config.save();
				Tweaks.load(config);
				setStatus(lyr ? (config.lyricsThroughBlocks ? "Lyrics show over blocks" : "Blocks now hide the lyrics")
						: (config.spotifyRight ? "Overlay moved to the top right" : "Overlay moved to the top left"),
						false);
			}, (g, t, h) -> {
				rrect(g, x - 2, sy - 2, w + 4, 28, 5, alpha(HOVER, (int) (t * 0x12)));
				boolean on = lyr ? config.lyricsThroughBlocks : config.spotifyRight;
				text(g, fit(lyr ? "Visible through blocks" : "Top right corner", w - 30), x + 2, sy + 2, TEXT);
				text(g, fit(lyr ? (on ? "Always on top" : "Hidden behind blocks") : (on ? "Top right" : "Top left"),
						w - 30), x + 2, sy + 13, DIM);
				int sw = 22;
				int sx = x + w - sw - 2;
				int syy = sy + 7;
				if (on) {
					hgrad3(g, sx, syy, sw, 11, 5, accent(), acc2(), acc3());
				} else {
					rrect(g, sx, syy, sw, 11, 5, TRACK);
				}
				rrect(g, on ? sx + sw - 10 : sx + 1, syy + 1, 9, 9, 4, 0xFFFFFFFF);
			});
		} else if (showSliders) {
			int sy = sliderTop;
			int n = THEMES.size();
			int ds = Math.max(6, Math.min(12, (w - (n - 1) * 4) / n));
			int dgap = n > 1 ? (w - n * ds) / (n - 1) : 0;
			hit(x, sy, 1, 1, () -> {
			}, (g, t, h) -> text(g, "Theme", x, sy, DIM));
			for (int i = 0; i < n; i++) {
				Theme th = THEMES.get(i);
				int sx = x + i * (ds + dgap);
				int dy = sy + 11;
				hit(sx - 1, dy - 1, ds + 2, ds + 2, () -> {
					config.accent = th.id();
					config.save();
				}, (g, t, h) -> {
					boolean on = th.id().equals(config.accent);
					if (on || t > 0.01f) {
						softGlow(g, sx - 5, dy - 5, ds + 10, ds + 10, th.color(), on ? 0xA0 : (int) (t * 0x70));
					}
					if (on) {
						rrect(g, sx - 2, dy - 2, ds + 4, ds + 4, 4, 0xFFFFFFFF);
					}
					swatch(g, sx, dy, ds, 3, th);
				});
			}
			int oy = sy + 25;
			opX = x;
			opW = w;
			opY = oy + 12;
			hit(x - 2, opY - 5, w + 4, 14, () -> {
				dragging = 2;
				applyOpacity(mx);
			}, (g, t, h) -> {
				text(g, "Opacity", x, oy, DIM);
				rtext(g, config.opacity + "%", x + w, oy, TEXT);
				rrect(g, opX, opY, opW, 4, 2, alpha(TRACK, 0xE0));
				int kx = opX + Math.round((config.opacity - 40) / 60f * (opW - 1));
				hgrad3(g, opX, opY, kx - opX + 1, 4, 2, accent(), acc2(), acc3());
				bigKnob(g, kx, opY + 2, acc2(), t);
			});
		}

		// Apply (or Remove for cosmetics) + a small reset-to-vanilla button
		int rw = btnH + 2;
		int aw = w - rw - 4;
		hit(x, btnY, aw, btnH, () -> {
			if (kind != null) {
				Cosmetics.wear(config, kind, null);
				setStatus("Removed", false);
				rebuild();
			} else if (!Objects.equals(focusedPack, activeOf(group()))) {
				selectStyle(focusedPack);
			}
		}, (g, t, h) -> {
			String label;
			boolean live;
			if (kind != null) {
				live = !Cosmetics.NONE.equals(Cosmetics.worn(kind));
				label = Cosmetics.isEffect(kind) ? (live ? "Turn off" : "Off") : live ? "Remove" : "Nothing equipped";
			} else {
				live = !Objects.equals(focusedPack, activeOf(group()));
				label = live ? "Apply Style" : "Applied";
			}
			primaryButton(g, x, btnY, aw, btnH, label, kind == null ? I_CHECK : I_CLOSE, live, t);
		});
		int rx = x + aw + 4;
		hit(rx, btnY, rw, btnH, () -> {
			if (kind != null) {
				Cosmetics.wear(config, kind, null);
				setStatus("Removed", false);
				rebuild();
			} else {
				selectStyle(null);
			}
		}, (g, t, h) -> {
			if (t > 0.01f) {
				softGlow(g, rx - 5, btnY - 5, rw + 10, btnH + 10, accent(), (int) (t * 0x60));
			}
			rrect(g, rx, btnY, rw, btnH, 6, alpha(mix(CARD, CARD_HI, t), 0xD8));
			outline(g, rx, btnY, rw, btnH, 6, alpha(mix(accent(), 0xFFFFFFFF, 0.3f), 0x70 + (int) (t * 0x80)));
			glyph(g, I_RESET, rx + (rw - 7) / 2, btnY + (btnH - 7) / 2, mix(SOFT, TEXT, t));
			if (h) {
				navTip = kind != null ? "Remove" : "Back to vanilla";
			}
		});
	}

	/** Big gradient button (accent to its neighbour hue) with an icon. live = false: calmer "done" look. */
	private void primaryButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, String[] icon,
			boolean live, float t) {
		int acc = accent();
		int acc2 = acc2();
		float k = live ? t : 0f;
		softGlow(g, x - 10, y - 8, w + 20, h + 16, acc2, live ? 0x80 + (int) (k * 0x50) : 0x30);
		int tone = live ? 0xFFFFFFFF : 0xFF000000;
		float tk = live ? k * 0.1f : 0.35f;
		hgrad3(g, x, y, w, h, 6, mix(acc, tone, tk), mix(acc2, tone, tk), mix(acc3(), tone, tk));
		hgrad(g, x + 6, y + 1, w - 12, 1, 0, alpha(0xFFFFFF, live ? 0x60 : 0x20), alpha(0xFFFFFF, 0x10));
		outline(g, x, y, w, h, 6, alpha(mix(acc2, 0xFFFFFFFF, 0.5f), live ? 0xC0 : 0x60));
		int tw = uiFont.width(label) + 12;
		int sx = x + (w - tw) / 2;
		int c = live ? 0xFFFFFFFF : alpha(0xFFFFFF, 0xC0);
		glyph(g, icon, sx, y + (h - 7) / 2, c);
		textShadow(g, label, sx + 12, y + (h - 8) / 2 + 1, c);
		if (!MenuFonts.smooth(config.font)) {
			textShadow(g, label, sx + 13, y + (h - 8) / 2 + 1, c);
		}
	}

	/** Larger slider knob: white ring with a coloured core and a soft glow. */
	private static void bigKnob(GuiGraphicsExtractor g, int cx, int cy, int color, float t) {
		ellipse(g, cx, cy, 8, 8, alpha(color, 0x30 + (int) (t * 0x30)));
		ellipse(g, cx, cy + 1, 6, 6, 0x40000000);
		ellipse(g, cx, cy, 6, 6, 0xFFFFFFFF);
		ellipse(g, cx, cy, 4, 4, color);
	}

	private int themeColor(String id) {
		for (Theme t : THEMES) {
			if (t.id().equals(id)) {
				return t.color();
			}
		}
		return THEMES.get(0).color();
	}

	/** Card background with a soft shadow (drawn before the card's content). */
	private void card(int x, int y, int w, int h) {
		hit(x, y, 1, 1, () -> {
		}, (g, t, hov) -> {
			shadow(g, x, y, w, h, 5, 1, 0x0C);
			rbox(g, x, y, w, h, 5, CARD, BORDER);
		});
	}

	/** Card title (small caps + accent tick) with an optional value on the right. */
	private void cardTitle(int x, int y, int w, String title, java.util.function.Supplier<String> right) {
		hit(x, y, 1, 1, () -> {
		}, (g, t, h) -> {
			FillBatch.fill(g, x, y + 1, x + 2, y + 7, accent());
			text(g, title, x + 5, y, DIM);
			if (right != null) {
				String r = right.get();
				if (r != null) {
					rtext(g, fit(r, w - uiFont.width(title) - 14), x + w, y, SOFT);
				}
			}
		});
	}

	/** Round slider knob: white ring with a colored center. */
	private static void knob(GuiGraphicsExtractor g, int cx, int cy, int color, float t) {
		int r = 5;
		rrect(g, cx - r, cy - r + 1, 2 * r, 2 * r, r, 0x33000000);
		rrect(g, cx - r, cy - r, 2 * r, 2 * r, r, 0xFFFFFFFF);
		int ir = t > 0.01f ? 4 : 3;
		rrect(g, cx - ir, cy - ir, 2 * ir, 2 * ir, ir, color);
	}

	private String hoverThemeName;
	private String hoverAccentName;

	private void buildThemes(int ipx, int ipw, int y) {
		int avail = bodyBottom - y;
		int head = 12;
		// pick the roomiest layout that fits: 0 = normal, 1 = compact, 2 = tight (themes in 5 columns, no link)
		int mode = 0;
		int pad = 7;
		int gapC = 6;
		int sh = 22;
		int cols = 3;
		boolean resetLink = true;
		for (; mode <= 2; mode++) {
			pad = mode == 0 ? 7 : 5;
			gapC = mode == 0 ? 6 : 4;
			cols = mode == 2 ? 5 : 3;
			sh = mode == 0 ? 22 : mode == 1 ? 16 : 15;
			resetLink = mode < 2;
			cols = 4;
			int rws = (THEMES.size() + cols - 1) / cols;
			int total = (2 * pad + head + rws * sh + (rws - 1) * 3) + (2 * pad + head + 16) + (2 * pad + head + 12 + 17)
					+ (2 * pad + head + 8) + 3 * gapC + (resetLink ? gapC + 12 : 0);
			if (total <= avail) {
				break;
			}
		}
		mode = Math.min(mode, 2);
		int iw = ipw - 2 * pad;
		int ix0 = ipx + pad;

		// ---- MENU THEME: the colour gradients, each tints the whole menu
		int rows = (THEMES.size() + cols - 1) / cols;
		int c1h = pad + head + rows * sh + (rows - 1) * 3 + pad;
		card(ipx, y, ipw, c1h);
		int gy = y + pad + head;
		int sw = (iw - 3 * (cols - 1)) / cols;
		final int fcols = cols;
		final int fsh = sh;
		for (int i = 0; i < THEMES.size(); i++) {
			Theme th = THEMES.get(i);
			int x = ix0 + (i % fcols) * (sw + 3);
			int ty = gy + (i / fcols) * (sh + 3);
			hit(x, ty, sw, sh, () -> {
				config.accent = th.id();
				config.menuTheme = "lila";
				config.save();
				applyPalette();
				rebuild();
			}, (g, t, h) -> paintGradientTile(g, th, x, ty, sw, fsh, t, h));
		}
		int t1 = y + pad;
		cardTitle(ix0, t1, iw, "MENU THEME", () -> {
			if (hoverThemeName != null) {
				return hoverThemeName;
			}
			Theme cur = theme();
			return cur == null ? "Custom" : cur.name();
		});
		y += c1h + gapC;

		// ---- FONT: one pill with arrows, the name is drawn in the font itself
		int c2h = pad + head + 16 + pad;
		card(ipx, y, ipw, c2h);
		int fy = y + pad + head;
		int half = iw / 2;
		hit(ix0, fy, half, 16, () -> cycleFont(-1), (g, t, h) -> {
			rbox(g, ix0, fy, iw, 16, 4, FIELD, BORDER);
			if (t > 0.01f) {
				rrect(g, ix0 + 1, fy + 1, 16, 14, 3, alpha(HOVER, (int) (t * 0x18)));
			}
			chevron(g, ix0 + 7, fy + 8, -1, mix(DIM, accent(), t));
		});
		hit(ix0 + half, fy, iw - half, 16, () -> cycleFont(1), (g, t, h) -> {
			if (t > 0.01f) {
				rrect(g, ix0 + iw - 17, fy + 1, 16, 14, 3, alpha(HOVER, (int) (t * 0x18)));
			}
			chevron(g, ix0 + iw - 8, fy + 8, 1, mix(DIM, accent(), t));
			MenuFonts.Entry fe = MenuFonts.byId(config.font);
			ctext(g, fit(fe.name(), iw - 36), ix0 + iw / 2, fy + 4, TEXT);
		});
		int fi = MenuFonts.ALL.indexOf(MenuFonts.byId(config.font)) + 1;
		if (resetLink) {
			cardTitle(ix0, y + pad, iw, "FONT", () -> fi + " / " + MenuFonts.ALL.size());
		} else {
			// tight layout: the reset link moves into the font card's title row
			cardTitle(ix0, y + pad, iw, "FONT", null);
			String rs = "Reset";
			int rsw = uiFont.width(rs);
			int rsx = ix0 + iw - rsw;
			int rsy = y + pad;
			hit(rsx - 2, rsy - 2, rsw + 4, 12, this::resetLook, (g, t, h) -> {
				text(g, rs, rsx, rsy, mix(FAINT, accent(), t));
				if (t > 0.01f) {
					FillBatch.fill(g, rsx, rsy + 9, rsx + rsw, rsy + 10, alpha(accent(), (int) (t * 255)));
				}
			});
		}
		y += c2h + gapC;

		// ---- CUSTOM COLOUR: hue slider (the theme then follows that colour)
		int ds = 0;
		int c3h = pad + head + 9 + 8 + pad - 8;
		card(ipx, y, ipw, c3h);
		int dy = y + pad + head + 1;
		hueX = ix0;
		hueW = iw;
		hueY = dy + ds + 4;
		hit(ix0 - 2, hueY - 5, iw + 4, 14, () -> {
			dragging = 1;
			applyHue(mx);
		}, (g, t, h) -> {
			int th = 6;
			hueBar(g, hueX, hueY, hueW, th);
			int kx = hueX + Math.round(hueOf(accent()) * (hueW - 1));
			knob(g, kx, hueY + th / 2, accent(), t);
		});
		cardTitle(ix0, y + pad, iw, "CUSTOM COLOR", () -> String.format("#%06X", accent() & 0xFFFFFF));
		y += c3h + gapC;

		// ---- OPACITY
		int c4h = pad + head + 8 + pad;
		card(ipx, y, ipw, c4h);
		opX = ix0;
		opW = iw;
		opY = y + pad + head + 2;
		hit(ix0 - 2, opY - 5, iw + 4, 14, () -> {
			dragging = 2;
			applyOpacity(mx);
		}, (g, t, h) -> {
			rrect(g, opX, opY, opW, 4, 2, TRACK);
			int kx = opX + Math.round((config.opacity - 40) / 60f * (opW - 1));
			rrect(g, opX, opY, kx - opX + 1, 4, 2, accent());
			knob(g, kx, opY + 2, accent(), t);
		});
		cardTitle(ix0, y + pad, iw, "OPACITY", () -> config.opacity + "%");
		y += c4h + gapC;

		// ---- reset link
		String rl = "Reset look";
		int rw = uiFont.width(rl);
		int rx = ipx + (ipw - rw) / 2;
		int ry = y + 1;
		if (resetLink && ry + 10 <= bodyBottom + 2) {
			hit(rx - 4, ry - 2, rw + 8, 12, this::resetLook, (g, t, h) -> {
				text(g, rl, rx, ry, mix(FAINT, accent(), t));
				if (t > 0.01f) {
					FillBatch.fill(g, rx, ry + 9, rx + rw, ry + 10, alpha(accent(), (int) (t * 255)));
				}
			});
		}
	}

	/** Back to the default look: Nebula theme, amethyst accent, 95% opacity, Minecraft font. */
	private void resetLook() {
		config.accent = "amethyst";
		config.opacity = 95;
		config.menuTheme = "lila";
		config.font = "nunito";
		config.save();
		applyPalette();
		rebuildWidgets();
	}

	/** Small arrow ("<" for dir -1, ">" for dir 1) centered at cx, cy. */
	private static void chevron(GuiGraphicsExtractor g, int cx, int cy, int dir, int color) {
		for (int i = 0; i < 4; i++) {
			int x = cx + (dir > 0 ? i - 2 : 1 - i);
			FillBatch.fill(g, x, cy - 4 + i, x + 1, cy - 2 + i, color);
			FillBatch.fill(g, x, cy + 2 - i, x + 1, cy + 4 - i, color);
		}
	}

	/** Mini window in a colour theme: tinted gradient background, sidebar, a card and the bright gradient bar. */
	private void paintGradientTile(GuiGraphicsExtractor g, Theme th, int x, int y, int w, int h, float t,
			boolean hover) {
		boolean on = th.id().equals(config.accent);
		if (hover) {
			hoverThemeName = th.name();
		}
		int yy = y - Math.round(t);
		if (on || t > 0.01f) {
			softGlow(g, x - 6, yy - 5, w + 12, h + 10, th.c2(), on ? 0xA0 : (int) (t * 0x60));
		}
		int d = 0xFF08070E;
		if (th.glassy()) {
			hgrad3(g, x, yy, w, h, 3, alpha(th.color(), 0x70), alpha(th.c2(), 0x60), alpha(th.c3(), 0x70));
		} else {
			hgrad3(g, x, yy, w, h, 3, mix(d, th.color(), 0.38f), mix(d, th.c2(), 0.30f), mix(d, th.c3(), 0.38f));
		}
		int sbw = Math.max(4, w / 5);
		rrect(g, x + 1, yy + 1, sbw, h - 2, 2, alpha(0xFF000000, th.glassy() ? 0x30 : 0x50));
		int cx0 = x + sbw + 3;
		int cw0 = x + w - 3 - cx0;
		if (h >= 14) {
			rrect(g, cx0, yy + 3, cw0, h - 10, 1, alpha(0xFFFFFF, th.glassy() ? 0x30 : 0x14));
		}
		hgrad3(g, cx0, yy + h - 5, cw0, 2, 1, th.color(), th.c2(), th.c3());
		outline(g, x, yy, w, h, 3, on ? 0xFFFFFFFF : alpha(mix(th.c2(), 0xFFFFFFFF, 0.4f), 0x60 + (int) (t * 0x60)));
	}

	/** Mini window showing what a menu theme looks like: sidebar, header bar, two cards, accent. */
	private void paintThemeTile(GuiGraphicsExtractor g, MenuTheme mt, int x, int y, int w, int h, float t,
			boolean hover) {
		boolean on = mt.id().equals(menuTheme().id());
		if (hover) {
			hoverThemeName = mt.name();
		}
		int c = mt.tint();
		int win;
		int side;
		int crd;
		int line;
		if (mt.light()) {
			win = mix(0xFFFFFFFF, c, 0.03f);
			side = mix(0xFFFFFFFF, c, 0.09f);
			crd = 0xFFFFFFFF;
			line = mix(0xFFD8DCE3, c, 0.22f);
		} else {
			win = mix(0xFF0A0B0F, c, 0.07f);
			side = mix(0xFF0A0B0F, c, 0.12f);
			crd = mix(0xFF0A0B0F, c, 0.20f);
			line = mix(0xFF22242E, c, 0.30f);
		}
		int acc = themeColor(mt.accent());
		int lift = Math.round(t);
		int yy = y - lift;
		if (on) {
			rrect(g, x - 2, yy - 2, w + 4, h + 4, 5, alpha(accent(), 0x55));
		}
		rbox(g, x, yy, w, h, 3, win, on ? accent() : mix(BORDER, BORDER_HI, t));
		int sbw = Math.max(4, w / 4);
		rrect(g, x + 1, yy + 1, sbw, h - 2, 2, side);
		FillBatch.fill(g, x + 3, yy + 3, x + sbw - 1, yy + 4, line);
		FillBatch.fill(g, x + 3, yy + 6, x + sbw - 1, yy + 7, acc);
		int cx0 = x + sbw + 3;
		int cw0 = x + w - 3 - cx0;
		FillBatch.fill(g, cx0, yy + 3, cx0 + Math.max(3, cw0 / 2), yy + 4, line);
		int ch = h - 9;
		if (ch >= 4) {
			int half = (cw0 - 2) / 2;
			rbox(g, cx0, yy + 6, half, ch, 1, crd, line);
			rbox(g, cx0 + half + 2, yy + 6, cw0 - half - 2, ch, 1, crd, line);
			FillBatch.fill(g, cx0 + 2, yy + 6 + ch - 3, cx0 + Math.max(3, half - 2), yy + 6 + ch - 2, acc);
		}
		if (on) {
			rrect(g, x + w - 6, yy + 2, 4, 4, 2, accent());
		}
	}

	private void cycleFont(int dir) {
		List<MenuFonts.Entry> all = MenuFonts.ALL;
		int i = all.indexOf(MenuFonts.byId(config.font));
		config.font = all.get(Math.floorMod(i + dir, all.size())).id();
		config.save();
		// Textfeld und Layout haengen an der Schrift -> komplett neu aufbauen
		rebuildWidgets();
	}

	private void applyHue(int mouseX) {
		float h = Math.max(0f, Math.min(1f, (mouseX - hueX) / (float) Math.max(1, hueW - 1)));
		config.accent = "custom";
		config.customColor = hsv(h, 0.55f, 1f);
	}

	private void applyOpacity(int mouseX) {
		float f = Math.max(0f, Math.min(1f, (mouseX - opX) / (float) Math.max(1, opW - 1)));
		config.opacity = 40 + Math.round(f * 60);
	}

	private void toggleFavorite(String packId) {
		if (packId == null) {
			return;
		}
		String k = favKey(group(), packId);
		if (!config.favorites.remove(k)) {
			config.favorites.add(k);
		}
		config.save();
		rebuild();
	}

	// ---------------------------------------------------------------- grid title row + pager

	/** Title row of the grid panel (All / Favorites switch) and the pager at its bottom. */
	private void buildGridBar() {
		boolean cos = group().cosmetic() != null;
		int segH = 14;
		int segY = gridPanelTop + 7;
		if (!cos) {
			String a = "All";
			String b = "Favorites";
			int wa = uiFont.width(a) + 12;
			int wb = uiFont.width(b) + 20;
			int sx = cx + cw - 8 - wa - wb - 4;
			int bx = sx + 2 + wa;
			hit(sx - 2, segY - 2, wa + wb + 6, segH + 4, () -> {
			}, (g, t, h) -> {
				rrect(g, sx - 2, segY - 2, wa + wb + 6, segH + 4, 6, alpha(mix(WIN, 0xFF000000, 0.3f), 0x90));
				outline(g, sx - 2, segY - 2, wa + wb + 6, segH + 4, 6, alpha(mix(accent(), 0xFFFFFFFF, 0.3f), 0x40));
			});
			segButton(sx, segY, wa, segH, a, null, () -> !favoritesOnly, () -> {
				favoritesOnly = false;
				gridScroll = 0;
				rebuild();
			});
			segButton(bx, segY, wb, segH, b, I_HEART, () -> favoritesOnly, () -> {
				favoritesOnly = true;
				gridScroll = 0;
				rebuild();
			});
		}

		// pager: <  dots / "3 / 12"  >
		int pages = pageCount();
		int cur = gridScroll / Math.max(1, rowsVisible);
		int fy = gridPanelBottom - 18;
		int bs = 14;
		int right = cx + cw - 8;
		if (pages <= 1) {
			return;
		}
		int dotsW;
		boolean dots = pages <= 10;
		String lbl = (cur + 1) + " / " + pages;
		dotsW = dots ? (pages - 1) * 9 + 18 : uiFont.width(lbl) + 8;
		int x = right - bs - 4 - dotsW - 4 - bs;
		pageArrow(x, fy, bs, -1, cur > 0, () -> goPage(cur - 1));
		int dx = x + bs + 4;
		if (dots) {
			int px0 = dx;
			for (int i = 0; i < pages; i++) {
				final int n = i;
				int w = i == cur ? 18 : 6;
				int bx = px0;
				hit(bx, fy + 4, w, 6, () -> goPage(n), (g, t, h) -> {
					if (n == cur) {
						softGlow(g, bx - 4, fy, w + 8, 14, acc2(), 0x90);
						hgrad(g, bx, fy + 4, w, 6, 3, accent(), acc2());
					} else {
						rrect(g, bx, fy + 4, w, 6, 3, mix(alpha(TRACK, 0xFF), accent(), t * 0.6f));
					}
				});
				px0 += w + 3;
			}
		} else {
			hit(dx, fy, 1, 1, () -> {
			}, (g, t, h) -> ctext(g, lbl, dx + dotsW / 2, fy + 3, SOFT));
		}
		pageArrow(right - bs, fy, bs, 1, cur < pages - 1, () -> goPage(cur + 1));
	}

	private void segButton(int x, int y, int w, int h, String label, String[] icon,
			java.util.function.BooleanSupplier on, Runnable action) {
		hit(x, y, w, h, action, (g, t, hov) -> {
			boolean o = on.getAsBoolean();
			if (o) {
				softGlow(g, x - 6, y - 5, w + 12, h + 10, acc2(), 0x70);
				hgrad3(g, x, y, w, h, 5, accent(), acc2(), acc3());
			} else if (t > 0.01f) {
				rrect(g, x, y, w, h, 5, alpha(accent(), (int) (t * 0x30)));
			}
			int c = o ? 0xFFFFFFFF : mix(DIM, TEXT, t);
			int tx = x + 6;
			if (icon != null) {
				glyph(g, icon, tx, y + (h - 7) / 2, o ? 0xFFFFFFFF : 0xFFF472B6);
				tx += 10;
			}
			textShadow(g, label, tx, y + (h - 8) / 2 + 1, c);
		});
	}

	private void pageArrow(int x, int y, int s, int dir, boolean enabled, Runnable action) {
		Hit hh = hit(x, y, s, s, action, (g, t, h) -> {
			int acc = accent();
			if (t > 0.01f) {
				softGlow(g, x - 4, y - 4, s + 8, s + 8, acc, (int) (t * 0x60));
			}
			rrect(g, x, y, s, s, 5, alpha(mix(CARD, CARD_HI, t), 0xD0));
			outline(g, x, y, s, s, 5, alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0x50 + (int) (t * 0x90)));
			chevron(g, x + s / 2, y + s / 2, dir, enabled ? mix(SOFT, TEXT, t) : alpha(FAINT, 0x70));
		});
		hh.button().active = enabled;
	}

	// ---------------------------------------------------------------- loadout dock

	private void buildDock() {
		if (dockH == 0) {
			return;
		}
		int y = dockY + 4;
		int h = dockH - 8;
		int x = mainX + 10 + Math.round(titleFont.width("LOADOUT")) + 10;
		int right = mainR - 5;
		List<Group> on = new ArrayList<>();
		for (Group gr : Group.main()) {
			if (isOn(gr)) {
				on.add(gr);
			}
		}
		if (on.isEmpty()) {
			hit(x, y, 1, 1, () -> {
			}, (g, t, hv) -> text(g, fit("Nothing equipped yet - click a style to wear it", right - x), x + 2,
					y + (h - 8) / 2, FAINT));
			return;
		}
		int avail = right - x;
		int slotW = Math.max(78, Math.min(132, (avail - (on.size() - 1) * 4) / on.size()));
		int fitN = Math.max(1, (avail + 4) / (slotW + 4));
		boolean more = on.size() > fitN;
		int shown = more ? fitN - 1 : on.size();
		for (int i = 0; i < shown; i++) {
			Group gr = on.get(i);
			int sx = x + i * (slotW + 4);
			hit(sx, y, slotW, h, () -> {
				themesTab = false;
				listMode = gr.cosmetic() != null ? 3 : 0;
				railFollow = null;
				selectGroup(gr);
			}, (g, t, hv) -> paintSlot(g, gr, sx, y, slotW, h, t));
		}
		if (more) {
			int sx = x + shown * (slotW + 4);
			String s = "+" + (on.size() - shown) + " more";
			hit(sx, y, Math.min(slotW, right - sx), h, () -> {
				themesTab = false;
				setListMode(2);
			}, (g, t, hv) -> {
				rrect(g, sx, y, Math.min(slotW, right - sx), h, 6, alpha(accent(), 0x18 + (int) (t * 0x30)));
				ctext(g, s, sx + Math.min(slotW, right - sx) / 2, y + (h - 8) / 2, mix(SOFT, TEXT, t));
			});
		}
	}

	private void paintSlot(GuiGraphicsExtractor g, Group gr, int x, int y, int w, int h, float t) {
		boolean sel = gr.id().equals(groupId);
		int acc = accent();
		if (sel || t > 0.01f) {
			softGlow(g, x - 6, y - 5, w + 12, h + 10, acc, sel ? 0x60 : (int) (t * 0x50));
		}
		rrect(g, x, y, w, h, 6, alpha(mix(CARD, sel ? mix(CARD, acc, 0.3f) : CARD_HI, 0.3f + t * 0.5f), 0xC8));
		outline(g, x, y, w, h, 6, alpha(mix(acc, 0xFFFFFFFF, 0.3f), sel ? 0xC0 : 0x40 + (int) (t * 0x70)));
		softGlow(g, x + 1, y + (h - 20) / 2, 20, 20, acc, 0x50);
		icon(g, gr, x + 3, y + (h - 16) / 2, 16);
		int tx = x + 23;
		int tw = w - 27;
		String val;
		Cosmetics.Kind k = gr.cosmetic();
		if (k != null) {
			Cosmetics.Item it = Cosmetics.item(k, Cosmetics.worn(k));
			val = it == null ? "None" : it.name();
		} else {
			val = packName(activeOf(gr));
		}
		text(g, fit(gr.label(), tw), tx, y + 2, DIM);
		textShadow(g, fit(val, tw), tx, y + h - 10, mix(TEXT, 0xFFFFFFFF, 0.5f));
	}

	// ---------------------------------------------------------------- soft light textures

	private static final int GLOW_N = 128;
	private Identifier glowId;
	private boolean glowFailed;

	/** Second accent colour: the accent's hue moved towards pink (grey accents stay grey). */
	private int acc2() {
		Theme th = theme();
		if (th != null) {
			return th.c2();
		}
		int a = accent();
		float r = ((a >> 16) & 255) / 255f;
		float gg = ((a >> 8) & 255) / 255f;
		float b = (a & 255) / 255f;
		float max = Math.max(r, Math.max(gg, b));
		float min = Math.min(r, Math.min(gg, b));
		float sat = max <= 0 ? 0 : (max - min) / max;
		if (sat < 0.2f) {
			return mix(a, 0xFFFFFFFF, 0.25f);
		}
		return hsv(hueOf(a) + 0.09f, Math.min(0.8f, sat + 0.05f), Math.max(0.85f, max));
	}

	/** White radial light, created once; tinted when drawn. */
	private Identifier glowTexture() {
		if (glowId != null || glowFailed) {
			return glowId;
		}
		try {
			com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(GLOW_N, GLOW_N,
					true);
			float c = (GLOW_N - 1) / 2f;
			for (int y = 0; y < GLOW_N; y++) {
				for (int x = 0; x < GLOW_N; x++) {
					float dx = (x - c) / c;
					float dy = (y - c) / c;
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					float a = Math.max(0f, 1f - d);
					a = a * a * (3 - 2 * a); // smoothstep
					img.setPixel(x, y, (Math.round(a * 255) << 24) | 0xFFFFFF);
				}
			}
			glowId = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "ui/glow");
			minecraft.getTextureManager().register(glowId,
					new net.minecraft.client.renderer.texture.DynamicTexture(() -> "pin visuals glow", img));
		} catch (RuntimeException e) {
			glowFailed = true;
			glowId = null;
		}
		return glowId;
	}

	/** Soft round light (or shadow, with a dark colour) stretched over the rectangle. a = strength 0..255. */
	private void softGlow(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, int a) {
		if (w <= 0 || h <= 0 || a <= 0) {
			return;
		}
		Identifier id = glowTexture();
		if (id == null) {
			return;
		}
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, GLOW_N, GLOW_N, GLOW_N, GLOW_N,
				alpha(color, Math.min(255, a)));
	}

	private static final int NEB_W = 320;
	private static final int NEB_H = 180;
	private Identifier nebulaId;
	private int nebulaKey;
	private int nebulaCounter;

	/** Painted nebula backdrop for the window (dark themes), coloured after the theme; rebuilt when it changes. */
	private Identifier nebulaTexture() {
		int tint = accent();
		int key = tint * 31 + acc2() * 17 + acc3();
		if (nebulaId != null && key == nebulaKey) {
			return nebulaId;
		}
		if (nebulaId != null) {
			minecraft.getTextureManager().release(nebulaId);
			nebulaId = null;
		}
		try {
			// blobs in the three colours of the theme gradient
			float h1 = hueOf(tint);
			float h2 = hueOf(acc2());
			float h3 = hueOf(acc3());
			int base = mix(0xFF07041A, tint, 0.07f);
			int[][] blobs = {
					{ 15, 10, 60, hsv(h1, 0.78f, 0.62f), 95 },
					{ 90, 92, 55, hsv(h3, 0.80f, 0.55f), 85 },
					{ 72, 18, 45, hsv(h2, 0.80f, 0.52f), 75 },
					{ 40, 78, 38, hsv(h2, 0.70f, 0.45f), 45 },
					{ 55, 45, 70, hsv(h1, 0.60f, 0.25f), 35 } };
			com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(NEB_W, NEB_H,
					true);
			java.util.Random rnd = new java.util.Random(tint);
			for (int y = 0; y < NEB_H; y++) {
				for (int x = 0; x < NEB_W; x++) {
					float r = (base >> 16) & 255;
					float gg = (base >> 8) & 255;
					float b = base & 255;
					for (int[] bl : blobs) {
						float dx = (x / (float) NEB_W * 100f - bl[0]) / bl[2];
						float dy = (y / (float) NEB_H * 100f - bl[1]) / (bl[2] * 1.2f);
						float a = bl[4] / 100f * (float) Math.exp(-(dx * dx + dy * dy) * 2.2f);
						r += ((bl[3] >> 16) & 255) * a;
						gg += ((bl[3] >> 8) & 255) * a;
						b += (bl[3] & 255) * a;
					}
					float n = rnd.nextFloat() * 3f - 1.5f; // a little noise against banding
					int ri = Math.max(0, Math.min(255, Math.round(r + n)));
					int gi = Math.max(0, Math.min(255, Math.round(gg + n)));
					int bi = Math.max(0, Math.min(255, Math.round(b + n)));
					img.setPixel(x, y, 0xFF000000 | (ri << 16) | (gi << 8) | bi);
				}
			}
			nebulaId = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "ui/nebula" + (nebulaCounter++));
			minecraft.getTextureManager().register(nebulaId,
					new net.minecraft.client.renderer.texture.DynamicTexture(() -> "pin visuals nebula", img));
			nebulaKey = key;
		} catch (RuntimeException e) {
			nebulaId = null;
		}
		return nebulaId;
	}

	/** Twinkling stars over the window (fixed positions). */
	private void stars(GuiGraphicsExtractor g) {
		long seed = 0x5EEDL;
		long ms = System.currentTimeMillis();
		int n = Math.max(30, pw * ph / 6000);
		for (int i = 0; i < n; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sx = px + 3 + (int) ((seed >>> 33) % Math.max(1, pw - 6));
			int sy = py + 3 + (int) ((seed >>> 13) % Math.max(1, ph - 6));
			float tw = 0.5f + 0.5f * (float) Math.sin(ms / 650.0 + i * 1.37);
			int a = (int) (0x18 + tw * 0xB0);
			if (i % 9 == 0) {
				sparkle(g, sx, sy, 2, alpha(0xFFFFFF, a));
			} else {
				FillBatch.fill(g, sx, sy, sx + 1, sy + 1, alpha(i % 5 == 0 ? mix(accent(), 0xFFFFFFFF, 0.6f) : 0xFFFFFF, a));
			}
		}
	}

	/** Glass panel: translucent tinted gradient, faint outline and a light line along the top edge. */
	private void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r) {
		int acc = accent();
		boolean light = menuTheme().light();
		int op = opacity();
		Theme th = theme();
		if (th != null && th.glassy()) {
			// frosted glass: pale see-through pane, bright rim and a diagonal-ish sheen
			vgrad(g, x, y, w, h, r, r, alpha(mix(0xFFFFFFFF, acc, 0.25f), 0x30), alpha(mix(0xFF1E2440, acc3(), 0.2f), 0x58));
			outline(g, x, y, w, h, r, alpha(0xFFFFFF, 0x70));
			hgrad3(g, x + r, y + 1, w - 2 * r, 1, 0, alpha(0xFFFFFF, 0x20), alpha(0xFFFFFF, 0x90), alpha(0xFFFFFF, 0x20));
			hgrad(g, x + 2, y + 2, Math.max(1, w / 3), Math.max(1, h / 5), r, alpha(0xFFFFFF, 0x14), alpha(0xFFFFFF, 0));
			return;
		}
		int a1 = (light ? 0xD8 : 0x9C) * op / 100;
		int a2 = (light ? 0xF0 : 0xC4) * op / 100;
		softGlow(g, x - 6, y - 2, w + 12, h + 12, 0xFF000000, light ? 0x18 : 0x70);
		vgrad(g, x, y, w, h, r, r, alpha(mix(PANEL, acc, light ? 0.06f : 0.20f), a1), alpha(PANEL, a2));
		outline(g, x, y, w, h, r, alpha(mix(acc, 0xFFFFFFFF, 0.35f), light ? 0x50 : 0x58));
		int half = (w - 2 * r) / 2;
		hgrad(g, x + r, y + 1, half, 1, 0, alpha(0xFFFFFF, 0), alpha(0xFFFFFF, light ? 0x60 : 0x2C));
		hgrad(g, x + r + half, y + 1, half, 1, 0, alpha(0xFFFFFF, light ? 0x60 : 0x2C), alpha(0xFFFFFF, 0));
	}

	/** Text in the pixel title font, optionally scaled. */
	private void titleText(GuiGraphicsExtractor g, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		FillBatch.flush(g).text(titleFont, s, 0, 0, color, !menuTheme().light());
		g.pose().popMatrix();
	}

	private String fitTitle(String s, int max, float scale) {
		if (titleFont.width(s) * scale <= max) {
			return s;
		}
		while (s.length() > 1 && titleFont.width(s + "...") * scale > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** Settings dialog row: icon, title, description, arrow. */
	private void settingsRow(int y, String[] icon, String title, String desc, Runnable action) {
		int x = mX + 12;
		int w = mW - 24;
		hit(x - 4, y - 2, w + 8, 28, action, (g, t, h) -> {
			rrect(g, x - 4, y - 2, w + 8, 28, 5, alpha(HOVER, (int) (t * 0x14)));
			softGlow(g, x - 3, y, 22, 22, accent(), 0x40 + (int) (t * 0x40));
			glyph2(g, icon, x + 1, y + 4, 2, mix(SOFT, TEXT, t));
			text(g, title, x + 24, y + 3, TEXT);
			text(g, fit(desc, w - 40), x + 24, y + 14, DIM);
			chevron(g, x + w - 4 + Math.round(t * 2), y + 12, 1, mix(DIM, accent(), t));
		});
	}

	// ================================================================== Pack-Sammlung (Dialog)

	private Path packDir() {
		return minecraft.getResourcePackDirectory();
	}

	private void openSelect() {
		picked.clear();
		for (PackLibrary.Entry e : PackLibrary.entries()) {
			if (!PackLibrary.installed(e, packDir())) {
				picked.add(e.file());
			}
		}
		pickScroll = 0;
		modal = Modal.SELECT;
	}

	private void startInstall(List<PackLibrary.Entry> list) {
		config.packOfferDone = true;
		config.save();
		job = PackLibrary.install(list, packDir());
		jobHandled = false;
		modal = Modal.PROGRESS;
		rebuild();
	}

	private List<String> wrap(String s, int w) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (uiFont.width(test) > w && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	private String offerText() {
		List<PackLibrary.Entry> all = PackLibrary.entries();
		return all.size() + " ready-made texture packs (" + PackLibrary.mb(PackLibrary.totalSize(all))
				+ ") come with Pin Visuals. Copy them into your resourcepacks folder?"
				+ " Then you can pick and mix them here as styles.";
	}

	/** Knopf im Dialog; primary = gefuellt in Akzentfarbe. */
	private void modalButton(int x, int y, int w, String label, boolean primary, boolean enabled, Runnable action) {
		Hit h = hit(x, y, w, 18, action, (g, t, hov) -> {
			int acc = accent();
			if (primary && enabled) {
				glow(g, x, y, w, 18, 5, 2, acc, 0x14 + (int) (t * 0x14));
				hgrad(g, x, y, w, 18, 5, mix(acc, 0xFF000000, 0.05f), mix(acc, 0xFFFFFFFF, 0.12f + t * 0.08f));
				outline(g, x, y, w, 18, 5, mix(acc, 0xFFFFFFFF, 0.4f));
				ctextShadow(g, label, x + w / 2, y + 5, onAccent());
			} else {
				rrect(g, x, y, w, 18, 5, mix(CARD, CARD_HI, t));
				outline(g, x, y, w, 18, 5, mix(mix(BORDER, acc, 0.25f), acc, t * 0.7f));
				ctext(g, label, x + w / 2, y + 5, enabled ? mix(SOFT, TEXT, t) : FAINT);
			}
		});
		h.button().active = enabled;
	}

	private void layoutModal(int w, int h) {
		mW = Math.min(w, width - 20);
		mH = Math.min(h, height - 20);
		mX = (width - mW) / 2;
		mY = (height - mH) / 2;
	}

	private void buildModal() {
		switch (modal) {
			case OFFER -> {
				int w = 300;
				List<String> lines = wrap(offerText(), Math.min(w, width - 20) - 24);
				layoutModal(w, 38 + lines.size() * 11 + 34);
				int by = mY + mH - 28;
				String a = "Install all";
				String b = "Choose";
				String c = "No thanks";
				int wa = uiFont.width(a) + 18;
				int wb = uiFont.width(b) + 18;
				int wc = uiFont.width(c) + 18;
				int x = mX + mW - 12 - wa - wb - wc - 10;
				modalButton(x, by, wc, c, false, true, () -> {
					config.packOfferDone = true;
					config.save();
					modal = Modal.NONE;
					setStatus("You can install packs later with GET PACKS", false);
					rebuild();
				});
				modalButton(x + wc + 5, by, wb, b, false, true, () -> {
					openSelect();
					rebuild();
				});
				modalButton(x + wc + wb + 10, by, wa, a, true, true,
						() -> startInstall(PackLibrary.entries()));
			}
			case SELECT -> {
				layoutModal(360, 280);
				List<PackLibrary.Entry> all = PackLibrary.entries();
				// Alle / Keine
				int kx = mX + mW - 12 - uiFont.width("None");
				hit(kx, mY + 10, uiFont.width("None"), 10, () -> {
					picked.clear();
				}, (g, t, h) -> text(g, "None", kx, mY + 11, mix(DIM, accent(), t)));
				int ax = kx - 10 - uiFont.width("All");
				hit(ax, mY + 10, uiFont.width("All"), 10, () -> {
					for (PackLibrary.Entry e : all) {
						if (!PackLibrary.installed(e, packDir())) {
							picked.add(e.file());
						}
					}
				}, (g, t, h) -> text(g, "All", ax, mY + 11, mix(DIM, accent(), t)));

				int rowH = 14;
				int top = mY + 30;
				int bottom = mY + mH - 36;
				pickRows = Math.max(1, (bottom - top) / rowH);
				pickScroll = Math.max(0, Math.min(pickScroll, Math.max(0, all.size() - pickRows)));
				Path dir = packDir();
				for (int i = 0; i < pickRows; i++) {
					int idx = pickScroll + i;
					if (idx >= all.size()) {
						break;
					}
					PackLibrary.Entry e = all.get(idx);
					boolean have = PackLibrary.installed(e, dir);
					int y = top + i * rowH;
					int rx = mX + 10;
					int rw = mW - 26;
					Hit h = hit(rx, y, rw, rowH - 1, () -> {
						if (!picked.remove(e.file())) {
							picked.add(e.file());
						}
					}, (g, t, hov) -> {
						boolean on = picked.contains(e.file());
						if (t > 0.01f) {
							rrect(g, rx, y, rw, rowH - 1, 3, alpha(HOVER, (int) (t * 0x10)));
						}
						// Kaestchen
						if (have) {
							rbox(g, rx + 4, y + 2, 9, 9, 2, SEL, BORDER);
							FillBatch.fill(g, rx + 6, y + 6, rx + 11, y + 7, GREEN);
						} else if (on) {
							rrect(g, rx + 4, y + 2, 9, 9, 2, accent());
							FillBatch.fill(g, rx + 6, y + 6, rx + 7, y + 8, onAccent());
							FillBatch.fill(g, rx + 7, y + 7, rx + 8, y + 9, onAccent());
							FillBatch.fill(g, rx + 8, y + 5, rx + 9, y + 8, onAccent());
							FillBatch.fill(g, rx + 9, y + 4, rx + 10, y + 6, onAccent());
						} else {
							rbox(g, rx + 4, y + 2, 9, 9, 2, FIELD, mix(BORDER_HI, accent(), t));
						}
						String right = have ? "installed" : PackLibrary.mb(e.size());
						int rwid = uiFont.width(right);
						text(g, fit(e.displayName(), rw - 26 - rwid - 8), rx + 18, y + 3, have ? DIM : TEXT);
						rtext(g, right, rx + rw - 4, y + 3, have ? GREEN : FAINT);
					});
					h.button().active = !have;
				}

				int by = mY + mH - 28;
				String back = "Back";
				int wbk = uiFont.width(back) + 18;
				modalButton(mX + 12, by, wbk, back, false, true, () -> {
					modal = config.packOfferDone ? Modal.NONE : Modal.OFFER;
					rebuild();
				});
				String go = "Install";
				int wgo = uiFont.width(go + " (00 · 000 MB)") + 18;
				modalButton(mX + mW - 12 - wgo, by, wgo, go, true, true, () -> {
					List<PackLibrary.Entry> sel = new ArrayList<>();
					for (PackLibrary.Entry e : all) {
						if (picked.contains(e.file())) {
							sel.add(e);
						}
					}
					if (!sel.isEmpty()) {
						startInstall(sel);
					}
				});
			}
			case PROGRESS -> {
				layoutModal(300, 96);
				String bg = "In background";
				int w = uiFont.width(bg) + 18;
				modalButton(mX + mW - 12 - w, mY + mH - 28, w, bg, false, true, () -> {
					modal = Modal.NONE;
					rebuild();
				});
			}
			case OPTIONS -> {
				layoutModal(300, 188);
				// tabs: Visuals | FPS (top right, next to the title)
				int tabH = 14;
				int tw1 = uiFont.width("Visuals") + 12;
				int tw2 = uiFont.width("FPS") + 12;
				int tx = mX + mW - 12 - tw1 - tw2 - 4;
				int ty = mY + 7;
				segButton(tx, ty, tw1, tabH, "Visuals", null, () -> optionsTab == 0, () -> {
					optionsTab = 0;
					rebuild();
				});
				segButton(tx + tw1 + 4, ty, tw2, tabH, "FPS", null, () -> optionsTab == 1, () -> {
					optionsTab = 1;
					rebuild();
				});
				int rowY = mY + 34;
				if (optionsTab == 0) {
					optionRow(rowY, "No totem pop animation", "Hides the big totem on your screen",
							() -> config.noTotemPop, v -> config.noTotemPop = v);
					optionRow(rowY + 30, "No totem particles", "Hides the particle burst around you",
							() -> config.noTotemParticles, v -> config.noTotemParticles = v);
					optionRow(rowY + 60, "Low fire", "Draws the burning overlay lower on screen",
							() -> config.lowFire, v -> config.lowFire = v);
					optionRow(rowY + 90, "Low shield", "Holds the shield lower in first person",
							() -> config.lowShield, v -> config.lowShield = v);
				} else {
					optionRow(rowY, "No explosion particles", "Crystal and anchor blasts without smoke",
							() -> config.noExplosionParticles, v -> config.noExplosionParticles = v);
					optionRow(rowY + 30, "Fewer hit particles", "No crit, sweep and damage particles",
							() -> config.fewerHitParticles, v -> config.fewerHitParticles = v);
					optionRow(rowY + 60, "Calm end crystals", "Crystals stop spinning and bouncing",
							() -> config.calmCrystals, v -> config.calmCrystals = v);
					optionRow(rowY + 90, "Hide far item drops", "Items on the ground over 16 blocks away",
							() -> config.hideFarItems, v -> config.hideFarItems = v);
					boolean all = Tweaks.fpsBoost(config);
					String boost = all ? "Boost off" : "FPS Boost";
					int bw = uiFont.width(boost) + 22;
					modalButton(mX + 12, mY + mH - 28, bw, boost, !all, true, () -> {
						Tweaks.setFpsBoost(config, !all);
						setStatus(all ? "FPS Boost off" : "FPS Boost on", false);
						rebuild();
					});
				}
				int w = 60;
				modalButton(mX + mW - 12 - w, mY + mH - 28, w, "Close", true, true, () -> {
					modal = Modal.NONE;
					rebuild();
				});
			}
			case SETTINGS -> {
				layoutModal(300, 170);
				int rowY = mY + 36;
				settingsRow(rowY, I_RESET, "Reset all styles", "Every collection back to vanilla", this::resetAll);
				settingsRow(rowY + 32, I_FOLDER, "Open pack folder", "Your resourcepacks folder",
						() -> openFolder(minecraft.getResourcePackDirectory()));
				settingsRow(rowY + 64, I_PALETTE, "Reset look", "Nebula theme, purple accent, Nunito font",
						this::resetLook);
				int w = 60;
				modalButton(mX + mW - 12 - w, mY + mH - 28, w, "Close", true, true, () -> {
					modal = Modal.NONE;
					rebuild();
				});
			}
			case DONE -> {
				layoutModal(300, 88);
				int w = 60;
				modalButton(mX + mW - 12 - w, mY + mH - 28, w, "OK", true, true, () -> {
					modal = Modal.NONE;
					rebuild();
				});
			}
			default -> {
			}
		}
	}

	/** A line in the options dialog: title, small description and an on/off switch on the right. */
	private void optionRow(int y, String title, String desc, java.util.function.BooleanSupplier get,
			java.util.function.Consumer<Boolean> set) {
		int x = mX + 12;
		int w = mW - 24;
		hit(x, y, w, 26, () -> {
			set.accept(!get.getAsBoolean());
			config.save();
			Tweaks.load(config);
		}, (g, t, h) -> {
			rrect(g, x - 4, y - 2, w + 8, 28, 3, alpha(HOVER, (int) (t * 0x10)));
			text(g, title, x, y + 3, TEXT);
			text(g, fit(desc, w - 40), x, y + 14, DIM);
			boolean on = get.getAsBoolean();
			int sw = 22;
			int sx = x + w - sw;
			int sy = y + 7;
			rrect(g, sx, sy, sw, 11, 5, on ? accent() : TRACK);
			int kx = on ? sx + sw - 10 : sx + 1;
			rrect(g, kx, sy + 1, 9, 9, 4, 0xFFFFFFFF);
		});
	}

	private void drawModalFrame(GuiGraphicsExtractor g) {
		int acc = accent();
		FillBatch.fill(g, 0, 0, width, height, 0x80000000);
		shadow(g, mX, mY, mW, mH, 8, 8, 0x18);
		glow(g, mX, mY, mW, mH, 8, 4, acc, 0x12);
		vgrad(g, mX, mY, mW, mH, 8, 8, mix(WIN, acc, 0.12f), WIN);
		outline(g, mX, mY, mW, mH, 8, mix(acc, 0xFFFFFFFF, 0.2f));
		hgrad(g, mX + 8, mY, mW / 2 - 8, 1, 0, alpha(acc, 0), mix(acc, 0xFFFFFFFF, 0.5f));
		hgrad(g, mX + mW / 2, mY, mW / 2 - 8, 1, 0, mix(acc, 0xFFFFFFFF, 0.5f), alpha(acc, 0));
	}

	private void drawModalExtras(GuiGraphicsExtractor g) {
		int x = mX + 12;
		int y = mY + 12;
		switch (modal) {
			case OFFER -> {
				bigText(g, "Install texture packs?", x, y, 1.25f, TEXT);
				int ly = y + 20;
				for (String l : wrap(offerText(), mW - 24)) {
					text(g, l, x, ly, SOFT);
					ly += 11;
				}
			}
			case OPTIONS -> {
				titleText(g, "TWEAKS", x, y, 1.25f, TEXT);
				FillBatch.fill(g, mX + 10, mY + 27, mX + mW - 10, mY + 28, LINE);
			}
			case SETTINGS -> {
				titleText(g, "SETTINGS", x, y, 1.25f, TEXT);
				FillBatch.fill(g, mX + 10, mY + 27, mX + mW - 10, mY + 28, LINE);
				if (!version.isEmpty()) {
					text(g, "Pin Visuals v" + version, x, mY + mH - 23, FAINT);
				}
			}
			case SELECT -> {
				bold(g, "Choose packs", x, y - 1, TEXT);
				FillBatch.fill(g, mX + 10, mY + 25, mX + mW - 10, mY + 26, LINE);
				// Zaehler im Installieren-Knopf
				long bytes = 0;
				for (PackLibrary.Entry e : PackLibrary.entries()) {
					if (picked.contains(e.file())) {
						bytes += e.size();
					}
				}
				String go = "Install (" + picked.size() + " · " + PackLibrary.mb(bytes) + ")";
				int wgo = uiFont.width("Install (00 · 000 MB)") + 18;
				int bx = mX + mW - 12 - wgo;
				int by = mY + mH - 28;
				rrect(g, bx, by, wgo, 18, 5, picked.isEmpty() ? BORDER : accent());
				ctext(g, go, bx + wgo / 2, by + 5, picked.isEmpty() ? DIM : onAccent());
				// Scrollleiste
				int total = PackLibrary.entries().size();
				if (total > pickRows) {
					int top = mY + 30;
					int h = pickRows * 14;
					int sx = mX + mW - 12;
					rrect(g, sx, top, 3, h, 1, TRACK);
					int th = Math.max(12, h * pickRows / total);
					int ty = top + (h - th) * pickScroll / Math.max(1, total - pickRows);
					rrect(g, sx, ty, 3, th, 1, accent());
				}
			}
			case PROGRESS -> {
				bold(g, "Installing packs...", x, y, TEXT);
				PackLibrary.Job j = job;
				if (j != null) {
					text(g, fit(j.current, mW - 24), x, y + 16, DIM);
					int bw = mW - 24;
					rrect(g, x, y + 30, bw, 6, 3, TRACK);
					rrect(g, x, y + 30, Math.max(6, Math.round(bw * j.progress())), 6, 3, accent());
					text(g, j.done + " / " + j.total + "  ·  " + PackLibrary.mb(j.bytesDone) + " / "
							+ PackLibrary.mb(j.bytesTotal), x, y + 42, FAINT);
				}
			}
			case DONE -> {
				PackLibrary.Job j = job;
				boolean err = j != null && j.error != null;
				bold(g, err ? "Installation failed" : "Done!", x, y, err ? RED : TEXT);
				String msg = j == null ? "" : err ? j.error
						: j.copied + " packs installed" + (j.total - j.copied > 0 ? ", " + (j.total - j.copied)
								+ " were already there." : ".") ;
				int ly = y + 16;
				for (String l : wrap(msg, mW - 24)) {
					text(g, l, x, ly, SOFT);
					ly += 11;
				}
				if (!err) {
					text(g, "Pick a collection on the left and click a style.", x, ly, DIM);
				}
			}
			default -> {
			}
		}
	}

	// ================================================================== Aktionen

	private void selectStyle(String packId) {
		Group gr = group();
		if (packId == null) {
			config.styles.remove(gr.id());
		} else {
			config.styles.put(gr.id(), packId);
		}
		config.save();
		focusedPack = packId;
		applyMix(packName(packId) + "  >  " + gr.label());
		rebuild();
	}

	private void resetAll() {
		config.styles.clear();
		config.save();
		focusedPack = null;
		applyMix("Everything reset to vanilla");
		rebuild();
	}

	private void setStatus(String s, boolean error) {
		status = s;
		statusError = error;
		statusAt = System.currentTimeMillis();
	}

	private void applyMix(String okText) {
		try {
			MixBuilder.apply(minecraft, index, config);
			setStatus(okText, false);
		} catch (Exception e) {
			setStatus("Error: " + e.getMessage(), true);
		}
	}

	private void openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			// Minecraft's own "open folder" (same as the resource pack screen), no process started by the mod
			com.mojang.blaze3d.Blaze3D.openPath(dir);
		} catch (Exception e) {
			setStatus("Could not open the folder", true);
		}
	}

	// ================================================================== Vorschau

	/** Woher eine Vorschau ihre Texturen bekommt: aus einem Pack oder aus dem Spiel selbst (Vanilla). */
	private interface Src {
		boolean has(String full);

		PreviewCache.Preview get(String full);

		List<String> all();
	}

	private Src src(Group gr, PackInfo pack) {
		if (pack == null) {
			return new Src() {
				@Override
				public boolean has(String full) {
					return previews.vanilla(full) != null;
				}

				@Override
				public PreviewCache.Preview get(String full) {
					return previews.vanilla(full);
				}

				@Override
				public List<String> all() {
					List<String> l = new ArrayList<>();
					for (String rel : gr.previews()) {
						l.add(tex(rel));
					}
					return l;
				}
			};
		}
		List<String> have = index.textures(gr.id(), pack);
		Set<String> hs = new HashSet<>(have);
		return new Src() {
			@Override
			public boolean has(String full) {
				return hs.contains(full);
			}

			@Override
			public PreviewCache.Preview get(String full) {
				return previews.get(pack, full);
			}

			@Override
			public List<String> all() {
				return have;
			}
		};
	}

	private static String tex(String rel) {
		return LegacyPaths.TEX + rel + ".png";
	}

	private static String first(Src s, String... rels) {
		for (String r : rels) {
			String f = tex(r);
			if (s.has(f)) {
				return f;
			}
		}
		return null;
	}

	private static boolean isSky(Group gr) {
		return gr.id().equals("sky");
	}

	/** Hintergrund der Vorschau: hell-blauer Verlauf, beim Himmel ein Nachthimmel. */
	private void plate(GuiGraphicsExtractor g, Group gr, int x, int y, int w, int h, int rTop, int rBottom, float t) {
		if (isSky(gr)) {
			vgrad(g, x, y, w, h, rTop, rBottom, mix(0xFF0A1330, 0xFF13204A, t), 0xFF24407A);
		} else {
			vgrad(g, x, y, w, h, rTop, rBottom, mix(PLATE_TOP, PLATE_TOP_HI, t), PLATE_BOTTOM);
		}
	}

	/** Ausschnitt (in Pixeln der 64er-Referenzgroesse) einer Textur zeichnen. */
	private static void region(GuiGraphicsExtractor g, PreviewCache.Preview p, int x, int y, int dw, int dh, float u,
			float v, float uw, float vh, float ref) {
		float s = p.width() / ref;
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, u * s, v * s, dw, dh, Math.max(1, Math.round(uw * s)),
				Math.max(1, Math.round(vh * s)), p.width(), p.height());
	}

	/** Ganze Textur (bei Animations-Streifen nur das erste Bild) in ein Quadrat einpassen. */
	private void drawTexture(GuiGraphicsExtractor g, PreviewCache.Preview p, int x, int y, int size) {
		if (p == null) {
			return;
		}
		int w = p.width();
		int h = p.height();
		int frameH = h > w ? w : h;
		float scale = Math.min((float) size / w, (float) size / frameH);
		int dw = Math.max(1, Math.round(w * scale));
		int dh = Math.max(1, Math.round(frameH * scale));
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, p.id(), x + (size - dw) / 2, y + (size - dh) / 2, 0, 0, dw, dh, w,
				frameH, w, h);
	}

	/** Main image of a custom sky: the shared imgs sheet, else the first OptiFine/MCPatcher sky image. */
	private static String skySheet(Src s) {
		String best = null;
		for (String f : s.all()) {
			if (!f.endsWith(".png")) {
				continue;
			}
			if (f.startsWith("assets/imgs/")) {
				return f;
			}
			if ((f.contains("/optifine/sky/") || f.contains("/mcpatcher/sky/")) && (best == null || f.compareTo(best) < 0)) {
				best = f;
			}
		}
		return best;
	}

	/** Can custom skies be shown at all? They need Skyboxify, or Nuit together with Nuit Interop. */
	private static boolean skyModPresent() {
		var fl = FabricLoader.getInstance();
		return fl.isModLoaded("skyboxify") || (fl.isModLoaded("nuit") && fl.isModLoaded("nuit_interop"))
				|| fl.isModLoaded("fabricskyboxes");
	}

	/** Nur das erste vorhandene Bild gross zeigen (z. B. Trident: Item statt Modell-Textur). */
	private boolean drawSingle(GuiGraphicsExtractor g, Src s, int x, int y, int w, int h, String... rels) {
		String f = first(s, rels);
		if (f == null) {
			return false;
		}
		int sz = Math.min(w, h);
		drawTexture(g, s.get(f), x + (w - sz) / 2, y + (h - sz) / 2, sz);
		return true;
	}

	/** Bis zu zwei bestimmte Bilder nebeneinander (fehlende werden ausgelassen). */
	private boolean drawPair(GuiGraphicsExtractor g, Src s, int x, int y, int w, int h, String a, String b) {
		if (a == null && b == null) {
			return false;
		}
		if (a == null || b == null) {
			int sz = Math.min(w, h);
			drawTexture(g, s.get(a != null ? a : b), x + (w - sz) / 2, y + (h - sz) / 2, sz);
			return true;
		}
		int sz = Math.min(w / 2, h);
		int sx = x + (w - sz * 2) / 2;
		drawTexture(g, s.get(a), sx, y + (h - sz) / 2, sz);
		drawTexture(g, s.get(b), sx + sz, y + (h - sz) / 2, sz);
		return true;
	}

	private void drawPics(GuiGraphicsExtractor g, Group gr, PackInfo pack, int x, int y, int w, int h) {
		Src s = src(gr, pack);
		boolean done = switch (gr.id()) {
			case "crystal" -> drawCrystal(g, s, x, y, w, h);
			case "shield" -> drawShield(g, s, x, y, w, h);
			case "armor" -> drawArmor(g, s, x, y, w, h);
			case "sky" -> drawSky(g, s, x, y, w, h);
			case "trident" -> drawSingle(g, s, x, y, w, h, "item/trident", "entity/trident/trident", "entity/trident");
			case "windcharge" -> drawPair(g, s, x, y, w, h, first(s, "item/wind_charge"),
					first(s, "particle/gust_0", "particle/small_gust_0"));
			default -> false;
		};
		if (done) {
			return;
		}
		// Standard: bis zu zwei passende Texturen nebeneinander
		List<String> paths = new ArrayList<>();
		for (String rel : gr.previews()) {
			if (s.has(tex(rel))) {
				paths.add(tex(rel));
			}
			if (paths.size() == 2) {
				break;
			}
		}
		if (pack != null) {
			for (String t : s.all()) {
				if (paths.size() >= 2) {
					break;
				}
				if (!paths.contains(t)) {
					paths.add(t);
				}
			}
		}
		if (paths.isEmpty()) {
			ctext(g, pack == null ? "VANILLA" : "-", x + w / 2, y + h / 2 - 4, FAINT);
			return;
		}
		if (paths.size() == 1) {
			int sz = Math.min(w, h);
			drawTexture(g, s.get(paths.get(0)), x + (w - sz) / 2, y + (h - sz) / 2, sz);
		} else {
			int sz = Math.min(w / 2, h);
			int sx = x + (w - sz * 2) / 2;
			drawTexture(g, s.get(paths.get(0)), sx, y + (h - sz) / 2, sz);
			drawTexture(g, s.get(paths.get(1)), sx + sz, y + (h - sz) / 2, sz);
		}
	}

	/** End-Kristall wie im Spiel: gedrehter Glas-Wuerfel mit Kern ueber dem Sockel (aus der Entity-Textur). */
	private boolean drawCrystal(GuiGraphicsExtractor g, Src s, int x, int y, int w, int h) {
		String path = first(s, "entity/end_crystal/end_crystal");
		PreviewCache.Preview p = path == null ? null : s.get(path);
		if (p == null) {
			return false;
		}
		// Masse in Modell-Pixeln: Sockel 12x4x12 (Einheit k), Glas 8x8x8 groesser skaliert (kg = 1.5k) wie im Spiel,
		// Kern darin kleiner. Gesamthoehe ~35k, Breite ~21k (isometrisch).
		float k = Math.min(h / 35f, w / 21f);
		float kg = k * 1.5f;
		float kc = kg * 0.68f;
		float cx = x + w / 2f;
		float baseTop = y + (h + 35 * k) / 2f - 10 * k; // Mitte der Sockel-Oberseite
		box(g, p, cx, baseTop - 6 * k, k, 0, 16, 12, 4, 12, false); // Sockel

		float center = baseTop - 13 * k; // Mitte des schwebenden Kristalls
		box(g, p, cx, center - 8 * kg, kg, 0, 0, 8, 8, 8, true); // Glas: Rueckseiten (durchscheinend)
		box(g, p, cx, center - 8 * kc, kc, 32, 0, 8, 8, 8, false); // Kern
		boxFront(g, p, cx, center - 8 * kg, kg, 0, 0, 8, 8, 8); // Glas: Vorderseiten
		return true;
	}

	private static final float ISO_X = 0.866f;
	private static final float ISO_Y = 0.5f;

	/**
	 * Eine Wuerfelseite als verzerrtes Rechteck: (ox,oy) = Ecke, a/b = Bildschirm-Vektor pro Texel entlang
	 * der Textur-x/y-Richtung, u/v/uw/vh = Ausschnitt in Pixeln der 64er-Referenz, color = Helligkeit/Alpha.
	 */
	private static void face(GuiGraphicsExtractor g, PreviewCache.Preview p, float ox, float oy, float ax, float ay,
			float bx, float by, int u, int v, int uw, int vh, int color) {
		float s = p.width() / 64f;
		g.pose().pushMatrix();
		g.pose().mul(new Matrix3x2f(ax, ay, bx, by, ox, oy));
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, p.id(), 0, 0, u * s, v * s, uw, vh, Math.max(1, Math.round(uw * s)),
				Math.max(1, Math.round(vh * s)), p.width(), p.height(), color);
		g.pose().popMatrix();
	}

	/**
	 * Quader aus einer Minecraft-Modell-Textur (Layout wie ModelPart-Boxen: texOffs u0/v0, Groesse dx/dy/dz)
	 * isometrisch zeichnen. (ox, oy) = obere hintere Ecke auf dem Bildschirm, k = Pixel pro Modell-Pixel.
	 * back = nur die verdeckten Seiten (fuer durchsichtiges Glas), sonst die drei sichtbaren.
	 */
	private static void box(GuiGraphicsExtractor g, PreviewCache.Preview p, float ox, float oy, float k, int u0,
			int v0, int dx, int dy, int dz, boolean back) {
		float exX = ISO_X * k;
		float exY = ISO_Y * k;
		float ezX = -ISO_X * k;
		float ezY = ISO_Y * k;
		if (back) {
			int c = 0xC8A0A0A0;
			face(g, p, ox, oy + dy * k, exX, exY, ezX, ezY, u0 + dz + dx, v0, dx, dz, c); // Boden
			face(g, p, ox, oy, exX, exY, 0, k, u0 + 2 * dz + dx, v0 + dz, dx, dy, c); // hinten
			face(g, p, ox, oy, ezX, ezY, 0, k, u0, v0 + dz, dz, dy, c); // andere Seite
			return;
		}
		boxFront(g, p, ox, oy, k, u0, v0, dx, dy, dz);
	}

	/** Die drei sichtbaren Seiten (oben hell, links mittel, rechts dunkler - wie Minecraft-Licht). */
	private static void boxFront(GuiGraphicsExtractor g, PreviewCache.Preview p, float ox, float oy, float k, int u0,
			int v0, int dx, int dy, int dz) {
		float exX = ISO_X * k;
		float exY = ISO_Y * k;
		float ezX = -ISO_X * k;
		float ezY = ISO_Y * k;
		// rechte Seite (x = dx), verlaeuft entlang z
		face(g, p, ox + dx * exX, oy + dx * exY, ezX, ezY, 0, k, u0 + dz + dx, v0 + dz, dz, dy, 0xFFA8A8A8);
		// linke Seite (z = dz), verlaeuft entlang x
		face(g, p, ox + dz * ezX, oy + dz * ezY, exX, exY, 0, k, u0 + dz, v0 + dz, dx, dy, 0xFFD6D6D6);
		// Oberseite
		face(g, p, ox, oy, exX, exY, ezX, ezY, u0 + dz, v0, dx, dz, 0xFFFFFFFF);
	}

	/** Schild von vorne (Vorderseite aus der Entity-Textur ausgeschnitten). */
	private boolean drawShield(GuiGraphicsExtractor g, Src s, int x, int y, int w, int h) {
		String path = first(s, "entity/shield/shield_base_nopattern", "entity/shield_base_nopattern",
				"entity/shield/shield_base", "entity/shield_base", "entity/shield");
		PreviewCache.Preview p = path == null ? null : s.get(path);
		if (p == null) {
			return false;
		}
		int dh = Math.round(h * 0.95f);
		int dw = Math.round(dh * 12f / 22f);
		region(g, p, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 1, 1, 12, 22, 64);
		return true;
	}

	/** Ruestung: Items, wenn vorhanden; sonst als Figur aus den Ruestungs-Texturen (Kopf, Brust, Arme, Beine). */
	private boolean drawArmor(GuiGraphicsExtractor g, Src s, int x, int y, int w, int h) {
		if (first(s, "item/diamond_chestplate", "item/diamond_helmet", "item/netherite_chestplate",
				"item/iron_chestplate") != null) {
			return false; // Standard-Darstellung mit Items
		}
		String body = null;
		for (String mat : new String[] { "diamond", "netherite", "iron", "gold", "chainmail", "leather" }) {
			body = first(s, "entity/equipment/humanoid/" + mat, "models/armor/" + mat + "_layer_1",
					"models/armor/" + (mat.equals("gold") ? "golden" : mat) + "_layer_1");
			if (body != null) {
				break;
			}
		}
		if (body == null) {
			for (String t : s.all()) {
				if (t.contains("entity/equipment/humanoid/") || t.matches(".*models/armor/[a-z_]+_layer_1\\.png")) {
					body = t;
					break;
				}
			}
		}
		PreviewCache.Preview pb = body == null ? null : s.get(body);
		if (pb == null) {
			return false;
		}
		String legsPath = body.replace("/humanoid/", "/humanoid_leggings/").replace("_layer_1", "_layer_2");
		PreviewCache.Preview pl = s.has(legsPath) ? s.get(legsPath) : null;

		float u = Math.min(h / 33f, w / 17f);
		int ox = x + Math.round((w - 16 * u) / 2);
		int oy = y + Math.round((h - 32 * u) / 2);
		int px4 = Math.round(4 * u);
		int px8 = Math.round(8 * u);
		int px12 = Math.round(12 * u);
		if (pl != null) {
			region(g, pl, ox + px4, oy + px8 + px12, px4, px12, 4, 20, 4, 12, 64); // Beine
			region(g, pl, ox + px8, oy + px8 + px12, px4, px12, 4, 20, 4, 12, 64);
		}
		region(g, pb, ox + px4, oy + px8, px8, px12, 20, 20, 8, 12, 64); // Brust
		region(g, pb, ox, oy + px8, px4, px12, 44, 20, 4, 12, 64); // Arme
		region(g, pb, ox + px4 + px8, oy + px8, px4, px12, 44, 20, 4, 12, 64);
		region(g, pb, ox + px4, oy, px8, px8, 8, 8, 8, 8, 64); // Helm
		return true;
	}

	/** Himmel: Sonne und Vollmond nebeneinander (alte moon_phases-Raster werden auf ein Bild zugeschnitten). */
	private boolean drawSky(GuiGraphicsExtractor g, Src s, int x, int y, int w, int h) {
		// custom skybox: show the horizon (lower row of the 3x2 sky sheet), cropped to the preview's shape
		String sheet = skySheet(s);
		if (sheet != null) {
			PreviewCache.Preview p = s.get(sheet);
			if (p == null) {
				// still loading in the background: three soft pulsing dots instead of a frozen menu
				long ms = System.currentTimeMillis();
				int cxm = x + w / 2;
				int cym = y + h / 2;
				for (int i = 0; i < 3; i++) {
					float a = 0.35f + 0.65f * (float) Math.abs(Math.sin(ms / 300.0 + i * 0.7));
					dot(g, cxm - 9 + i * 7, cym - 2, 4, alpha(0xFFFFFF, (int) (a * 200)));
				}
				return true;
			}
			float srcH = p.height() / 2f;
			float srcW = Math.min(p.width(), srcH * w / (float) Math.max(1, h));
			float u = (p.width() - srcW) / 2f;
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, p.id(), x, y, u, srcH, w, h, Math.round(srcW), Math.round(srcH),
					p.width(), p.height());
			return true;
		}
		String sun = first(s, "environment/celestial/sun", "environment/sun");
		String moon = first(s, "environment/celestial/moon/full_moon", "environment/moon_phases");
		if (sun == null && moon == null) {
			return false;
		}
		int sz = Math.min(sun != null && moon != null ? w / 2 : w, h);
		int count = (sun != null ? 1 : 0) + (moon != null ? 1 : 0);
		int sx = x + (w - sz * count) / 2;
		int sy = y + (h - sz) / 2;
		if (sun != null) {
			drawTexture(g, s.get(sun), sx, sy, sz);
			sx += sz;
		}
		if (moon != null) {
			PreviewCache.Preview p = s.get(moon);
			if (p != null && moon.endsWith("moon_phases.png")) {
				FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, p.id(), sx, sy, 0, 0, sz, sz, p.width() / 4, p.height() / 2,
						p.width(), p.height());
			} else {
				drawTexture(g, p, sx, sy, sz);
			}
		}
		return true;
	}

	// ================================================================== Zeichnen

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta); // Hintergrund; Buttons werden gleich uebermalt
		ensurePalette();
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		mx = mouseX;
		my = mouseY;
		hoverSet = false;
		hoverPack = null;
		hoverThemeName = null;
		hoverAccentName = null;
		navTip = null;

		// Regler ziehen
		if (dragging != 0) {
			if (minecraft.mouseHandler.isLeftPressed()) {
				if (dragging == 1) {
					applyHue(mx);
				} else if (dragging == 2) {
					applyOpacity(mx);
				}
			} else {
				config.save();
				dragging = 0;
			}
		}

		// Installation fertig -> Packs neu einlesen
		if (job != null && job.finished && !jobHandled) {
			jobHandled = true;
			index = PackIndex.scan(minecraft.getResourcePackDirectory());
			focusedPack = activeOf(group());
			if (job.error != null) {
				setStatus("Installation failed: " + job.error, true);
			} else {
				setStatus(job.copied == 0 ? "All packs were already installed" : job.copied + " packs installed",
						false);
			}
			if (modal == Modal.PROGRESS) {
				modal = Modal.DONE;
			}
			rebuild();
		}

		if (zoom) {
			FillBatch.begin();
			try {
				drawZoom(g);
			} finally {
				FillBatch.end(g);
			}
			return;
		}

		FillBatch.begin();
		try {
			drawFrame(g);
			drawHeader(g);
			drawSearchFrame(g);
			drawGridChrome(g);
			paintHits(g, hits);
			drawEmpty(g);
			if (!themesTab) {
				drawInspector(g);
			} else {
				titleText(g, "THEMES", prevPanelX + 9, bodyTop + 9, 1f, menuTheme().light() ? TEXT : 0xFFFFFFFF);
			}
			drawFooter(g);
			drawTip(g);
			if (modal != Modal.NONE) {
				drawModalFrame(g);
				paintHits(g, modalHits);
				drawModalExtras(g);
			}
		} finally {
			FillBatch.end(g);
		}
		if (modal == Modal.NONE) {
			search.extractRenderState(g, mouseX, mouseY, delta);
		}
	}

	private void paintHits(GuiGraphicsExtractor g, List<Hit> list) {
		for (Hit h : list) {
			Button b = h.button();
			boolean hover = b.active && mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY()
					&& my < b.getY() + b.getHeight();
			long key = ((long) b.getX() << 32) | (b.getY() & 0xFFFFFFFFL);
			h.painter().paint(g, ease(key, hover), hover);
		}
	}

	/** Backdrop, nebula window, stars, the glass panels and the logo. */
	private void drawFrame(GuiGraphicsExtractor g) {
		int acc = accent();
		boolean light = menuTheme().light();
		boolean glassy = theme() != null && theme().glassy();
		FillBatch.fill(g, 0, 0, width, height, glassy ? 0x40000000 : DIMMER);
		softGlow(g, px - 30, py - 20, pw + 60, ph + 50, 0xFF000000, glassy ? 0x60 : 0xC0);
		softGlow(g, px - 40, py - 30, pw + 80, ph + 60, acc, light ? 0x10 : 0x28);
		Theme glassTheme = theme();
		if (glassTheme != null && glassTheme.glassy()) {
			// glass theme: the world shows through a lightly tinted, colour-washed pane
			hgrad3(g, px, py, pw, ph, 0, alpha(acc, 0x38), alpha(acc2(), 0x30), alpha(acc3(), 0x38));
			vgrad(g, px, py, pw, ph, 0, 0, alpha(0xFF0B1020, 0x30), alpha(0xFF0B1020, 0x70));
			stars(g);
		} else if (light) {
			vgrad(g, px, py, pw, ph, 8, 8, win(mix(WIN, acc, 0.05f)), win(WIN));
		} else {
			Identifier neb = nebulaTexture();
			if (neb != null) {
				FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, neb, px, py, 0f, 0f, pw, ph, NEB_W, NEB_H, NEB_W,
						NEB_H, alpha(0xFFFFFF, opacity() * 255 / 100));
			} else {
				vgrad(g, px, py, pw, ph, 0, 0, win(mix(WIN, acc, 0.08f)), win(WIN));
			}
			stars(g);
		}
		outline(g, px, py, pw, ph, 0, alpha(mix(WIN_BORDER, acc, 0.6f), 0xE0));
		hgrad(g, px + pw / 5, py, pw * 3 / 10, 1, 0, alpha(acc, 0x00), alpha(mix(acc, 0xFFFFFFFF, 0.5f), 0xD0));
		hgrad(g, px + pw / 2, py, pw * 3 / 10, 1, 0, alpha(mix(acc, 0xFFFFFFFF, 0.5f), 0xD0), alpha(acc, 0x00));

		glass(g, navX, navY, navW, navH, 8);
		glass(g, mainX, railY, mainW, railH, 8);
		glass(g, prevPanelX, bodyTop, previewW, bodyBottom - bodyTop, 8);
		glass(g, cx, gridPanelTop, cw, gridPanelBottom - gridPanelTop, 8);
		if (dockH > 0) {
			glass(g, mainX, dockY, mainW, dockH, 8);
			int ly = dockY + (dockH - 8) / 2;
			titleText(g, "LOADOUT", mainX + 10, ly, 1f, mix(acc, 0xFFFFFFFF, 0.55f));
		}

		// logo on top of the sidebar
		int s = Math.min(24, navW - 8);
		int lx = navX + (navW - s) / 2;
		int ly = navY + 5;
		softGlow(g, lx - 10, ly - 10, s + 20, s + 20, acc2(), 0xA0);
		hgrad3(g, lx, ly, s, s, 6, acc, acc2(), acc3());
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, LOGO, lx + 2, ly + 2, 0, 0, s - 4, s - 4, 64, 64, 64, 64);
	}

	private void drawHeader(GuiGraphicsExtractor g) {
		int acc = accent();
		int x = mainX + 2;
		int y = headY + 1;
		softGlow(g, x - 10, y - 8, Math.round(titleFont.width(TITLE_CAPS) * 1.25f) + 20, 26, acc, 0x50);
		titleText(g, TITLE_CAPS, x, y, 1.25f, menuTheme().light() ? TEXT : 0xFFFFFFFF);
		text(g, subtitle(), x, y + 14, DIM);
	}

	/** Title and count above the grid, "Using ..." next to the pager. */
	private void drawGridChrome(GuiGraphicsExtractor g) {
		Group gr = group();
		int acc = accent();
		int x = cx + 9;
		int y = gridPanelTop + 9;
		Cosmetics.Kind kind = gr.cosmetic();
		int maxW = kind != null ? cw - 18 : cw - 18 - (uiFont.width("All") + uiFont.width("Favorites") + 40);
		String t = gr.label().toUpperCase();
		String t2 = fitTitle(t, Math.max(20, maxW - 50), 1f);
		titleText(g, t2, x, y + 1, 1f, menuTheme().light() ? TEXT : 0xFFFFFFFF);
		int cnt = kind != null ? Cosmetics.list(kind).size() + 1 : shownCount;
		String c = cnt + (kind != null ? " items" : favoritesOnly ? " favorites" : " styles");
		int cxp = x + titleFont.width(t2) + 7;
		if (cxp + uiFont.width(c) < x + maxW) {
			text(g, c, cxp, y + 1, DIM);
		}
		FillBatch.fill(g, cx + 8, gridPanelTop + 24, cx + cw - 8, gridPanelTop + 25, alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0x30));

		String cur;
		if (kind != null) {
			Cosmetics.Item it = Cosmetics.item(kind, Cosmetics.worn(kind));
			cur = it == null ? "None" : it.name();
		} else {
			cur = packName(activeOf(gr));
		}
		int fy = gridPanelBottom - 15;
		String pre = "Using ";
		int pagerW = pageCount() > 1 ? (pageCount() <= 10 ? (pageCount() - 1) * 9 + 18 : 40) + 44 : 0;
		int room = cw - 18 - pagerW;
		if (room > 40) {
			text(g, pre, x, fy, DIM);
			textShadow(g, fit(cur, room - uiFont.width(pre)), x + uiFont.width(pre), fy, mix(acc, 0xFFFFFFFF, 0.45f));
		}
	}

	private void drawEmpty(GuiGraphicsExtractor g) {
		if (index.packs.isEmpty() && group().cosmetic() == null) {
			int w = Math.min(cw - 30, 240);
			int x = cx + (cw - w) / 2;
			int y = gridPanelTop + 40;
			rrect(g, x, y, w, 42, 6, alpha(CARD, 0xE0));
			outline(g, x, y, w, 42, 6, mix(BORDER, accent(), 0.4f));
			ctext(g, "No packs found.", x + w / 2, y + 10, TEXT);
			ctext(g, fit("Click Get Packs or use the folder button.", w - 10), x + w / 2, y + 24, DIM);
		}
	}

	private void drawSearchFrame(GuiGraphicsExtractor g) {
		int acc = accent();
		boolean f = search.isFocused();
		int x = searchX;
		int y = searchY;
		if (f) {
			softGlow(g, x - 8, y - 6, searchW + 16, SEARCH_H + 12, acc, 0x70);
		}
		rrect(g, x, y, searchW, SEARCH_H, 7, alpha(mix(WIN, 0xFF000000, menuTheme().light() ? 0f : 0.35f), 0xB0));
		outline(g, x, y, searchW, SEARCH_H, 7, f ? mix(acc, 0xFFFFFFFF, 0.4f) : alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0x60));
		glyph(g, I_SEARCH, x + 8, y + 6, f ? mix(acc, 0xFFFFFFFF, 0.5f) : DIM);
	}

	private PackInfo displayedPack() {
		if (hoverSet) {
			return hoverPack;
		}
		return focusedPack == null ? null : index.byId(focusedPack);
	}

	/** Small rounded tag; returns its width. */
	private int tag(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean pixel) {
		int w = (pixel ? titleFont.width(s) : uiFont.width(s)) + 10;
		rrect(g, x, y, w, 12, 4, alpha(color, 0x48));
		outline(g, x, y, w, 12, 4, alpha(mix(color, 0xFFFFFFFF, 0.3f), 0x70));
		int tc = menuTheme().light() ? mix(color, 0xFF000000, 0.45f) : mix(color, 0xFFFFFFFF, 0.65f);
		if (pixel) {
			FillBatch.flush(g).text(titleFont, s, x + 5, y + 2, tc, false);
		} else {
			text(g, s, x + 5, y + 2, tc);
		}
		return w;
	}

	/** Dark stage with a spotlight, two slowly turning rings, sparkles and a floor shadow. */
	private void drawStage(GuiGraphicsExtractor g, Group gr) {
		int x = stageX;
		int y = stageY;
		int w = stageW;
		int h = stageH;
		int acc = accent();
		if (gr != null && isSky(gr)) {
			plate(g, gr, x, y, w, h, 7, 7, 0f);
			outline(g, x, y, w, h, 7, alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0x80));
			return;
		}
		if (menuTheme().light()) {
			vgrad(g, x, y, w, h, 7, 7, mix(PLATE_TOP, acc, 0.10f), PLATE_BOTTOM);
		} else {
			vgrad(g, x, y, w, h, 7, 7, alpha(mix(0xFF06030F, acc, 0.16f), 0xF0), alpha(0xFF04020A, 0xF4));
		}
		int ccx = x + w / 2;
		int ccy = y + h / 2;
		int s = Math.min(w, h);
		softGlow(g, ccx - s * 3 / 4, ccy - s * 3 / 4, s * 3 / 2, s * 3 / 2, acc, 0x70);
		softGlow(g, ccx - s / 3, ccy - s / 3, s * 2 / 3, s * 2 / 3, acc2(), 0x50);
		long ms = System.currentTimeMillis();
		float rot = (float) (ms % 24000L / 24000.0 * Math.PI * 2);
		int rx = Math.round(s * 0.44f);
		orbit(g, ccx, ccy, rx, Math.max(4, rx / 3), rot * 0.25f - 0.3f, alpha(mix(acc, 0xFFFFFFFF, 0.5f), 0x60));
		orbit(g, ccx, ccy, rx * 3 / 4, Math.max(3, rx / 4), -rot * 0.4f + 0.5f, alpha(mix(acc2(), 0xFFFFFFFF, 0.4f), 0x50));
		long seed = 0xC0FFEEL;
		for (int i = 0; i < 9; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sx = x + 6 + (int) ((seed >>> 33) % Math.max(1, w - 12));
			int sy = y + 6 + (int) ((seed >>> 13) % Math.max(1, h - 12));
			float tw = 0.5f + 0.5f * (float) Math.sin(ms / 420.0 + i * 1.9);
			int a = (int) (0x20 + tw * 0xC0);
			if (i % 3 == 0) {
				sparkle(g, sx, sy, 2, alpha(mix(acc, 0xFFFFFFFF, 0.6f), a));
			} else {
				FillBatch.fill(g, sx, sy, sx + 1, sy + 1, alpha(0xFFFFFF, a));
			}
		}
		softGlow(g, ccx - s / 3, y + h - h / 6 - 5, s * 2 / 3, 12, 0xFF000000, 0xE0);
		outline(g, x, y, w, h, 7, alpha(mix(acc, 0xFFFFFFFF, 0.35f), 0x70));
	}

	private void drawInspector(GuiGraphicsExtractor g) {
		Group gr = group();
		int x = prevPanelX + 9;
		int w = previewW - 18;
		int acc = accent();
		Cosmetics.Kind kind = gr.cosmetic();
		int ty = bodyTop + 8;
		int tx = x + tag(g, fitTitle(gr.label().toUpperCase(), w / 2, 1f), x, ty, acc, true) + 4;

		if (kind != null) {
			boolean worn = !Cosmetics.NONE.equals(Cosmetics.worn(kind));
			if (tx + 50 < x + w) {
				tag(g, worn ? "Equipped" : "None", tx, ty, worn ? GREEN : FAINT, false);
			}
			drawCosmeticInspector(g, kind);
			return;
		}

		PackInfo p = displayedPack();
		boolean active = Objects.equals(p == null ? null : p.id(), activeOf(gr));
		if (tx + 50 < x + w - 16) {
			tag(g, active ? "Equipped" : hoverSet ? "Preview" : "Not equipped", tx, ty, active ? GREEN : FAINT, false);
		}

		drawStage(g, gr);
		long ms = System.currentTimeMillis();
		if (isSky(gr)) {
			drawPics(g, gr, p, stageX + 2, stageY + 2, stageW - 4, stageH - 4);
		} else {
			int size = Math.min(stageW, stageH) - Math.max(16, Math.min(stageW, stageH) / 4);
			int bob = Math.round((float) Math.sin(ms / 450.0) * Math.max(1, stageH / 40f));
			drawPics(g, gr, p, stageX + (stageW - size) / 2, stageY + (stageH - size) / 2 - 3 + bob, size, size);
		}
		boolean overStage = mx >= stageX && mx < stageX + stageW && my >= stageY && my < stageY + stageH;
		if (overStage && modal == Modal.NONE) {
			glyph(g, I_SEARCH, stageX + 6, stageY + 6, alpha(0xFFFFFF, 0xC0));
		}

		String name = packName(p == null ? null : p.id());
		float sc = titleFont.width(name) * 1.25f <= w ? 1.25f : 1f;
		titleText(g, fitTitle(name, w, sc), x, nameY, sc, menuTheme().light() ? TEXT : 0xFFFFFFFF);
		int n = p == null ? 0 : index.textures(gr.id(), p).size();
		String sub = p == null ? "Minecraft default"
				: isSky(gr) ? "Custom skybox" : n + (n == 1 ? " texture" : " textures") + " · " + p.displayName();
		if (isSky(gr) && !skyModPresent()) {
			sub = "Needs Skyboxify to show skies";
		}
		text(g, fit(sub, w), x, nameY + 14, isSky(gr) && !skyModPresent() ? GOLD : DIM);
		if (showSliders) {
			FillBatch.fill(g, x, sliderTop - 5, x + w, sliderTop - 4, alpha(mix(acc, 0xFFFFFFFF, 0.3f), 0x30));
		}
	}

	/** Status toast that slides in at the top of the window for a few seconds after an action. */
	private void drawFooter(GuiGraphicsExtractor g) {
		long age = System.currentTimeMillis() - statusAt;
		if (status.isEmpty() || age >= 4000) {
			return;
		}
		float a = age < 200 ? age / 200f : age > 3500 ? (4000 - age) / 500f : 1f;
		String s = fit(status, mainW - 60);
		int w = uiFont.width(s) + 30;
		int h = 20;
		int x = mainX + (mainW - w) / 2;
		int y = py + 6 - Math.round((1 - a) * 14);
		int col = statusError ? RED : GREEN;
		int al = Math.round(a * 255);
		softGlow(g, x - 12, y - 8, w + 24, h + 16, col, Math.round(a * 0xA0));
		hgrad(g, x, y, w, h, 7, alpha(mix(col, 0xFF000000, 0.35f), Math.round(a * 0xF0)),
				alpha(mix(col, 0xFF000000, 0.15f), Math.round(a * 0xF0)));
		outline(g, x, y, w, h, 7, alpha(mix(col, 0xFFFFFFFF, 0.4f), al));
		if (al > 8) {
			glyph(g, statusError ? I_CLOSE : I_CHECK, x + 8, y + 7, alpha(0xFFFFFF, Math.max(9, al)));
			textShadow(g, s, x + 20, y + 6, alpha(0xFFFFFF, Math.max(9, al)));
		}
	}

	/** Tooltip for icon-only buttons (small windows). */
	private void drawTip(GuiGraphicsExtractor g) {
		if (navTip == null || modal != Modal.NONE) {
			return;
		}
		int w = uiFont.width(navTip) + 10;
		int x = Math.min(mx + 10, width - w - 2);
		int y = Math.max(2, my - 16);
		rrect(g, x, y, w, 14, 4, 0xF0100A20);
		outline(g, x, y, w, 14, 4, alpha(accent(), 0xC0));
		text(g, navTip, x + 5, y + 3, 0xFFFFFFFF);
	}

	private void drawZoom(GuiGraphicsExtractor g) {
		FillBatch.fill(g, 0, 0, width, height, alpha(WIN, 0xEC));
		Group gr = group();
		PackInfo p = zoomPack;
		int size = Math.max(60, Math.min(width, height) - 96);
		int x = (width - size) / 2;
		int y = 22;
		int acc = accent();
		glow(g, x, y, size, size, 6, 4, acc, 0x10);
		plate(g, gr, x, y, size, size, 6, 6, 0f);
		drawPics(g, gr, p, x + 12, y + 12, size - 24, size - 24);
		String title = packName(p == null ? null : p.id());
		int tw = uiFont.width(title) + 1;
		bold(g, title, (width - tw) / 2, y + size + 12, TEXT);
		ctext(g, gr.label(), width / 2, y + size + 25, accent());
		ctext(g, "Click anywhere to close", width / 2, y + size + 40, FAINT);
	}

	// ================================================================== Eingabe / Lebenszyklus

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// right click = zoom; the button numbers differ between versions (26.3: left 1, right 3), so use the constant
		if (!zoom && modal == Modal.NONE && event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			for (CardRect c : cards) {
				if (event.x() >= c.x() && event.x() < c.x() + c.w() && event.y() >= c.y() && event.y() < c.y() + c.h()) {
					zoomPack = c.pack();
					zoom = true;
					rebuild();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (modal != Modal.NONE) {
			return true;
		}
		if (!zoom && (search == null || !search.isFocused())) {
			int cp = event.codepoint();
			if (cp == '/') {
				setFocused(search);
				return true;
			}
			if (cp == 'f' || cp == 'F') {
				PackInfo p = hoverSet ? hoverPack : null;
				toggleFavorite(p != null ? p.id() : focusedPack);
				return true;
			}
		}
		return super.charTyped(event);
	}

	/** Mausrad: ueber der Sammlungsliste scrollt die Liste, sonst das Raster. Absichtlich ohne @Override. */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (zoom) {
			return true;
		}
		int dir = scrollY > 0 ? -1 : 1;
		if (modal != Modal.NONE) {
			if (modal == Modal.SELECT) {
				pickScroll += dir;
				rebuild();
			}
			return true;
		}
		if (mouseY >= railY && mouseY < railY + railH && mouseX >= mainX) {
			int max = Math.max(0, railSize - railVisible);
			int next = Math.max(0, Math.min(max, railScroll + dir));
			if (next == railScroll) {
				return true;
			}
			railScroll = next;
			railFollow = groupId;
		} else if (mouseX >= cx && mouseX < mainR && mouseY >= gridPanelTop && mouseY < gridPanelBottom && !themesTab) {
			int pages = pageCount();
			int cur = gridScroll / Math.max(1, rowsVisible);
			int next = Math.max(0, Math.min(pages - 1, cur + dir));
			if (next == cur) {
				return true;
			}
			gridScroll = next * rowsVisible;
		} else {
			return true;
		}
		rebuild();
		return true;
	}

	@Override
	public void removed() {
		previews.close();
		for (Identifier[] ids : wingTextures.values()) {
			for (Identifier id : ids) {
				minecraft.getTextureManager().release(id);
			}
		}
		wingTextures.clear();
		if (glowId != null) {
			minecraft.getTextureManager().release(glowId);
			glowId = null;
		}
		if (nebulaId != null) {
			minecraft.getTextureManager().release(nebulaId);
			nebulaId = null;
		}
		super.removed();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
