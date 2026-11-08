import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Erzeugt die Umhang-Texturen der Cosmetics (128x64, doppelte Aufloesung des Vanilla-Umhangs) und eine weisse
 * Hilfstextur fuer Huete/Fluegel.
 *
 * Aufruf: java tools/CosmeticArt.java src/main/resources/assets/texturestudio/textures/cosmetics
 *
 * Umhang-Layout (in 64x32-Pixeln, hier mal 2): Vorderseite (aussen) bei (1,1) 10x16, Rueckseite bei (12,1),
 * Kanten oben (1,0)/(11,0), links (0,1), rechts (11,1).
 */
public class CosmeticArt {

	static final int S = 2; // Aufloesungsfaktor
	static final int FW = 10 * S;
	static final int FH = 16 * S;

	interface Design {
		int color(int x, int y);
	}

	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		dir.mkdirs();
		BufferedImage white = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				white.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		ImageIO.write(white, "png", new File(dir, "white.png"));

		cape(dir, "weiss", CosmeticArt::weiss);
		cape(dir, "blau", CosmeticArt::blau);
		cape(dir, "galaxie", galaxie());
		cape(dir, "flammen", flammen());
		cape(dir, "sakura", sakura());
		cape(dir, "ender", ender());
		cape(dir, "regenbogen", CosmeticArt::regenbogen);
		cape(dir, "eis", eis());
	}

	static void cape(File dir, String id, Design d) throws Exception {
		BufferedImage img = new BufferedImage(64 * S, 32 * S, BufferedImage.TYPE_INT_ARGB);
		int[][] face = new int[FH][FW];
		for (int y = 0; y < FH; y++) {
			for (int x = 0; x < FW; x++) {
				face[y][x] = 0xFF000000 | d.color(x, y);
			}
		}
		// Vorderseite (aussen)
		for (int y = 0; y < FH; y++) {
			for (int x = 0; x < FW; x++) {
				img.setRGB(S + x, S + y, face[y][x]);
			}
		}
		// Rueckseite (innen): gespiegelt und dunkler
		for (int y = 0; y < FH; y++) {
			for (int x = 0; x < FW; x++) {
				img.setRGB(12 * S + x, S + y, shade(face[y][FW - 1 - x], 0.62f));
			}
		}
		// Kanten in der Farbe des Randes
		for (int i = 0; i < S; i++) {
			for (int x = 0; x < FW; x++) {
				img.setRGB(S + x, i, shade(face[0][x], 0.8f));
				img.setRGB(11 * S + x, i, shade(face[FH - 1][x], 0.8f));
			}
			for (int y = 0; y < FH; y++) {
				img.setRGB(i, S + y, shade(face[y][0], 0.75f));
				img.setRGB(11 * S + i, S + y, shade(face[y][FW - 1], 0.75f));
			}
		}
		ImageIO.write(img, "png", new File(dir, "cape_" + id + ".png"));
	}

	// ------------------------------------------------------------------ Designs

	/** Clean white cape with soft folds, a light frame and a faint emblem plate (like cosmetic-client capes). */
	static int weiss(int x, int y) {
		float fold = (float) Math.sin(x * 0.7) * 0.5f + 0.5f;
		int c = mix(0xFFFFFF, 0xE6E9EF, fold * 0.55f + y / (float) FH * 0.25f);
		if (x == 0 || x == FW - 1 || y == FH - 1) {
			return 0xCBD2DC;
		}
		if (y == 0) {
			return 0xDDE2E9;
		}
		// emblem plate in the upper middle
		int ex0 = 4;
		int ex1 = FW - 5;
		int ey0 = 5;
		int ey1 = 17;
		if (x >= ex0 && x <= ex1 && y >= ey0 && y <= ey1) {
			if (x == ex0 || x == ex1 || y == ey0 || y == ey1) {
				return 0xC3CAD6;
			}
			if ((y == ey0 + 3 || y == ey0 + 6 || y == ey0 + 9) && x > ex0 + 2 && x < ex1 - 2) {
				return 0xD3D9E2;
			}
			return mix(c, 0xF8FAFC, 0.6f);
		}
		return c;
	}

	static int blau(int x, int y) {
		int c = lerp3(0x1E3A8A, 0x2563EB, 0x60A5FA, y / (float) (FH - 1));
		if (x == 0 || x == FW - 1 || y == 0 || y == FH - 1) {
			return 0xDBEAFE;
		}
		if (x == 1 || x == FW - 2 || y == 1 || y == FH - 2) {
			return mix(c, 0x0F1E4A, 0.4f);
		}
		// Raute mit Punkt (Pin-Zeichen)
		int cx = FW / 2;
		int cy = FH / 2 - 2;
		int d = Math.abs(x - cx) + Math.abs(y - cy);
		if (d == 6 || d == 5) {
			return 0xFFFFFF;
		}
		if (d <= 2) {
			return 0xFFFFFF;
		}
		if (d < 5) {
			return mix(c, 0xFFFFFF, 0.25f);
		}
		return c;
	}

	static Design galaxie() {
		Random r = new Random(7);
		int[][] st = new int[FH][FW];
		for (int i = 0; i < 26; i++) {
			int x = r.nextInt(FW);
			int y = r.nextInt(FH);
			st[y][x] = r.nextInt(4) == 0 ? 0xBFDBFE : 0xFFFFFF;
		}
		return (x, y) -> {
			if (st[y][x] != 0) {
				return st[y][x];
			}
			float n = noise(x * 0.18f, y * 0.12f, 3);
			int base = mix(0x0B1026, 0x2E1065, y / (float) FH);
			if (n > 0.58f) {
				base = mix(base, 0x7C3AED, (n - 0.58f) * 2.2f);
			} else if (n < 0.38f) {
				base = mix(base, 0x1D4ED8, (0.38f - n) * 1.6f);
			}
			return base;
		};
	}

	static Design flammen() {
		return (x, y) -> {
			float h = y / (float) (FH - 1) + (noise(x * 0.35f, y * 0.12f, 11) - 0.5f) * 0.55f;
			if (h > 0.92f) {
				return 0xFDE047;
			}
			if (h > 0.76f) {
				return 0xFB923C;
			}
			if (h > 0.6f) {
				return 0xEA580C;
			}
			if (h > 0.46f) {
				return 0xB91C1C;
			}
			if (h > 0.36f) {
				return 0x5F1212;
			}
			return mix(0x0C0A09, 0x1C1917, y / (float) FH);
		};
	}

	static Design sakura() {
		Random r = new Random(3);
		int[][] pet = new int[FH][FW];
		for (int i = 0; i < 14; i++) {
			int x = r.nextInt(FW - 1);
			int y = r.nextInt(FH - 1);
			int c = r.nextBoolean() ? 0xFFFFFF : 0xF472B6;
			pet[y][x] = c;
			pet[y][x + 1] = c;
			pet[y + 1][x] = c;
			pet[y + 1][x + 1] = mix(c, 0xDB2777, 0.3f);
		}
		return (x, y) -> {
			// Ast als Kurve von links unten nach rechts oben
			float bx = 2 + y * 0.0f;
			float curve = FW - 3 - (float) Math.pow((FH - y) / (float) FH, 1.4) * (FW - 4);
			if (Math.abs(x - curve) < 1.0f && y > 6 && y < FH - 2) {
				return 0x57341F;
			}
			if (pet[y][x] != 0) {
				return pet[y][x];
			}
			return mix(0xFCE7F3, 0xF9A8D4, y / (float) FH + bx * 0);
		};
	}

	static Design ender() {
		Random r = new Random(5);
		int[][] dots = new int[FH][FW];
		for (int i = 0; i < 30; i++) {
			dots[r.nextInt(FH)][r.nextInt(FW)] = r.nextBoolean() ? 0xC026D3 : 0x7E22CE;
		}
		return (x, y) -> {
			float ex = (x - (FW - 1) / 2f) / 5.5f;
			float ey = (y - FH / 2f + 2) / 3.2f;
			float e = ex * ex + ey * ey;
			if (e < 1f) {
				float px = (x - (FW - 1) / 2f) / 1.6f;
				float py = (y - FH / 2f + 2) / 2.8f;
				if (px * px + py * py < 1f) {
					return 0x022C22;
				}
				return e < 0.55f ? 0x34D399 : 0x059669;
			}
			if (e < 1.35f) {
				return 0x064E3B;
			}
			if (dots[y][x] != 0) {
				return dots[y][x];
			}
			float n = noise(x * 0.2f, y * 0.15f, 9);
			return mix(0x09090F, 0x2E1065, Math.max(0f, n - 0.35f) * 1.4f);
		};
	}

	static int regenbogen(int x, int y) {
		int[] c = { 0xEF4444, 0xF97316, 0xFACC15, 0x22C55E, 0x3B82F6, 0x8B5CF6 };
		float f = y / (float) FH * c.length;
		int i = Math.min(c.length - 1, (int) f);
		int col = c[i];
		if (f - i < 0.18f) {
			col = mix(col, 0xFFFFFF, 0.25f);
		}
		if (x == 0 || x == FW - 1) {
			col = mix(col, 0x000000, 0.25f);
		}
		return col;
	}

	static Design eis() {
		return (x, y) -> {
			int base = mix(0xE0F2FE, 0x38BDF8, y / (float) FH);
			// diagonale Eiskristalle
			int a = Math.floorMod(x + y, 9);
			int b = Math.floorMod(x - y, 11);
			if (a == 0 && y % 4 != 3) {
				return 0xFFFFFF;
			}
			if (b == 0 && y > 4) {
				return mix(base, 0x0284C7, 0.45f);
			}
			float n = noise(x * 0.3f, y * 0.3f, 4);
			return mix(base, 0xFFFFFF, Math.max(0f, n - 0.6f) * 1.8f);
		};
	}

	// ------------------------------------------------------------------ Helfer

	static float noise(float x, float y, int seed) {
		float v = 0;
		float amp = 0.5f;
		float f = 1;
		for (int o = 0; o < 3; o++) {
			v += amp * vnoise(x * f, y * f, seed + o * 31);
			amp *= 0.5f;
			f *= 2;
		}
		return v / 0.875f;
	}

	static float vnoise(float x, float y, int seed) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		float tx = x - xi;
		float ty = y - yi;
		tx = tx * tx * (3 - 2 * tx);
		ty = ty * ty * (3 - 2 * ty);
		float a = hash(xi, yi, seed);
		float b = hash(xi + 1, yi, seed);
		float c = hash(xi, yi + 1, seed);
		float d = hash(xi + 1, yi + 1, seed);
		return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
	}

	static float hash(int x, int y, int seed) {
		int h = x * 374761393 + y * 668265263 + seed * 1442695041;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}

	static int shade(int argb, float f) {
		return (argb & 0xFF000000) | mix(argb & 0xFFFFFF, 0x000000, 1 - f);
	}

	static int lerp3(int a, int b, int c, float t) {
		return t < 0.5f ? mix(a, b, t * 2) : mix(b, c, (t - 0.5f) * 2);
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
