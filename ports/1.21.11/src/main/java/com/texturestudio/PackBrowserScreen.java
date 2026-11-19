package com.texturestudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Studio -> Pack Browser: searches resource packs on Modrinth (api.modrinth.com), shows them with icon and
 * description and downloads one with a click into the resourcepacks folder (SHA-1 checked).
 */
final class PackBrowserScreen extends ToolScreen {

	private static final String API = "https://api.modrinth.com/v2";
	private static final String AGENT = "Schxels/pin-visuals/2.0.0 (github.com/Schxels/pin-visuals)";
	private static final int PAGE = 12;
	private static final String[] SORTS = { "Relevance", "Downloads", "Newest" };
	private static final String[] SORT_IDS = { "relevance", "downloads", "newest" };
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	/** Icons stay loaded while the game runs (id -> texture, or null while loading / failed). */
	private static final Map<String, Icon> ICONS = new HashMap<>();
	private static final Set<String> ICON_LOADING = new HashSet<>();

	record Icon(Identifier id, int w, int h) {
	}

	record Hit(String id, String title, String author, String description, long downloads, String icon) {
	}

	private final String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	private String query = "";
	private int sort;
	private int page;
	private int total;
	private List<Hit> hits = new ArrayList<>();
	private Hit selected;
	private boolean loading;
	private String error;
	private int request;
	private long searchAt;
	/** project id -> status text of its download ("Downloading", "Installed", error). */
	private final Map<String, String> downloads = new HashMap<>();

	PackBrowserScreen(TextureStudioScreen home, Screen back) {
		super("Pack Browser", home, back);
		search();
	}

	@Override
	protected String subtitle() {
		return "Resource packs from Modrinth" + (mcVersion.isEmpty() ? "" : " for " + mcVersion);
	}

	@Override
	protected int preferredWidth() {
		return 540;
	}

	@Override
	protected int preferredHeight() {
		return 330;
	}

	private int listW() {
		return Math.min(260, ww / 2);
	}

	@Override
	protected void build() {
		int x = wx + 12;
		int y = top();
		int lw = listW();
		editBox(x, y, lw - 4, "Search packs ...", query, s -> {
			if (!s.trim().equals(query)) {
				query = s.trim();
				page = 0;
				searchAt = System.currentTimeMillis() + 450; // wait until typing pauses
			}
		});
		int sx = x + lw + 6;
		segment(sx, y, wx + ww - 12 - sx, 16, SORTS, () -> sort, v -> {
			sort = v;
			page = 0;
			search();
		});
		int rowH = 30;
		int ly = y + 22;
		int rows = Math.min(PAGE, Math.max(1, (wy + wh - 34 - ly) / rowH));
		for (int i = 0; i < Math.min(rows, hits.size()); i++) {
			Hit h = hits.get(i);
			int ry = ly + i * rowH;
			add(x, ry, lw - 4, rowH - 3, () -> selected = h, null, (g, hov, p) -> {
				boolean on = selected != null && selected.id().equals(h.id());
				TextureStudioScreen.rrect(g, x, ry, lw - 4, rowH - 3, 5,
						on ? TextureStudioScreen.alpha(home.accent(), 0x55) : hov ? home.CARD_HI : home.CARD);
				if (on) {
					TextureStudioScreen.outline(g, x, ry, lw - 4, rowH - 3, 5, home.accent());
				}
				icon(g, h, x + 3, ry + 2, 23);
				home.text(g, home.uiFont.plainSubstrByWidth(h.title(), lw - 40), x + 31, ry + 4, home.TEXT);
				String info = h.author() + " · " + shortNum(h.downloads()) + " downloads";
				home.text(g, home.uiFont.plainSubstrByWidth(info, lw - 40), x + 31, ry + 15, home.DIM);
				String st = downloads.get(h.id());
				if ("Installed".equals(st)) {
					TextureStudioScreen.rrect(g, x + lw - 14, ry + 4, 5, 5, 2, 0xFF22C55E);
				}
			});
		}
		// paging
		int by = wy + wh - 28;
		int pages = Math.max(1, (total + PAGE - 1) / PAGE);
		button(x, by, 50, 16, "< Prev", false, () -> {
			if (page > 0) {
				page--;
				search();
			}
		});
		button(x + lw - 54, by, 50, 16, "Next >", false, () -> {
			if (page < pages - 1) {
				page++;
				search();
			}
		});
		// download button for the selection
		if (selected != null) {
			Hit h = selected;
			int dx = sx;
			int dw = wx + ww - 12 - dx;
			String st = downloads.get(h.id());
			boolean busy = "Downloading".equals(st);
			String label = "Installed".equals(st) ? "Installed - download again" : busy ? "Downloading ..." : "Download";
			button(dx, by, dw, 18, label, !busy, () -> {
				if (!busy) {
					download(h);
				}
			});
		}
	}

