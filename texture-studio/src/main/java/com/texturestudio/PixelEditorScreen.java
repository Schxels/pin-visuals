package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Studio -> Pixel Editor: pick a texture, paint over its pixels right in the menu and save. The edited texture is
 * stored in config/pinvisuals/edits and copied into the Mix pack (on top of everything), so it shows in game.
 * <p>
 * Cape mode (Studio -> Cape Designer) edits the 10x16 design of your own cape instead.
 */
final class PixelEditorScreen extends ToolScreen {

	private static final String[] TOOLS = { "Pen", "Erase", "Pick", "Fill" };
	private static final int MAX_SIDE = 128;

	private final boolean cape;
	private List<String> all;
	private final List<String> shown = new ArrayList<>();
	private String filter = "";
	private int listScroll;
	private int listRows;

	private String path; // e.g. minecraft:item/diamond_sword (null = nothing open)
	private int[] px;
	private int iw;
	private int ih;
	private int tool;
	private int color = 0xFFFFFFFF;
	private boolean grid = true;
	private final Deque<int[]> undo = new ArrayDeque<>();
	private int[] palette = new int[0];
	private String message = "";
	private long messageAt;
	private boolean changedPixels;

	private int canvasX;
	private int canvasY;
	private int cell;

	PixelEditorScreen(TextureStudioScreen home, Screen back, boolean cape) {
		super(cape ? "Cape Designer" : "Pixel Editor", home, back);
		this.cape = cape;
		if (cape) {
			px = CapeStore.load();
			iw = CapeStore.W;
			ih = CapeStore.H;
			path = "cape";
			palette = paletteOf(px);
		}
	}

	@Override
	protected String subtitle() {
		if (cape) {
			return "Your own cape - only you see it";
		}
		return path == null ? "Pick a texture on the left" : path + "  (" + iw + "x" + ih + ")";
	}

	@Override
	protected int preferredWidth() {
		return 520;
	}

	@Override
	protected int preferredHeight() {
		return 320;
	}

	// ------------------------------------------------------------------ layout

	private int listW() {
		return cape ? 0 : Math.min(150, ww / 3);
	}

	private int sideW() {
		return 132;
	}

	private int sideX() {
		return wx + ww - 12 - sideW();
	}

	@Override
	protected void build() {
		int y0 = top();
		if (!cape) {
			int lx = wx + 12;
			int lw = listW();
			editBox(lx, y0, lw, "Search textures", filter, s -> {
				filter = s.toLowerCase(Locale.ROOT).trim();
				listScroll = 0;
				refilter();
				rebuildKeepFocus();
			});
			if (all == null) {
				all = scan();
				refilter();
			}
			int rowH = 13;
			int ly = y0 + 20;
			listRows = Math.max(1, (wy + wh - 12 - ly) / rowH);
			listScroll = Math.max(0, Math.min(listScroll, Math.max(0, shown.size() - listRows)));
			for (int i = 0; i < listRows; i++) {
				int idx = listScroll + i;
				if (idx >= shown.size()) {
					break;
				}
				String p = shown.get(idx);
				int ry = ly + i * rowH;
				add(lx, ry, lw, rowH - 1, () -> open(p), null, (g, hov, pr) -> {
					boolean on = p.equals(path);
					if (on) {
						TextureStudioScreen.rrect(g, lx, ry, lw, rowH - 1, 3,
								TextureStudioScreen.alpha(home.accent(), 0x60));
					} else if (hov) {
						TextureStudioScreen.rrect(g, lx, ry, lw, rowH - 1, 3, home.CARD_HI);
					}
					String name = p.startsWith("minecraft:") ? p.substring(10) : p;
					boolean edited = Files.isRegularFile(editFile(p));
					int tw = lw - 8 - (edited ? 8 : 0);
					home.text(g, home.uiFont.plainSubstrByWidth(name, tw), lx + 4, ry + 2,
							on ? home.TEXT : home.SOFT);
					if (edited) {
						TextureStudioScreen.rrect(g, lx + lw - 8, ry + 4, 4, 4, 2, 0xFF22C55E);
					}
				});
			}
		}

		// tools on the right
		int sx = sideX();
		int sw = sideW();
		int y = y0;
		if (px == null) {
			return;
		}
		segment(sx, y, sw, 16, TOOLS, () -> tool, v -> tool = v);
		y += 22;
		colorPicker(sx, y, sw, () -> color, v -> {
			color = v;
			if (tool == 1 || tool == 2) {
				tool = 0;
			}
		});
		y += 52;
		// colours used in the texture
		int n = Math.min(palette.length, 14);
		int s = 12;
		for (int i = 0; i < n; i++) {
			int c = palette[i];
			int cx = sx + (i % 7) * (s + 4);
			int cy = y + (i / 7) * (s + 4);
			add(cx, cy, s, s, () -> {
				color = c;
				tool = 0;
			}, null, (g, hov, p) -> {
				if (hov || (color == c)) {
					TextureStudioScreen.rrect(g, cx - 2, cy - 2, s + 4, s + 4, 4, color == c ? 0xFFFFFFFF : home.accent());
				}
				TextureStudioScreen.rrect(g, cx, cy, s, s, 3, c | 0xFF000000);
			});
		}
		y += n > 7 ? 34 : 18;
		toggle(sx, y, sw, "Grid", () -> grid, v -> grid = v);
		y += 18;
		int half = (sw - 4) / 2;
		button(sx, y, half, 16, "Undo", false, this::undo);
		button(sx + half + 4, y, sw - half - 4, 16, cape ? "Clear" : "Reset", false, this::reset);
		y += 20;
		button(sx, y, sw, 18, cape ? "Save & wear" : "Save to Mix", true, this::save);

		// canvas
		int ax = wx + 12 + (cape ? 0 : listW() + 10);
		int aw = sx - 10 - ax;
		int ah = wy + wh - 12 - y0;
		cell = Math.max(1, Math.min(aw / iw, ah / ih));
		cell = Math.min(cell, 24);
		canvasX = ax + (aw - cell * iw) / 2;
		canvasY = y0 + (ah - cell * ih) / 2;
		add(canvasX, canvasY, cell * iw, cell * ih, () -> {
			pushUndo();
			if (tool == 3) {
				fillAt(mx, my);
			}
		}, (x, yy) -> {
			if (tool != 3) {
				paintAt(x, yy);
			}
		}, (g, hov, p) -> {
		});
	}

