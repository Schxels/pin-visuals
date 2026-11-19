package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Sky Designer: paints a whole sky (OptiFine 3x2 cube sheet, used through Skyboxify) from a few settings: a colour
 * gradient from the horizon up and glowing lines across it (smooth "strings" like in many visuals edits, jagged
 * "lightning" or wide "aurora" bands).
 * <p>
 * Every line is a curve elevation = f(azimuth) built only from cos(k * azimuth) with k a multiple of 4. Such a sky looks
 * the same every 90 degrees and is mirror-symmetric, so the six cube faces always fit together without seams, no
 * matter how the sky mod arranges or turns them.
 */
public final class SkyGen {

	public static final String PACK = "PinVisuals Sky";

	/** All settings of one sky. */
	public record Spec(int style, int top, int horizon, int line, int count, int width, int glow, long seed) {
	}

	private record Curve(double base, double[] amp, int[] k) {
		double at(double theta) {
			double v = base;
			for (int i = 0; i < k.length; i++) {
				v += amp[i] * Math.cos(k[i] * theta);
			}
			return v;
		}
	}

	private final Spec spec;
	private final Curve[] curves;
	private final double w;
	private final double sigma;
	private final double glowAmt;

	public SkyGen(Spec spec) {
		this.spec = spec;
		Random r = new Random(spec.seed());
		int n = Math.max(1, spec.count());
		curves = new Curve[n];
		for (int i = 0; i < n; i++) {
			int terms = spec.style() == 1 ? 7 : 3;
			double[] amp = new double[terms];
			int[] k = new int[terms];
			for (int t = 0; t < terms; t++) {
				boolean jag = spec.style() == 1 && t >= 2;
				k[t] = 4 * (jag ? 6 + r.nextInt(14) : 1 + r.nextInt(spec.style() == 2 ? 2 : 4));
				amp[t] = Math.toRadians(jag ? 0.6 + r.nextDouble() * 1.4 : (spec.style() == 2 ? 3 : 6) + r.nextDouble() * 14);
			}
			double base = Math.toRadians(spec.style() == 2 ? 25 + r.nextDouble() * 35 : 6 + r.nextDouble() * 70);
			curves[i] = new Curve(base, amp, k);
		}
		double width = Math.max(1, spec.width());
		w = Math.toRadians((spec.style() == 2 ? 2.4 : 0.22) * width);
		sigma = w * (spec.style() == 2 ? 1.8 : 4.5);
		glowAmt = spec.glow() / 100.0;
	}

	/** Colour (ARGB) of the sky in a direction: azimuth theta, elevation phi (radians). */
	public int color(double theta, double phi) {
		double up = Math.max(0, phi) / (Math.PI / 2);
		int bg = phi < 0 ? mix(spec.horizon(), 0xFF000000, Math.min(1, -phi / 0.6) * 0.7)
				: mix(spec.horizon(), spec.top(), Math.pow(up, 0.7));
		double best = 0;
		for (Curve c : curves) {
			double d = Math.abs(phi - c.at(theta));
			double core = d < w ? 1 : d < w * 1.8 ? 1 - (d - w) / (w * 0.8) : 0;
			double g = glowAmt * Math.exp(-(d * d) / (2 * sigma * sigma)) * (spec.style() == 2 ? 0.9 : 0.55);
			best = Math.max(best, Math.min(1, core * (spec.style() == 2 ? 0.35 : 1) + g));
		}
		if (phi < -0.05) {
			best *= Math.max(0, 1 + (phi + 0.05) / 0.3); // lines fade out below the horizon
		}
		return add(bg, spec.line(), best);
	}

	/** A strip of the whole sky around you (azimuth across, elevation -15..90 degrees up the image). */
	public int[] panorama(int pw, int ph) {
		int[] px = new int[pw * ph];
		for (int y = 0; y < ph; y++) {
			double phi = Math.toRadians(90 - (105.0 * y / (ph - 1)));
			for (int x = 0; x < pw; x++) {
				px[y * pw + x] = color(2 * Math.PI * x / pw, phi);
			}
		}
		return px;
	}

	/** The 3x2 cube sheet (face size n): top row bottom, top, side; bottom row three sides. */
	public NativeImage sheet(int n) {
		NativeImage img = new NativeImage(3 * n, 2 * n, false);
		for (int cell = 0; cell < 6; cell++) {
			int ox = (cell % 3) * n;
			int oy = (cell / 3) * n;
			for (int y = 0; y < n; y++) {
				double v = 1 - 2.0 * y / (n - 1);
				for (int x = 0; x < n; x++) {
					double u = 2.0 * x / (n - 1) - 1;
					double theta;
					double phi;
					if (cell == 0) { // bottom
						theta = Math.atan2(u, v);
						phi = -Math.atan2(1, Math.hypot(u, v));
					} else if (cell == 1) { // top
						theta = Math.atan2(u, v);
						phi = Math.atan2(1, Math.hypot(u, v));
					} else { // sides
						theta = Math.atan(u);
						phi = Math.atan2(v, Math.hypot(1, u));
					}
					img.setPixel(ox + x, oy + y, color(theta, phi));
				}
			}
		}
		return img;
	}

	/** Writes the sky as a resource pack folder (resourcepacks/PinVisuals Sky). */
	public void writePack(Path packDir, int faceSize) throws java.io.IOException {
		Path root = packDir.resolve(PACK);
		Path sky = root.resolve("assets/minecraft/optifine/sky/world0");
		Files.createDirectories(sky);
		Files.writeString(root.resolve("pack.mcmeta"),
				"{\"pack\":{\"description\":\"Pin Visuals: your own sky\",\"pack_format\":64,"
						+ "\"supported_formats\":[1,999],\"min_format\":1,\"max_format\":999}}",
				StandardCharsets.UTF_8);
		Files.writeString(sky.resolve("sky1.properties"), "source=./sky1.png\nblend=alpha\nrotate=false\n",
				StandardCharsets.UTF_8);
		try (NativeImage img = sheet(faceSize)) {
			img.writeToFile(sky.resolve("sky1.png"));
		}
	}

	private static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private static int add(int a, int b, double t) {
		int r = (int) Math.min(255, ((a >> 16) & 255) + ((b >> 16) & 255) * t);
		int g = (int) Math.min(255, ((a >> 8) & 255) + ((b >> 8) & 255) * t);
		int bl = (int) Math.min(255, (a & 255) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
