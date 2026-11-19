package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * Item Swap Animation (Player): when you switch hotbar slots the new item glides into your hand (instead of the hard
 * lower-and-raise) and the hotbar selection slides over to the new slot.
 */
final class ItemSwap {

	static final String[] STYLES = { "Slide up", "Swing in", "Flip", "Spin" };

	private static int lastSlot = -1;
	private static ItemStack lastStack = ItemStack.EMPTY;
	private static long swapStart = -1;
	private static float glide = -1;
	private static long lastFrame = System.nanoTime();

	private ItemSwap() {
	}

	static boolean on() {
		return Features.swapOn != null && Features.swapOn.b();
	}

	static void tick(Minecraft mc) {
		if (mc.player == null) {
			lastSlot = -1;
			return;
		}
		int slot = mc.player.getInventory().getSelectedSlot();
		ItemStack now = mc.player.getMainHandItem();
		if (lastSlot >= 0 && slot != lastSlot && on() && !ItemStack.isSameItem(now, lastStack)) {
			swapStart = System.nanoTime();
		}
		lastSlot = slot;
		lastStack = now;
	}

	private static float length() {
		return 0.28f * 100f / Features.swapSpeed.f();
	}

	/** 0 = just switched, 1 = done (or no animation). */
	private static float progress() {
		if (swapStart < 0) {
			return 1f;
		}
		float t = (System.nanoTime() - swapStart) / 1_000_000_000f / length();
		if (t >= 1f) {
			swapStart = -1;
			return 1f;
		}
		return t;
	}

	/** Hand transform while the new item comes in (main hand, first person). */
	static void transform(PoseStack pose, HumanoidArm arm) {
		Minecraft mc = Minecraft.getInstance();
		if (!on() || mc.player == null || arm != mc.player.getMainArm()) {
			return;
		}
		float t = progress();
		if (t >= 1f) {
			return;
		}
		// ease out with a tiny overshoot: quick at first, settles softly
		float c1 = 1.4f;
		float e = 1f + (c1 + 1f) * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
		float r = 1f - e;
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		switch (Features.swapStyle.i()) {
			case 1 -> {
				pose.translate(side * 0.38f * r, -0.22f * r, 0.05f * r);
				pose.rotate(new Quaternionf().rotateZ((float) Math.toRadians(-side * 40f * r)));
				pose.rotate(new Quaternionf().rotateY((float) Math.toRadians(side * 35f * r)));
			}
			case 2 -> {
				pose.translate(0f, -0.25f * r, 0f);
				pose.translate(0f, 0.15f, 0f);
				pose.rotate(new Quaternionf().rotateX((float) Math.toRadians(-360f * r)));
				pose.translate(0f, -0.15f, 0f);
			}
			case 3 -> {
				pose.translate(0f, -0.18f * r, 0f);
				pose.rotate(new Quaternionf().rotateY((float) Math.toRadians(side * 360f * r)));
			}
			default -> {
				pose.translate(0f, -0.6f * r, 0.04f * r);
				pose.rotate(new Quaternionf().rotateX((float) Math.toRadians(-28f * r)));
			}
		}
	}

	/** Extra x offset (GUI pixels) of the hotbar selection while it slides to the new slot. */
	static int hotbarOffset() {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		if (!on() || !Features.swapHotbar.b() || mc.player == null) {
			glide = -1;
			return 0;
		}
		int sel = mc.player.getInventory().getSelectedSlot();
		if (glide < 0) {
			glide = sel;
		}
		glide += (sel - glide) * Math.min(1f, dt * 22f * Features.swapSpeed.f() / 100f);
		if (Math.abs(sel - glide) < 0.02f) {
			glide = sel;
		}
		return Math.round((glide - sel) * 20f);
	}
}
