package com.texturestudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * Cosmetics (Umhaenge, Huete, Fluegel). Sie sind rein clientseitig: nur der Spieler selbst sieht sie.
 * Huete und Fluegel sind kleine Pixel-Modelle aus farbigen Quadern/Pixeln, Umhaenge sind Texturen
 * (assets/texturestudio/textures/cosmetics/cape_*.png, erzeugt von tools/CosmeticArt.java).
 */
public final class Cosmetics {

	public enum Kind {
		CAPE, HAT, WINGS,
		/** Effect played where a player you killed dies (see KillEffects). */
		KILL,
		/** Style of the Spotify sing-along lyrics in the sky (see Lyrics); NONE = off. */
		LYRICS,
		/** Armor durability HUD next to the hotbar (see ArmorHud); NONE = off. */
		ARMOR,
		/** Spotify now-playing card in a top corner (see SpotifyHud); NONE = off. */
		SPOTIFY,
		/** Colour of the flash when a mob or player is hit (see HitColor); NONE = vanilla red. */
		HIT,
		/** Coloured ghost when another player pops a totem (see PopChams); NONE = off. */
		POP
	}

	public record Item(String id, String name, Kind kind, int color) {
	}

	/** Farbiger Quader in Modell-Pixeln (Kopf: x -4..4, y -8..0, z -4..4, vorne = -z). */
	public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
	}

	/** Hut: Quader und ob er leuchtet (Heiligenschein) bzw. schwebt. */
	public record Hat(List<Box> boxes, boolean glow, boolean floating) {
	}

	/** One flat layer of a wing: px[y][x] (x = 0 at the body, going outward), z = offset along the wing normal. */
	public record Layer(int[][] px, float z) {
	}

	/**
	 * Wing made of one or more pixel layers. cell = size of one pixel in model pixels (0.5 = HD),
	 * rootY = row at shoulder height.
	 */
	public record Wing(List<Layer> layers, float cell, int rootY, boolean glow) {
		public int w() {
			return layers.get(0).px()[0].length;
		}

		public int h() {
			return layers.get(0).px().length;
		}
	}

	public static final String NONE = "none";

	public static final List<Item> CAPES = List.of(
			new Item("weiss", "Cloud White", Kind.CAPE, 0xFFF1F5F9),
			new Item("blau", "Pin Blue", Kind.CAPE, 0xFF2563EB),
			new Item("galaxie", "Galaxy", Kind.CAPE, 0xFF6D28D9),
			new Item("flammen", "Flames", Kind.CAPE, 0xFFF97316),
			new Item("sakura", "Cherry Blossom", Kind.CAPE, 0xFFF472B6),
			new Item("ender", "Ender", Kind.CAPE, 0xFF10B981),
			new Item("regenbogen", "Rainbow", Kind.CAPE, 0xFFFACC15),
			new Item("eis", "Ice", Kind.CAPE, 0xFF7DD3FC));

	public static final List<Item> HATS = List.of(
			new Item("zylinder", "Top Hat", Kind.HAT, 0xFF22222A),
			new Item("krone", "Crown", Kind.HAT, 0xFFFACC15),
			new Item("halo", "Halo", Kind.HAT, 0xFFFDE68A),
			new Item("hoerner", "Horns", Kind.HAT, 0xFF991B1B),
			new Item("partyhut", "Party Hat", Kind.HAT, 0xFFEC4899),
			new Item("katzenohren", "Cat Ears", Kind.HAT, 0xFF3F3F46));

	public static final List<Item> WINGS = List.of(
			new Item("engel", "Angel", Kind.WINGS, 0xFFF1F5F9),
			new Item("seraph", "Seraph", Kind.WINGS, 0xFFFDE68A),
			new Item("rabe", "Raven", Kind.WINGS, 0xFF27272A),
			new Item("phoenix", "Phoenix", Kind.WINGS, 0xFFF97316),
			new Item("drache", "Dragon", Kind.WINGS, 0xFF991B1B),
			new Item("enderdrache", "Ender Dragon", Kind.WINGS, 0xFF6D28D9),
			new Item("schmetterling", "Butterfly", Kind.WINGS, 0xFF38BDF8));

	public static final List<Item> KILLS = List.of(
			new Item("lightning", "Lightning", Kind.KILL, 0xFF93C5FD),
			new Item("firework", "Firework", Kind.KILL, 0xFFF472B6),
			new Item("souls", "Souls", Kind.KILL, 0xFF67E8F9),
			new Item("hearts", "Hearts", Kind.KILL, 0xFFF43F5E),
			new Item("ender", "Ender", Kind.KILL, 0xFFA855F7),
			new Item("crimson", "Crimson", Kind.KILL, 0xFFB91C1C),
			new Item("flames", "Flames", Kind.KILL, 0xFFF97316));

	public static final List<Item> LYRIC_STYLES = List.of(
			new Item("sky", "Sky Glow", Kind.LYRICS, 0xFFE0F2FE),
			new Item("neon", "Neon", Kind.LYRICS, 0xFFF472B6),
			new Item("gold", "Gold", Kind.LYRICS, 0xFFFACC15),
			new Item("aurora", "Aurora", Kind.LYRICS, 0xFF34D399),
			new Item("classic", "Classic", Kind.LYRICS, 0xFFFFFFFF));

	public static final List<Item> ARMOR_STYLES = List.of(
			new Item("bars", "Bars", Kind.ARMOR, 0xFF4ADE80),
			new Item("glass", "Glass", Kind.ARMOR, 0xFF7DD3FC),
			new Item("compact", "Compact", Kind.ARMOR, 0xFFFACC15));

	public static final List<Item> SPOTIFY_STYLES = List.of(
			new Item("glass", "Glass Card", Kind.SPOTIFY, 0xFF22D3EE),
			new Item("dark", "Dark Card", Kind.SPOTIFY, 0xFF1DB954),
			new Item("minimal", "Minimal", Kind.SPOTIFY, 0xFFA855F7));

	public static final List<Item> HIT_COLORS = List.of(
			new Item("blue", "Ice Blue", Kind.HIT, 0xFF2F7BFF),
			new Item("purple", "Purple", Kind.HIT, 0xFFA23BFF),
			new Item("pink", "Pink", Kind.HIT, 0xFFFF4FC3),
			new Item("green", "Toxic Green", Kind.HIT, 0xFF33E05A),
			new Item("gold", "Gold", Kind.HIT, 0xFFFFB800),
			new Item("white", "Ghost White", Kind.HIT, 0xFFFFFFFF),
			new Item("shadow", "Shadow", Kind.HIT, 0xFF27272A),
			new Item("rainbow", "Rainbow", Kind.HIT, 0xFFFACC15));

	public static final List<Item> POP_STYLES = List.of(
			new Item("red", "Crimson", Kind.POP, 0xFFEF4444),
			new Item("blue", "Ice", Kind.POP, 0xFF38BDF8),
			new Item("purple", "Void", Kind.POP, 0xFFA855F7),
			new Item("gold", "Gold", Kind.POP, 0xFFFACC15),
			new Item("green", "Toxic", Kind.POP, 0xFF4ADE80),
			new Item("pink", "Pink", Kind.POP, 0xFFF472B6),
			new Item("white", "Spirit", Kind.POP, 0xFFF1F5F9),
			new Item("rainbow", "Rainbow", Kind.POP, 0xFFFACC15));

	// Aktuelle Auswahl (von der Konfiguration gespiegelt, damit das Rendern ohne Dateizugriff auskommt)
	public static volatile String cape = NONE;
	public static volatile String hat = NONE;
	public static volatile String wings = NONE;
	public static volatile String kill = NONE;
	public static volatile String lyrics = NONE;
	public static volatile String armorHud = NONE;
	public static volatile String spotify = NONE;
	public static volatile String hitColor = NONE;
	public static volatile String popChams = NONE;

	private static final Map<String, Hat> HAT_MODELS = new HashMap<>();
	private static final Map<String, Wing> WING_MODELS = new HashMap<>();

	private Cosmetics() {
	}

	public static void load(MixConfig c) {
		cape = valid(c.cape, CAPES);
		hat = valid(c.hat, HATS);
		wings = valid(c.wings, WINGS);
		kill = valid(c.killEffect, KILLS);
		lyrics = valid(c.lyrics, LYRIC_STYLES);
		armorHud = valid(c.armorHud, ARMOR_STYLES);
		spotify = valid(c.spotify, SPOTIFY_STYLES);
		hitColor = valid(c.hitColor, HIT_COLORS);
		popChams = valid(c.popChams, POP_STYLES);
	}

	private static String valid(String id, List<Item> list) {
		if (id != null) {
			for (Item i : list) {
				if (i.id().equals(id)) {
					return id;
				}
			}
		}
		return NONE;
	}

	public static List<Item> list(Kind k) {
		return switch (k) {
			case CAPE -> CAPES;
			case HAT -> HATS;
			case WINGS -> WINGS;
			case KILL -> KILLS;
			case LYRICS -> LYRIC_STYLES;
			case ARMOR -> ARMOR_STYLES;
			case SPOTIFY -> SPOTIFY_STYLES;
			case HIT -> HIT_COLORS;
			case POP -> POP_STYLES;
		};
	}

	public static String worn(Kind k) {
		return switch (k) {
			case CAPE -> cape;
			case HAT -> hat;
			case WINGS -> wings;
			case KILL -> kill;
			case LYRICS -> lyrics;
			case ARMOR -> armorHud;
			case SPOTIFY -> spotify;
			case HIT -> hitColor;
			case POP -> popChams;
		};
	}

	/** Kinds that are effects, not things worn on the player model. */
	public static boolean isEffect(Kind k) {
		return k == Kind.KILL || k == Kind.LYRICS || k == Kind.ARMOR || k == Kind.SPOTIFY || k == Kind.HIT || k == Kind.POP;
	}

	public static Item item(Kind k, String id) {
		for (Item i : list(k)) {
			if (i.id().equals(id)) {
				return i;
			}
		}
		return null;
	}

	/** Setzt die Auswahl und speichert sie in der Konfiguration. */
	public static void wear(MixConfig c, Kind k, String id) {
		String v = id == null ? NONE : id;
		switch (k) {
			case CAPE -> {
				cape = v;
				c.cape = v;
			}
			case HAT -> {
				hat = v;
				c.hat = v;
			}
			case WINGS -> {
				wings = v;
				c.wings = v;
			}
			case KILL -> {
				kill = v;
				c.killEffect = v;
			}
			case LYRICS -> {
				lyrics = v;
				c.lyrics = v;
			}
			case ARMOR -> {
				armorHud = v;
				c.armorHud = v;
			}
			case SPOTIFY -> {
				spotify = v;
				c.spotify = v;
			}
			case HIT -> {
				hitColor = v;
				c.hitColor = v;
			}
			case POP -> {
				popChams = v;
				c.popChams = v;
			}
		}
		c.save();
	}

	/** Gehoert dieser Render-Zustand zum eigenen Spieler (Welt und Inventar-Vorschau)? */
	public static boolean isLocal(AvatarRenderState s) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && s.id == mc.player.getId();
	}

	public static Identifier capeTexture(String id) {
		return Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "textures/cosmetics/cape_" + id + ".png");
	}

	public static final Identifier WHITE = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID,
			"textures/cosmetics/white.png");

	// ================================================================== Huete

	public static Hat hat(String id) {
		return HAT_MODELS.computeIfAbsent(id, Cosmetics::buildHat);
	}

	private static Hat buildHat(String id) {
		List<Box> b = new ArrayList<>();
		switch (id) {
			case "zylinder" -> {
				b.add(new Box(-6, -9.2f, -6, 6, -8.5f, 6, 0xFF1C1C22));
				b.add(new Box(-4, -10.7f, -4, 4, -9.2f, 4, 0xFFB91C1C));
				b.add(new Box(-3.9f, -16, -3.9f, 3.9f, -10.7f, 3.9f, 0xFF26262E));
				b.add(new Box(-3.6f, -16.2f, -3.6f, 3.6f, -16, 3.6f, 0xFF33333D));
				return new Hat(b, false, false);
			}
			case "krone" -> {
				int gold = 0xFFFACC15;
				int dark = 0xFFCA8A04;
				b.add(new Box(-4.7f, -11, -4.7f, 4.7f, -8.5f, -4.2f, gold));
				b.add(new Box(-4.7f, -11, 4.2f, 4.7f, -8.5f, 4.7f, gold));
				b.add(new Box(-4.7f, -11, -4.2f, -4.2f, -8.5f, 4.2f, gold));
				b.add(new Box(4.2f, -11, -4.2f, 4.7f, -8.5f, 4.2f, gold));
				b.add(new Box(-4.75f, -9.2f, -4.75f, 4.75f, -8.5f, 4.75f, dark));
				float[] pos = { -4.7f, -0.6f, 3.5f };
				for (float p : pos) {
					b.add(new Box(p, -12.6f, -4.7f, p + 1.2f, -11, -4.2f, gold));
					b.add(new Box(p, -12.6f, 4.2f, p + 1.2f, -11, 4.7f, gold));
					b.add(new Box(-4.7f, -12.6f, p, -4.2f, -11, p + 1.2f, gold));
					b.add(new Box(4.2f, -12.6f, p, 4.7f, -11, p + 1.2f, gold));
				}
				b.add(new Box(-0.8f, -10.4f, -4.95f, 0.8f, -9.2f, -4.7f, 0xFFDC2626));
				b.add(new Box(-3.3f, -10.2f, -4.9f, -2.3f, -9.4f, -4.7f, 0xFF2563EB));
				b.add(new Box(2.3f, -10.2f, -4.9f, 3.3f, -9.4f, -4.7f, 0xFF16A34A));
				b.add(new Box(-0.5f, -13.4f, -4.8f, 0.5f, -12.6f, -4.3f, 0xFFDC2626));
				return new Hat(b, false, false);
			}
			case "halo" -> {
				int c = 0xFFFDE68A;
				b.add(new Box(-4, -12.6f, -4, 4, -12, -3, c));
				b.add(new Box(-4, -12.6f, 3, 4, -12, 4, c));
				b.add(new Box(-4, -12.6f, -3, -3, -12, 3, c));
				b.add(new Box(3, -12.6f, -3, 4, -12, 3, c));
				return new Hat(b, true, true);
			}
			case "hoerner" -> {
				for (int s = -1; s <= 1; s += 2) {
					horn(b, s, 2.2f, -9.6f, 1.6f, 1.1f, 0xFF7F1D1D);
					horn(b, s, 2.9f, -11.0f, 1.3f, 1.4f, 0xFF991B1B);
					horn(b, s, 3.6f, -12.2f, 1.0f, 1.2f, 0xFF7F1D1D);
					horn(b, s, 4.2f, -13.0f, 0.7f, 0.8f, 0xFF450A0A);
				}
				return new Hat(b, false, false);
			}
			case "partyhut" -> {
				int[] cols = { 0xFFEC4899, 0xFFFBBF24, 0xFF22D3EE };
				float y = -8.5f;
				for (int i = 0; i < 6; i++) {
					float r = 3.4f - i * 0.55f;
					b.add(new Box(-r, y - 1.2f, -r, r, y, r, cols[i % 3]));
					y -= 1.2f;
				}
				b.add(new Box(-0.8f, y - 1.5f, -0.8f, 0.8f, y, 0.8f, 0xFFFFFFFF));
				return new Hat(b, false, false);
			}
			case "katzenohren" -> {
				int fur = 0xFF3F3F46;
				int pink = 0xFFF9A8D4;
				for (int s = -1; s <= 1; s += 2) {
					ear(b, s, 1.0f, 4.0f, -9.6f, -8.5f, fur);
					ear(b, s, 1.5f, 3.5f, -10.6f, -9.6f, fur);
					ear(b, s, 2.0f, 3.0f, -11.6f, -10.6f, fur);
					b.add(sym(s, 1.6f, 3.4f, -9.9f, -8.6f, -0.75f, -0.5f, pink));
				}
				return new Hat(b, false, false);
			}
			default -> {
				return new Hat(b, false, false);
			}
		}
	}

	private static void horn(List<Box> b, int s, float x, float y, float w, float h, int c) {
		b.add(sym(s, x, x + w, y - h + 0.6f, y + 0.6f, -0.6f, 0.6f, c));
	}

	private static void ear(List<Box> b, int s, float xi, float xo, float y0, float y1, int c) {
		b.add(sym(s, xi, xo, y0, y1, -0.5f, 0.5f, c));
	}

	/** Quader auf der Seite s (+1 rechts, -1 links) mit x-Bereich (a..b) vom Kopf aus gemessen. */
	private static Box sym(int s, float a, float bx, float y0, float y1, float z0, float z1, int c) {
		float x0 = s > 0 ? a : -bx;
		float x1 = s > 0 ? bx : -a;
		return new Box(x0, y0, z0, x1, y1, z1, c);
	}

	// ================================================================== Fluegel

	public static Wing wing(String id) {
		return WING_MODELS.computeIfAbsent(id, Cosmetics::buildWing);
	}

	private static Wing buildWing(String id) {
		return switch (id) {
			case "engel" -> hdFeather(0xFFFFFFFF, 0xFFF4F6F9, 0xFFC9D0DB, 0xFF8E99AB, false);
			case "seraph" -> hdFeather(0xFFFFFFFF, 0xFFFFF8E1, 0xFFFACC15, 0xFFB7791F, true);
			case "rabe" -> hdFeather(0xFF4A4A54, 0xFF2C2C33, 0xFF16161B, 0xFF07070A, false);
			case "phoenix" -> hdFeather(0xFFFDE047, 0xFFFB923C, 0xFFDC2626, 0xFF7F1D1D, true);
			case "drache" -> membrane(0xFF8F1D1D, 0xFF5F1212, 0xFF2A1A16, 0xFF140C0A);
			case "enderdrache" -> membrane(0xFF5B21B6, 0xFF2E1065, 0xFF1A1033, 0xFF0B0716);
			default -> butterfly();
		};
	}

	private static final int WW = 18;
	private static final int WH = 24;
	private static final int ROOT = 10;

	// HD feather wings: 0.5 model pixels per cell, tall and upright like angel wings from cosmetic clients.
	private static final float HD = 0.5f;
	private static final int HW = 30; // 15 model px outward
	private static final int HH = 64; // 32 model px tall
	private static final int HROOT = 34; // shoulder row: 17 px of wing above it, 15 below

	/** One feather: anchor (model px), direction angle from straight down (deg, + = outward), length, half width. */
	private record Feather(float ax, float ay, float angle, float len, float hw) {
	}

	/** Point on the wing's arm bone, t = 0 at the shoulder, 1 at the top tip. */
	private static float[] bone(float t) {
		// quadratic curve: shoulder (0.5, 0) -> elbow (5, -9) -> tip (10, -16)
		float x = (1 - t) * (1 - t) * 0.5f + 2 * (1 - t) * t * 5f + t * t * 10f;
		float y = (1 - t) * (1 - t) * 0f + 2 * (1 - t) * t * -9f + t * t * -16f;
		return new float[] { x, y };
	}

	/**
	 * Layered feather wing: back layer = long primaries and secondaries hanging down in tiers,
	 * front layer = short coverts along the arm. Colors: light (coverts/top), main, tip shade, edge.
	 */
	private static Wing hdFeather(int light, int main, int tipShade, int edge, boolean glow) {
		List<Feather> back = new ArrayList<>();
		// secondaries: from the shoulder half of the arm, hanging straight down to the hip
		for (int i = 0; i < 6; i++) {
			float t = 0.08f + i * 0.085f;
			float[] a = bone(t);
			back.add(new Feather(a[0], a[1] + 1, -4 + i * 1.5f, 12f - i * 0.3f + (i % 2) * 1.2f, 1.6f));
		}
		// primaries: from the outer half, longest at the middle, fanning slightly outward
		for (int i = 0; i < 7; i++) {
			float t = 0.55f + i * 0.07f;
			float[] a = bone(t);
			back.add(new Feather(a[0], a[1] + 1, 4 + i * 2.6f, 17f - Math.abs(i - 2) * 1.3f + (i % 2) * 0.8f, 1.7f));
		}
		List<Feather> front = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			float t = 0.04f + i * 0.092f;
			float[] a = bone(t);
			front.add(new Feather(a[0], a[1] + 0.3f, 8 + i * 1.8f, 5.5f + (i % 2) * 1.2f, 1.5f));
		}
		int[][] backPx = paint(back, main, tipShade, edge, light, false);
		int[][] frontPx = paint(front, light, main, edge, light, true);
		return new Wing(List.of(new Layer(backPx, 0f), new Layer(frontPx, 0.3f), new Layer(frontPx, -0.3f)), HD,
				HROOT, glow);
	}

	private static int[][] paint(List<Feather> feathers, int base, int tipShade, int edge, int light, boolean arm) {
		int[][] px = new int[HH][HW];
		for (int r = 0; r < HH; r++) {
			for (int c = 0; c < HW; c++) {
				float u = (c + 0.5f) * HD;
				float v = (r - HROOT + 0.5f) * HD;
				int col = 0;
				// later feathers lie on top of earlier ones
				for (Feather f : feathers) {
					double ang = Math.toRadians(f.angle());
					float dx = (float) Math.sin(ang);
					float dy = (float) Math.cos(ang);
					float px0 = u - f.ax();
					float py0 = v - f.ay();
					float s = px0 * dx + py0 * dy; // along the feather
					float w = px0 * dy - py0 * dx; // across
					if (s < 0 || s > f.len()) {
						continue;
					}
					float k = s / f.len();
					float shape = k < 0.35f ? 1f : (float) Math.sqrt(Math.max(0, 1 - Math.pow((k - 0.35f) / 0.65f, 2)));
					float half = f.hw() * shape;
					if (Math.abs(w) > half) {
						continue;
					}
					int cc = lerp(base, tipShade, (float) Math.pow(k, 1.6) * 0.75f);
					if (Math.abs(w) < 0.22f && k > 0.08f && k < 0.85f) {
						cc = lerp(cc, edge, 0.25f); // shaft
					}
					if (Math.abs(w) > half - 0.5f) {
						cc = lerp(cc, edge, 0.55f); // feather edge -> visible separation
					}
					if (arm && k < 0.3f) {
						cc = lerp(cc, light, 0.6f);
					}
					col = cc;
				}
				if (arm) {
					// arm ridge along the bone
					for (int i = 0; i <= 40; i++) {
						float[] b = bone(i / 40f);
						float d = (float) Math.hypot(u - b[0], v - b[1]);
						if (d < 0.9f) {
							col = d < 0.45f ? light : lerp(light, edge, 0.35f);
							break;
						}
					}
				}
				px[r][c] = col;
			}
		}
		return px;
	}

	/** Hautfluegel (Drache): Knochen von der Spitze zu den Zacken, dazwischen Haut mit Bogen-Unterkante. */
	private static Wing membrane(int skin, int skinDark, int bone, int edge) {
		int[][] px = new int[WH][WW];
		float tipX = WW - 1;
		float tipY = top(WW - 1);
		float[][] fingers = { { 0, 9 }, { 5.5f, 11 }, { 11, 10.5f }, { 16, 7.5f } };
		for (int c = 0; c < WW; c++) {
			float top = top(c);
			// Unterkante: zwischen den Fingern nach oben gewoelbt
			float bottom = 9;
			for (int i = 0; i < fingers.length - 1; i++) {
				float a = fingers[i][0];
				float bx = fingers[i + 1][0];
				if (c >= a && c <= bx) {
					float t = (c - a) / (bx - a);
					bottom = fingers[i][1] + (fingers[i + 1][1] - fingers[i][1]) * t
							- 3.2f * (float) Math.sin(Math.PI * t);
				}
			}
			if (c > fingers[fingers.length - 1][0]) {
				bottom = fingers[fingers.length - 1][1] - (c - 16) * 4;
			}
			for (int r = 0; r < WH; r++) {
				float y = r - ROOT;
				if (y < top - 1 || y > bottom) {
					continue;
				}
				int col = lerp(skin, skinDark, Math.max(0f, Math.min(1f, (y - top) / 12f)));
				if (y < top + 0.2f) {
					col = bone; // Armknochen oben
				}
				for (float[] fi : fingers) {
					if (distSeg(c + 0.5f, y + 0.5f, tipX, tipY, fi[0], fi[1]) < 0.55f) {
						col = bone;
					}
				}
				px[r][c] = col;
			}
		}
		// Kralle an der Spitze
		int tr = Math.round(tipY) + ROOT - 1;
		if (tr >= 0) {
			px[tr][WW - 1] = 0xFFE7E5E4;
		}
		outline(px, edge);
		return single(px);
	}

	private static Wing single(int[][] px) {
		return new Wing(List.of(new Layer(px, 0f)), 1f, ROOT, false);
	}

	private static float top(int c) {
		return 1 - 0.68f * c;
	}

	/** Schmetterling: grosser Oberfluegel, kleiner Unterfluegel, dunkler Rand mit weissen Punkten, Augenfleck. */
	private static Wing butterfly() {
		int[][] px = new int[WH][WW];
		int inner = 0xFF7DD3FC;
		int outer = 0xFF1D4ED8;
		for (int r = 0; r < WH; r++) {
			for (int c = 0; c < WW; c++) {
				float x = c + 0.5f;
				float y = r - ROOT + 0.5f;
				float d1 = ell(x, y, 8.5f, -4.4f, 8.5f, 5.6f, -0.35f);
				float d2 = ell(x, y, 6f, 4.5f, 6f, 4.8f, 0.3f);
				float d = Math.min(d1, d2);
				if (d > 1f) {
					continue;
				}
				int col = lerp(inner, outer, Math.min(1f, d * 1.1f));
				if (d > 0.8f) {
					col = 0xFF0F172A;
					if ((c + r) % 3 == 0 && d < 0.93f) {
						col = 0xFFFFFFFF;
					}
				}
				float e = (float) Math.hypot(x - 10.5f, y + 6.5f);
				if (e < 1.9f) {
					col = e < 0.9f ? 0xFF0F172A : 0xFFFACC15;
				}
				px[r][c] = col;
			}
		}
		outline(px, 0xFF020617);
		return single(px);
	}

	private static float ell(float x, float y, float cx, float cy, float rx, float ry, float rot) {
		float dx = x - cx;
		float dy = y - cy;
		float cs = (float) Math.cos(rot);
		float sn = (float) Math.sin(rot);
		float u = dx * cs + dy * sn;
		float v = -dx * sn + dy * cs;
		return (float) Math.sqrt((u * u) / (rx * rx) + (v * v) / (ry * ry));
	}

	private static float distSeg(float px, float py, float ax, float ay, float bx, float by) {
		float vx = bx - ax;
		float vy = by - ay;
		float t = ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy);
		t = Math.max(0f, Math.min(1f, t));
		float dx = ax + vx * t - px;
		float dy = ay + vy * t - py;
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	/** Randpixel (mit leerem Nachbarn) bekommen die Kantenfarbe; die Koerperseite (x = 0) bleibt offen. */
	private static void outline(int[][] px, int edge) {
		int h = px.length;
		int w = px[0].length;
		boolean[][] e = new boolean[h][w];
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) {
				if (px[r][c] == 0) {
					continue;
				}
				boolean up = r == 0 || px[r - 1][c] == 0;
				boolean dn = r == h - 1 || px[r + 1][c] == 0;
				boolean rt = c == w - 1 || px[r][c + 1] == 0;
				boolean lt = c > 0 && px[r][c - 1] == 0;
				e[r][c] = up || dn || rt || lt;
			}
		}
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) {
				if (e[r][c]) {
					px[r][c] = edge;
				}
			}
		}
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
		int rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (aa << 24) | (rr << 16) | (gg << 8) | bb;
	}
}