	// ------------------------------------------------------------------ Modrinth

	private void search() {
		int my = ++request;
		loading = true;
		error = null;
		String facets = "[[\"project_type:resourcepack\"]" + (mcVersion.isEmpty() ? "" : ",[\"versions:" + mcVersion + "\"]")
				+ "]";
		String url = API + "/search?limit=" + PAGE + "&offset=" + page * PAGE + "&index=" + SORT_IDS[sort] + "&facets="
				+ enc(facets) + (query.isEmpty() ? "" : "&query=" + enc(query));
		getJson(url).whenComplete((json, err) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (my != request) {
				return;
			}
			loading = false;
			if (err != null || json == null) {
				error = "Modrinth not reachable";
				hits = new ArrayList<>();
			} else {
				JsonObject o = json.getAsJsonObject();
				total = o.has("total_hits") ? o.get("total_hits").getAsInt() : 0;
				List<Hit> list = new ArrayList<>();
				for (JsonElement e : o.getAsJsonArray("hits")) {
					JsonObject h = e.getAsJsonObject();
					list.add(new Hit(str(h, "project_id"), str(h, "title"), str(h, "author"), str(h, "description"),
							h.has("downloads") ? h.get("downloads").getAsLong() : 0, str(h, "icon_url")));
				}
				hits = list;
				if (selected == null || list.stream().noneMatch(x -> x.id().equals(selected.id()))) {
					selected = list.isEmpty() ? null : list.get(0);
				}
			}
			if (Nav.current() == this) {
				rebuildKeepSearch();
			}
		}));
	}

	private void rebuildKeepSearch() {
		boolean focused = getFocused() instanceof net.minecraft.client.gui.components.EditBox;
		rebuild();
		if (focused) {
			for (var c : children()) {
				if (c instanceof net.minecraft.client.gui.components.EditBox b) {
					setFocused(b);
					b.setFocused(true);
				}
			}
		}
	}

	private void download(Hit h) {
		downloads.put(h.id(), "Downloading");
		rebuildKeepSearch();
		String vers = mcVersion.isEmpty() ? "" : "?game_versions=" + enc("[\"" + mcVersion + "\"]");
		getJson(API + "/project/" + h.id() + "/version" + vers).thenCompose(json -> {
			JsonArray arr = json == null ? null : json.getAsJsonArray();
			if ((arr == null || arr.isEmpty()) && !vers.isEmpty()) {
				return getJson(API + "/project/" + h.id() + "/version"); // no build for this version: newest
			}
			return CompletableFuture.completedFuture(json);
		}).thenCompose(json -> {
			JsonArray arr = json.getAsJsonArray();
			if (arr.isEmpty()) {
				throw new IllegalStateException("no files");
			}
			JsonArray files = arr.get(0).getAsJsonObject().getAsJsonArray("files");
			JsonObject file = files.get(0).getAsJsonObject();
			for (JsonElement f : files) {
				if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
					file = f.getAsJsonObject();
				}
			}
			String url = str(file, "url");
			String name = str(file, "filename").replaceAll("[^A-Za-z0-9._ +()-]", "_");
			String sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
			if (!url.startsWith("https://cdn.modrinth.com/") || name.isEmpty()) {
				throw new IllegalStateException("unexpected download");
			}
			return getBytes(url).thenApply(bytes -> {
				try {
					String got = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
					if (!got.equalsIgnoreCase(sha1)) {
						throw new IllegalStateException("checksum does not match");
					}
					Path dir = net.minecraft.client.Minecraft.getInstance().getResourcePackDirectory();
					Files.createDirectories(dir);
					Files.write(dir.resolve(name.endsWith(".zip") ? name : name + ".zip"), bytes);
					return name;
				} catch (Exception e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			});
		}).whenComplete((name, err) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				downloads.put(h.id(), "Failed: " + c.getMessage());
			} else {
				downloads.put(h.id(), "Installed");
				home.packsChanged();
			}
			if (Nav.current() == this) {
				rebuildKeepSearch();
			}
		}));
	}

	private static CompletableFuture<JsonElement> getJson(String url) {
		return getBytes(url).thenApply(b -> JsonParser.parseString(new String(b, StandardCharsets.UTF_8)));
	}

	private static CompletableFuture<byte[]> getBytes(String url) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT)
				.timeout(Duration.ofSeconds(60)).GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
			if (r.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + r.statusCode());
			}
			return r.body();
		});
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static String shortNum(long n) {
		if (n >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
		}
		if (n >= 1_000) {
			return String.format(Locale.ROOT, "%.1fk", n / 1_000.0);
		}
		return Long.toString(n);
	}

	// ------------------------------------------------------------------ icons

	private void icon(GuiGraphics g, Hit h, int x, int y, int s) {
		Icon ic = ICONS.get(h.id());
		if (ic != null) {
			FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, ic.id(), x, y, 0f, 0f, s, s, ic.w(), ic.h(), ic.w(),
					ic.h());
			return;
		}
		int c = TextureStudioScreen.hsv((h.id().hashCode() & 0xFFFF) / 65535f, 0.55f, 0.85f);
		TextureStudioScreen.hgrad(g, x, y, s, s, 4, c, TextureStudioScreen.mix(c, 0xFF000000, 0.4f));
		String l = h.title().isEmpty() ? "?" : h.title().substring(0, 1).toUpperCase(Locale.ROOT);
		home.ctext(g, l, x + s / 2, y + (s - 8) / 2, 0xFFFFFFFF);
		if (!h.icon().isEmpty() && ICON_LOADING.add(h.id())) {
			getBytes(h.icon()).thenApply(PackBrowserScreen::decode).whenComplete((img, err) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
				if (img == null) {
					return; // webp or broken: the letter stays
				}
				Identifier tid = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID,
						"modrinth/" + h.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_"));
				int iw = img.getWidth();
				int ih = img.getHeight();
				net.minecraft.client.Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(() -> "modrinth icon", img));
				ICONS.put(h.id(), new Icon(tid, iw, ih));
			}));
		}
	}

	/** PNG directly, JPEG / GIF through ImageIO; anything else (WebP) gives null. */
	private static NativeImage decode(byte[] b) {
		try {
			return NativeImage.read(new ByteArrayInputStream(b));
		} catch (Exception ignored) {
			// not a PNG
		}
		try {
			java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(b));
			if (bi == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			javax.imageio.ImageIO.write(bi, "png", out);
			return NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
		} catch (Exception e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void drawContent(GuiGraphics g) {
		if (searchAt != 0 && System.currentTimeMillis() >= searchAt) {
			searchAt = 0;
			search();
		}
		int x = wx + 12;
		int lw = listW();
		int ly = top() + 22;
		if (loading && hits.isEmpty()) {
			home.ctext(g, "Loading ...", x + lw / 2, ly + 20, home.DIM);
		} else if (error != null) {
			home.ctext(g, error, x + lw / 2, ly + 20, 0xFFEF4444);
		} else if (hits.isEmpty()) {
			home.ctext(g, "No packs found", x + lw / 2, ly + 20, home.DIM);
		}
		int pages = Math.max(1, (total + PAGE - 1) / PAGE);
		home.ctext(g, (page + 1) + " / " + pages, x + lw / 2 - 2, wy + wh - 24, home.DIM);

		// details on the right
		int dx = x + lw + 6;
		int dw = wx + ww - 12 - dx;
		int dy = ly;
		int dh = wy + wh - 34 - dy;
		card(g, dx, dy, dw, dh);
		Hit h = selected;
		if (h == null) {
			return;
		}
		icon(g, h, dx + 8, dy + 8, 40);
		home.bold(g, home.uiFont.plainSubstrByWidth(h.title(), dw - 60), dx + 54, dy + 11, home.TEXT);
		home.text(g, home.uiFont.plainSubstrByWidth("by " + h.author(), dw - 60), dx + 54, dy + 23, home.DIM);
		home.text(g, shortNum(h.downloads()) + " downloads", dx + 54, dy + 35, home.FAINT);
		int ty = dy + 56;
		for (String line : wrap(h.description(), dw - 16)) {
			if (ty > dy + dh - 24) {
				break;
			}
			home.text(g, line, dx + 8, ty, home.SOFT);
			ty += 11;
		}
		String st = downloads.get(h.id());
		if (st != null && !"Downloading".equals(st)) {
			boolean ok = "Installed".equals(st);
			String msg = ok ? "In your packs - pick it in a collection" : st;
			home.text(g, home.uiFont.plainSubstrByWidth(msg, dw - 16), dx + 8, dy + dh - 14,
					ok ? 0xFF22C55E : 0xFFEF4444);
		}
	}

	private List<String> wrap(String s, int w) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split("\\s+")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (home.uiFont.width(next) > w && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}
}