	private void rebuildKeepFocus() {
		// the search box keeps its text; rebuilding recreates it, so give it the focus back
		rebuild();
		for (var c : children()) {
			if (c instanceof net.minecraft.client.gui.components.EditBox b) {
				setFocused(b);
				b.setFocused(true);
			}
		}
	}

	// ------------------------------------------------------------------ texture list

	private List<String> scan() {
		List<String> out = new ArrayList<>();
		try {
			Map<Identifier, Resource> found = minecraft.getResourceManager().listResources("textures",
					id -> id.getPath().endsWith(".png"));
			for (Identifier id : found.keySet()) {
				String p = id.getPath();
				String rel = p.substring("textures/".length(), p.length() - 4);
				if (rel.startsWith("block/") || rel.startsWith("item/") || rel.startsWith("entity/")
						|| rel.startsWith("particle/") || rel.startsWith("mob_effect/") || rel.startsWith("painting/")) {
					out.add(id.getNamespace() + ":" + rel);
				}
			}
		} catch (RuntimeException e) {
			TextureStudioClient.LOGGER.warn("Pin Visuals: texture list failed", e);
		}
		out.sort((a, b) -> {
			int ca = a.startsWith("minecraft:") ? 0 : 1;
			int cb = b.startsWith("minecraft:") ? 0 : 1;
			return ca != cb ? ca - cb : a.compareTo(b);
		});
		return out;
	}

	private void refilter() {
		shown.clear();
		if (all == null) {
			return;
		}
		for (String p : all) {
			if (filter.isEmpty() || p.contains(filter)) {
				shown.add(p);
			}
		}
	}

	@Override
	protected void onScroll(int dir) {
		if (!cape && mx < wx + 12 + listW() + 4) {
			listScroll = Math.max(0, Math.min(Math.max(0, shown.size() - listRows), listScroll + dir * 3));
			rebuildKeepFocus();
		}
	}

	private static Identifier idOf(String p, String suffix) {
		int c = p.indexOf(':');
		return Identifier.fromNamespaceAndPath(p.substring(0, c), "textures/" + p.substring(c + 1) + suffix);
	}

	private static Path editFile(String p) {
		int c = p.indexOf(':');
		return MixBuilder.editsDir().resolve("assets").resolve(p.substring(0, c))
				.resolve("textures/" + p.substring(c + 1) + ".png");
	}

