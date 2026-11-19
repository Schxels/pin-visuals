package com.texturestudio;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Zoom (Render): while the zoom key is held the field of view shrinks smoothly; the mouse wheel zooms further. */
public final class Zoom {

	static KeyMapping key;
	private static boolean held;
	private static double scroll = 1;
	private static float cur = 1f;
	private static long last = System.nanoTime();
	private static Boolean savedCinematic;

	private Zoom() {
	}

	/** Called every client tick. */
	static void tick(Minecraft mc) {
		boolean h = key != null && key.isDown() && mc.player != null
				&& Nav.current() == null;
		if (h && !held) {
			scroll = 1;
			if (Features.zoomCinematic.b()) {
				savedCinematic = mc.options.smoothCamera;
				mc.options.smoothCamera = true;
			}
		} else if (!h && held && savedCinematic != null) {
			mc.options.smoothCamera = savedCinematic;
			savedCinematic = null;
		}
		held = h;
	}

	/** Field of view after the zoom (called by the camera every frame). */
	public static float apply(float fov) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
		last = now;
		if (Features.zoomFactor == null) {
			return fov;
		}
		float target = held ? (float) (Features.zoomFactor.get() * scroll) : 1f;
		float speed = 30f - 26f * Features.zoomSmooth.f() / 100f; // 0 % = almost instant, 100 % = slow glide
		cur += (target - cur) * Math.min(1f, dt * speed);
		if (Math.abs(cur - target) < 0.002f) {
			cur = target;
		}
		return cur <= 1.0001f ? fov : fov / cur;
	}

	/** Mouse wheel while zooming: true = used for the zoom (the hotbar does not scroll). */
	public static boolean scroll(double dy) {
		if (!held || Features.zoomScroll == null || !Features.zoomScroll.b() || dy == 0) {
			return false;
		}
		scroll *= dy > 0 ? 1.18 : 1 / 1.18;
		double max = 30.0 / Features.zoomFactor.get();
		scroll = Math.max(0.5, Math.min(max, scroll));
		return true;
	}

	/** Is the view zoomed in right now? */
	public static boolean active() {
		return cur > 1.03f;
	}
}
