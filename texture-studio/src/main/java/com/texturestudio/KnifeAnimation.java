package com.texturestudio;

/**
 * Butterfly knife inspect as keyframes (pure maths, no Minecraft classes), built like a real balisong routine:
 * wind up, fan-close with a snap, flick it open again (the handles bounce off the latch), lift it to look at both
 * sides of the blade, a small aerial spin with a toss, a rollover of each handle and back to rest.
 *
 * Angles in radians. A handle at angle 0 points down (open), at PI it lies on the blade (closed). holdA: 0 = the
 * hand holds handle B, 1 = handle A, 0.5 = both (open or closed, the handles are together).
 */
public final class KnifeAnimation {

	private static final float PI = (float) Math.PI;
	private static final float TAU = PI * 2f;
	private static final float B = -0.28f; // resting tilt of the knife in the hand

	/**
	 * @param knifeRot rotation of the whole knife around the hand
	 * @param handleA  angle of handle A (front)
	 * @param handleB  angle of handle B (back)
	 * @param holdA    which handle the hand holds
	 * @param wrist    wrist turn in degrees (shows the sides of the blade)
	 * @param dx       hand offset sideways
	 * @param dy       hand offset up
	 * @param roll     tilt towards the camera in degrees
	 */
	public record Pose(float knifeRot, float handleA, float handleB, float holdA, float wrist, float dx, float dy,
			float roll) {
	}

	// t, knifeRot, handleA, handleB, holdA, wrist, dx, dy, roll
	private static final float[][] KEYS = {
			{ 0.00f, B, 0, 0, 0.5f, 0, 0, 0, 0 },
			// wind up, then fan-close: both handles swing onto the blade and snap
			{ 0.22f, B + 0.30f, 0.10f, -0.10f, 0.5f, -6, 0.015f, -0.03f, 0 },
			{ 0.52f, B - 0.18f, -PI - 0.10f, PI + 0.10f, 0.5f, 3, -0.01f, 0.02f, 0 },
			{ 0.62f, B - 0.10f, -PI + 0.07f, PI - 0.07f, 0.5f, 2, 0, 0.01f, 0 },
			{ 0.72f, B - 0.12f, -PI, PI, 0.5f, 0, 0, 0, 0 },
			// cock the wrist and flick it open: the handles bounce off the latch
			{ 0.95f, B + 0.34f, -PI, PI, 0.5f, -8, 0.02f, -0.04f, 0 },
			{ 1.26f, B - 0.20f, -TAU - 0.14f, TAU + 0.14f, 0.5f, 4, -0.02f, 0.05f, 0 },
			{ 1.36f, B - 0.06f, -TAU + 0.07f, TAU - 0.07f, 0.5f, 2, -0.01f, 0.03f, 0 },
			{ 1.48f, B, -TAU, TAU, 0.5f, 0, 0, 0.02f, 0 },
			// lift and look along the blade, then turn it to the other side
			{ 1.95f, B + 0.42f, -TAU, TAU, 0.5f, 62, -0.10f, 0.10f, -14 },
			{ 2.35f, B + 0.40f, -TAU, TAU, 0.5f, 68, -0.11f, 0.11f, -16 },
			{ 2.80f, B + 0.36f, -TAU, TAU, 0.5f, -38, -0.09f, 0.10f, -10 },
			{ 3.05f, B + 0.34f, -TAU, TAU, 0.5f, -42, -0.09f, 0.10f, -10 },
			{ 3.35f, B, -TAU, TAU, 0.5f, 0, 0, 0.02f, 0 },
			// aerial: a full spin with a small toss
			{ 3.55f, B + 0.25f, -TAU, TAU, 0.5f, 0, 0, -0.03f, 0 },
			{ 3.85f, B - PI, -TAU, TAU, 0.5f, 0, 0, 0.17f, 0 },
			{ 4.12f, B - TAU, -TAU, TAU, 0.5f, 0, 0, -0.02f, 0 },
			{ 4.22f, B - TAU, -TAU, TAU, 0.0f, 0, 0, 0, 0 },
			// rollover of handle A (hand holds B), then of handle B (hand holds A)
			{ 4.80f, B - TAU + 0.18f, -2 * TAU, TAU, 0.0f, 4, 0, 0.01f, 0 },
			{ 4.90f, B - TAU, -2 * TAU, TAU, 1.0f, 0, 0, 0, 0 },
			{ 5.48f, B - TAU - 0.18f, -2 * TAU, 2 * TAU, 1.0f, -4, 0, 0.01f, 0 },
			{ 5.58f, B - TAU, -2 * TAU, 2 * TAU, 0.5f, 0, 0, 0, 0 },
			{ 6.00f, B - TAU, -2 * TAU, 2 * TAU, 0.5f, 0, 0, 0, 0 },
	};

	/** Moments the latch clicks (seconds). */
	static final float[] CLICKS = { 0.66f, 1.32f, 4.85f, 5.53f };

	public static final float LENGTH = KEYS[KEYS.length - 1][0];

	private static final float[] TIMES = new float[KEYS.length];
	private static final float[][] VALUES = new float[8][KEYS.length];

	static {
		for (int i = 0; i < KEYS.length; i++) {
			TIMES[i] = KEYS[i][0];
			for (int c = 0; c < 8; c++) {
				VALUES[c][i] = KEYS[i][c + 1];
			}
		}
	}

	private KnifeAnimation() {
	}

	public static Pose at(float seconds) {
		float t = Math.max(0f, Math.min(LENGTH, seconds));
		float[] v = new float[8];
		for (int c = 0; c < 8; c++) {
			v[c] = KnifeInspect.curve(TIMES, VALUES[c], t);
		}
		// a little life in the hand while it is lifted
		float env = (float) Math.sin(Math.PI * t / LENGTH);
		v[6] += 0.004f * env * (float) Math.sin(t * 5.3f);
		v[7] += 1.2f * env * (float) Math.sin(t * 3.9f);
		return new Pose(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
	}
}
