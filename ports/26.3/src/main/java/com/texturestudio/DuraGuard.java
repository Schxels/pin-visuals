package com.texturestudio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dura Guard (Player): stops mining (and hitting, for swords) while the tool in your hand is about to break. For
 * which tools and at how much durability it stops are settings; sneaking can let you go on anyway.
 */
final class DuraGuard {

	static final String[] MODES = { "Durability left", "Percent left" };

	private static long lastWarn;

	private DuraGuard() {
	}

	static boolean on() {
		return Features.duraOn != null && Features.duraOn.b();
	}

	/** Is this kind of tool watched? */
	private static boolean watched(ItemStack s) {
		if (s.is(ItemTags.PICKAXES)) {
			return Features.duraPick.b();
		}
		if (s.is(ItemTags.AXES)) {
			return Features.duraAxe.b();
		}
		if (s.is(ItemTags.SHOVELS)) {
			return Features.duraShovel.b();
		}
		if (s.is(ItemTags.HOES)) {
			return Features.duraHoe.b();
		}
		if (s.is(ItemTags.SWORDS)) {
			return Features.duraSword.b();
		}
		if (s.is(Items.SHEARS)) {
			return Features.duraShears.b();
		}
		return Features.duraOther.b();
	}

	/** Durability left on the stack. */
	static int left(ItemStack s) {
		return s.getMaxDamage() - s.getDamageValue();
	}

	/** True when the held item must not be used now (for mining, or for attacking when attack = true). */
	static boolean blocks(boolean attack) {
		Minecraft mc = Minecraft.getInstance();
		if (!on() || mc.player == null || mc.gameMode == null || mc.player.isCreative() || mc.player.isSpectator()) {
			return false;
		}
		ItemStack s = mc.player.getMainHandItem();
		if (s.isEmpty() || !s.isDamageableItem() || !watched(s)) {
			return false;
		}
		if (attack && !s.is(ItemTags.SWORDS) && !s.is(ItemTags.AXES)) {
			return false;
		}
		if (Features.duraSneak.b() && mc.player.isShiftKeyDown()) {
			return false;
		}
		int left = left(s);
		boolean low = Features.duraMode.i() == 1
				? left * 100.0 / Math.max(1, s.getMaxDamage()) <= Features.duraPercent.get()
				: left <= Features.duraLeft.i();
		if (low) {
			warn(s, left);
		}
		return low;
	}

	private static void warn(ItemStack s, int left) {
		long now = System.currentTimeMillis();
		if (now - lastWarn < 1500) {
			return;
		}
		lastWarn = now;
		Minecraft mc = Minecraft.getInstance();
		if (Features.duraWarn.b()) {
			Features.notice("Dura Guard", s.getHoverName().getString() + ": only " + left + " durability left"
					+ (Features.duraSneak.b() ? " (sneak to keep going)" : ""));
		}
		if (Features.duraSound.b() && mc.player != null) {
			mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
		}
	}
}