	private void open(String p) {
		if (changedPixels && px != null && !cape) {
			say("Unsaved changes were dropped");
		}
		try {
			Optional<Resource> r = minecraft.getResourceManager().getResource(idOf(p, ".png"));
			if (r.isEmpty()) {
				say("Texture not found");
				return;
			}
			try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
				if (img.getWidth() > MAX_SIDE || img.getHeight() > MAX_SIDE * 4
						|| img.getWidth() * img.getHeight() > MAX_SIDE * MAX_SIDE) {
					say("Too big to edit (" + img.getWidth() + "x" + img.getHeight() + ")");
					return;
				}
				iw = img.getWidth();
				ih = img.getHeight();
				px = new int[iw * ih];
				for (int y = 0; y < ih; y++) {
					for (int x = 0; x < iw; x++) {
						px[y * iw + x] = img.getPixel(x, y);
					}
				}
			}
			path = p;
			undo.clear();
			changedPixels = false;
			palette = paletteOf(px);
			rebuildKeepFocus();
		} catch (Exception e) {
			say("Could not open: " + e.getMessage());
		}
	}

	private static int[] paletteOf(int[] px) {
		Map<Integer, Integer> count = new HashMap<>();
		for (int c : px) {
			if (((c >>> 24) & 255) > 20) {
				count.merge(c | 0xFF000000, 1, Integer::sum);
			}
		}
		return count.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(14)
				.mapToInt(Map.Entry::getKey).toArray();
	}

	// ------------------------------------------------------------------ painting

	private int pixelAt(int x, int y) {
		int cx = Math.floorDiv(x - canvasX, Math.max(1, cell));
		int cy = Math.floorDiv(y - canvasY, Math.max(1, cell));
		if (cx < 0 || cy < 0 || cx >= iw || cy >= ih) {
			return -1;
		}
		return cy * iw + cx;
	}

	private void paintAt(int x, int y) {
		int i = pixelAt(x, y);
		if (i < 0 || px == null) {
			return;
		}
		switch (tool) {
			case 1 -> set(i, cape ? 0xFF000000 : 0);
			case 2 -> {
				if (((px[i] >>> 24) & 255) > 0) {
					color = px[i] | 0xFF000000;
				}
			}
			default -> set(i, color | 0xFF000000);
		}
	}

	private void set(int i, int c) {
		if (px[i] != c) {
			px[i] = c;
			changedPixels = true;
		}
	}

	private void fillAt(int x, int y) {
		int start = pixelAt(x, y);
		if (start < 0) {
			return;
		}
		int from = px[start];
		int to = color | 0xFF000000;
		if (from == to) {
			return;
		}
		ArrayDeque<Integer> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty()) {
			int i = q.poll();
			if (px[i] != from) {
				continue;
			}
			px[i] = to;
			int cx = i % iw;
			int cy = i / iw;
			if (cx > 0) {
				q.add(i - 1);
			}
			if (cx < iw - 1) {
				q.add(i + 1);
			}
			if (cy > 0) {
				q.add(i - iw);
			}
			if (cy < ih - 1) {
				q.add(i + iw);
			}
		}
		changedPixels = true;
	}

	private void pushUndo() {
		if (px == null) {
			return;
		}
		undo.push(px.clone());
		while (undo.size() > 40) {
			undo.removeLast();
		}
	}

	private void undo() {
		if (!undo.isEmpty()) {
			px = undo.pop();
			changedPixels = true;
		}
	}

	private void reset() {
		if (cape) {
			pushUndo();
			java.util.Arrays.fill(px, 0xFF1E1B2E);
			changedPixels = true;
			return;
		}
		if (path == null) {
			return;
		}
		Path f = editFile(path);
		try {
			boolean had = Files.deleteIfExists(f);
			Files.deleteIfExists(Path.of(f + ".mcmeta"));
			if (had) {
				rebuildMix("Your edit was removed");
			}
			open(path);
		} catch (Exception e) {
			say("Reset failed: " + e.getMessage());
		}
	}

	private void save() {
		if (px == null) {
			return;
		}
		try {
			if (cape) {
				CapeStore.save(px);
				Cosmetics.wear(cfg, Cosmetics.Kind.CAPE, "custom");
				changedPixels = false;
				say("Saved - you are wearing your cape");
				return;
			}
			Path f = editFile(path);
			Files.createDirectories(f.getParent());
			try (NativeImage img = new NativeImage(iw, ih, false)) {
				for (int y = 0; y < ih; y++) {
					for (int x = 0; x < iw; x++) {
						img.setPixel(x, y, px[y * iw + x]);
					}
				}
				img.writeToFile(f);
			}
			// animation data travels with the picture (else an animated texture would show as a strip)
			Optional<Resource> meta = minecraft.getResourceManager().getResource(idOf(path, ".png.mcmeta"));
			if (meta.isPresent()) {
				try (InputStream in = meta.get().open()) {
					Files.write(Path.of(f + ".mcmeta"), in.readAllBytes());
				}
			}
			changedPixels = false;
			rebuildMix("Saved - it is in your Mix now");
		} catch (Exception e) {
			say("Saving failed: " + e.getMessage());
		}
	}

	private void rebuildMix(String done) {
		try {
			PackIndex idx = home.index != null ? home.index : PackIndex.scan(minecraft.getResourcePackDirectory());
			MixBuilder.apply(minecraft, idx, cfg);
			say(done);
		} catch (Exception e) {
			say("Mix could not be rebuilt: " + e.getMessage());
		}
	}

	private void say(String s) {
		message = s;
		messageAt = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void drawContent(GuiGraphicsExtractor g) {
		if (!cape) {
			int lx = wx + 12;
			if (shown.isEmpty()) {
				home.text(g, "No textures found", lx + 4, top() + 24, home.DIM);
			} else if (shown.size() > listRows) {
				int ly = top() + 20;
				int lh = wy + wh - 12 - ly;
				int bar = Math.max(10, lh * listRows / shown.size());
				int by = ly + (lh - bar) * listScroll / Math.max(1, shown.size() - listRows);
				TextureStudioScreen.rrect(g, lx + listW() + 2, by, 2, bar, 1, home.BORDER);
			}
		}
		if (px == null) {
			int ax = wx + 12 + listW() + 10;
			home.ctext(g, "Choose a texture to start", (ax + wx + ww - 12) / 2, wy + wh / 2, home.DIM);
			return;
		}
		int cw = cell * iw;
		int ch = cell * ih;
		TextureStudioScreen.outline(g, canvasX - 2, canvasY - 2, cw + 4, ch + 4, 3, home.BORDER);
		// checkerboard for transparent pixels
		int ck = Math.max(2, cell / 2);
		for (int y = 0; y < ch; y += ck) {
			for (int x = 0; x < cw; x += ck) {
				boolean dark = ((x / ck) + (y / ck)) % 2 == 0;
				FillBatch.fill(g, canvasX + x, canvasY + y, canvasX + Math.min(cw, x + ck), canvasY + Math.min(ch, y + ck),
						dark ? 0xFF2A2833 : 0xFF3A3846);
			}
		}
		for (int y = 0; y < ih; y++) {
			int x = 0;
			while (x < iw) {
				int c = px[y * iw + x];
				int e = x + 1;
				while (e < iw && px[y * iw + e] == c) {
					e++;
				}
				if (((c >>> 24) & 255) > 0) {
					FillBatch.fill(g, canvasX + x * cell, canvasY + y * cell, canvasX + e * cell, canvasY + (y + 1) * cell, c);
				}
				x = e;
			}
		}
		if (grid && cell >= 6) {
			int gc = 0x22FFFFFF;
			for (int x = 1; x < iw; x++) {
				FillBatch.fill(g, canvasX + x * cell, canvasY, canvasX + x * cell + 1, canvasY + ch, gc);
			}
			for (int y = 1; y < ih; y++) {
				FillBatch.fill(g, canvasX, canvasY + y * cell, canvasX + cw, canvasY + y * cell + 1, gc);
			}
		}
		int hi = pixelAt(mx, my);
		if (hi >= 0) {
			int hx = canvasX + (hi % iw) * cell;
			int hy = canvasY + (hi / iw) * cell;
			TextureStudioScreen.outline(g, hx - 1, hy - 1, cell + 2, cell + 2, 0, 0xFFFFFFFF);
		}
		if (cape) {
			home.ctext(g, "Back of the cape (what others see)", canvasX + cw / 2, canvasY + ch + 4, home.DIM);
		}
	}

	@Override
	protected void drawOver(GuiGraphicsExtractor g) {
		if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 4000) {
			int w = home.uiFont.width(message) + 16;
			int x = wx + (ww - w) / 2;
			int y = wy + wh - 26;
			TextureStudioScreen.rrect(g, x, y, w, 16, 5, TextureStudioScreen.alpha(home.CARD, 0xF0));
			TextureStudioScreen.outline(g, x, y, w, 16, 5, home.accent());
			home.ctext(g, message, x + w / 2, y + 4, home.TEXT);
		}
	}
}
