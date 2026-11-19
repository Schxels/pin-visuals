package com.texturestudio;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Freelook (Render): while the freelook key is held (or after a press in toggle mode) the mouse turns only the
 * camera, not the player. The player keeps walking and looking where he did. Optionally the camera goes into third
 * person for that time and comes back afterwards.
 */
public final class Freelook {

	static KeyMapping key;
	private static boolean active;
	private static boolean toggled;
	private static boolean wasDown;
	private static float yaw;
	private static float pitch;
	private static CameraType saved;

	private Freelook() {
	}

	static void tick(Minecraft mc) {
		boolean on = key != null && mc.player != null
				&& Nav.current() == null;
		boolean down = on && key.isDown();
		if (Features.freeMode.i() == 1) {
			if (down && !wasDown) {
				toggled = !toggled;
			}
			if (!on) {
				toggled = false;
			}
		} else {
			toggled = down;
		}
		wasDown = down;
		boolean want = on && toggled;
		if (want && !active) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			if (Features.freeThird.b() && mc.options.getCameraType().isFirstPerson()) {
				saved = mc.options.getCameraType();
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			active = true;
		} else if (!want && active) {
			active = false;
			if (saved != null) {
				mc.options.setCameraType(saved);
				saved = null;
			}
		}
	}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** Mouse movement while freelook is on (same scale as Entity.turn). */
	public static void turn(double dx, double dy) {
		float k = Features.freeSpeed.f() / 100f;
		float sign = Features.freeInvert.b() ? -1f : 1f;
		yaw += (float) dx * 0.15f * k;
		pitch = Math.max(-90f, Math.min(90f, pitch + sign * (float) dy * 0.15f * k));
	}
}
