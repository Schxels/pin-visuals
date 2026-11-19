package com.texturestudio;

/**
 * CS-style inspect animations of the karambit, bayonet and flip knife as keyframes (pure maths, no Minecraft
 * classes). Each key: time, position x/y/z, rotation x/y/z in degrees and the pivot x/y/z the rotation turns around
 * (all in the hand's space after Minecraft placed the item in first person). Between keys the values glide on a
 * monotone cubic curve: smooth like a real hand, but without overshooting a pose that is held.
 */
final class KnifeInspect {

	private static final int X = 0, Y = 1, Z = 2, RX = 3, RY = 4, RZ = 5, PX = 6, PY = 7, PZ = 8;

	private record Track(float[] times, float[][] values) {
		float length() {
			return times[times.length - 1];
		}
	}

	private static Track build(float[][] keys) {
		float[] t = new float[keys.length];
		float[][] v = new float[9][keys.length];
		for (int i = 0; i < keys.length; i++) {
			t[i] = keys[i][0];
			for (int c = 0; c < 9; c++) {
				v[c][i] = keys[i][c + 1];
			}
		}
		return new Track(t, v);
	}

	// t, x, y, z, rx, ry, rz, px, py, pz
	/** Karambit: up to the middle, two fast spins around the finger ring, then both sides of the blade. */
	private static final Track KARAMBIT = build(new float[][] {
			{ 0.00f, 0, 0, 0, 0, 0, 0, 0, 0.13f, 0 },
			{ 0.30f, -0.15f, 0.16f, 0.03f, -12, 8, 12, 0, 0.13f, 0 },
			{ 0.42f, -0.16f, 0.17f, 0.03f, -12, 8, 40, 0, 0.13f, 0 },
			{ 0.80f, -0.17f, 0.18f, 0.03f, -10, 6, 400, 0, 0.13f, 0 },
			{ 1.12f, -0.16f, 0.17f, 0.03f, -12, 8, 732, 0, 0.13f, 0 },
			{ 1.30f, -0.15f, 0.15f, 0.03f, -14, 10, 728, 0, 0.13f, 0 },
			{ 1.85f, -0.15f, 0.12f, 0.06f, -22, 72, 736, 0, 0.05f, 0 },
			{ 2.20f, -0.15f, 0.12f, 0.06f, -24, 78, 738, 0, 0.05f, 0 },
			{ 2.70f, -0.12f, 0.10f, 0.05f, -30, -38, 734, 0, 0.05f, 0 },
			{ 2.95f, -0.12f, 0.10f, 0.05f, -30, -42, 732, 0, 0.05f, 0 },
			{ 3.40f, 0, 0, 0, 0, 0, 720, 0, 0.05f, 0 },
	});

	/** Bayonet: raise and look along the flat, roll to the other side, flip into a reverse grip and back. */
	private static final Track BAYONET = build(new float[][] {
			{ 0.00f, 0, 0, 0, 0, 0, 0, 0, 0.10f, 0 },
			{ 0.35f, -0.07f, 0.07f, 0.05f, -10, 10, 8, 0, 0.10f, 0 },
			{ 1.00f, -0.14f, 0.12f, 0.06f, -18, 74, 4, 0, 0.10f, 0 },
			{ 1.55f, -0.15f, 0.13f, 0.06f, -20, 84, 2, 0, 0.10f, 0 },
			{ 2.10f, -0.12f, 0.12f, 0.06f, -26, -50, 6, 0, 0.10f, 0 },
			{ 2.50f, -0.12f, 0.12f, 0.06f, -24, -56, 6, 0, 0.10f, 0 },
			{ 3.05f, -0.06f, 0.10f, 0.04f, -385, -10, 0, 0, 0.12f, 0 },
			{ 3.25f, -0.04f, 0.06f, 0.03f, -362, -4, 0, 0, 0.12f, 0 },
			{ 3.80f, 0, 0, 0, -360, 0, 0, 0, 0.12f, 0 },
	});

	/** Flip knife: a little dip, toss it end over end, catch, then show both sides. */
	private static final Track FLIP = build(new float[][] {
			{ 0.00f, 0, 0, 0, 0, 0, 0, 0, 0.30f, 0 },
			{ 0.22f, -0.03f, -0.06f, 0.02f, 14, 4, 4, 0, 0.30f, 0 },
			{ 0.55f, -0.06f, 0.42f, -0.06f, -175, 6, 6, 0, 0.30f, 0 },
			{ 0.88f, -0.05f, 0.05f, 0.01f, -352, 6, 4, 0, 0.30f, 0 },
			{ 1.02f, -0.05f, -0.04f, 0.02f, -362, 6, 4, 0, 0.30f, 0 },
			{ 1.20f, -0.06f, 0.02f, 0.03f, -366, 10, 4, 0, 0.30f, 0 },
			{ 1.75f, -0.14f, 0.11f, 0.06f, -378, 68, 2, 0, 0.10f, 0 },
			{ 2.15f, -0.14f, 0.12f, 0.06f, -380, 76, 2, 0, 0.10f, 0 },
			{ 2.60f, -0.11f, 0.10f, 0.05f, -384, -40, 6, 0, 0.10f, 0 },
			{ 2.85f, -0.11f, 0.10f, 0.05f, -382, -44, 6, 0, 0.10f, 0 },
			{ 3.30f, 0, 0, 0, -360, 0, 0, 0, 0.10f, 0 },
	});

	private KnifeInspect() {
	}

	private static Track track(String model) {
		return switch (model) {
			case "karambit" -> KARAMBIT;
			case "bayonet" -> BAYONET;
			default -> FLIP; // flip knife (and the butterfly where its trick renderer is missing)
		};
	}

	static float length(String model) {
		return track(model).length();
	}

	/** x, y, z, rx, ry, rz (degrees), px, py, pz at this time; a little breathing on top while it is shown. */
	static float[] sample(String model, float time) {
		Track tr = track(model);
		float[] out = new float[9];
		for (int c = 0; c < 9; c++) {
			out[c] = curve(tr.times, tr.values[c], time);
		}
		float len = tr.length();
		float env = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, time / len)));
		out[Y] += 0.006f * env * (float) Math.sin(time * 5.1f);
		out[RZ] += 1.4f * env * (float) Math.sin(time * 3.7f + 1f);
		out[RX] += 1.0f * env * (float) Math.sin(time * 4.3f);
		return out;
	}

	/** Monotone cubic (Fritsch-Carlson) through the keys: smooth, no overshoot. */
	static float curve(float[] t, float[] v, float x) {
		int n = t.length;
		if (x <= t[0]) {
			return v[0];
		}
		if (x >= t[n - 1]) {
			return v[n - 1];
		}
		int i = 0;
		while (x > t[i + 1]) {
			i++;
		}
		float h = t[i + 1] - t[i];
		float d = (v[i + 1] - v[i]) / h;
		float m0 = tangent(t, v, i, d);
		float m1 = tangent(t, v, i + 1, d);
		float s = (x - t[i]) / h;
		float s2 = s * s;
		float s3 = s2 * s;
		return (2 * s3 - 3 * s2 + 1) * v[i] + (s3 - 2 * s2 + s) * h * m0 + (-2 * s3 + 3 * s2) * v[i + 1]
				+ (s3 - s2) * h * m1;
	}

	private static float tangent(float[] t, float[] v, int k, float segSlope) {
		int n = t.length;
		if (k == 0 || k == n - 1) {
			return 0f; // start and end at rest
		}
		float a = (v[k] - v[k - 1]) / (t[k] - t[k - 1]);
		float b = (v[k + 1] - v[k]) / (t[k + 1] - t[k]);
		if (a * b <= 0f) {
			return 0f; // turning point: hold still for a moment
		}
		float m = (a + b) / 2f;
		float lim = 3f * Math.min(Math.abs(a), Math.abs(b));
		return Math.signum(m) * Math.min(Math.abs(m), lim);
	}

	static final int[] AXES = { X, Y, Z, RX, RY, RZ, PX, PY, PZ };
}
