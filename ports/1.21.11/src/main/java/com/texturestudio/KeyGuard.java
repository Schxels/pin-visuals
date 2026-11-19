package com.texturestudio;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Key conflicts: other mods often use the same keys (NoRisk zoom on C, voice chat on V, Alt for the hotbar ...).
 * Once, on the first start with these keys, every Pin Visuals key that collides with another key is moved to a free
 * one and a short note is shown. After that the player's own choice is never changed again.
 */
final class KeyGuard {

	private record Guarded(KeyMapping key, String name, int[] candidates) {
	}

	private static final List<Guarded> KEYS = new ArrayList<>();
	private static boolean done;

	private KeyGuard() {
	}

	static void guard(KeyMapping key, String name, int... candidates) {
		KEYS.add(new Guarded(key, name, candidates));
	}

	/** Called every tick until it ran once (the options with the saved keys must be loaded). */
	static void tick(Minecraft mc, MixConfig cfg) {
		if (done || mc.options == null || mc.player == null) {
			return;
		}
		done = true;
		if (cfg.keyCheck >= 1) {
			return;
		}
		List<String> moved = new ArrayList<>();
		for (Guarded g : KEYS) {
			KeyMapping k = g.key();
			if (k == null || k.isUnbound() || !taken(mc, k, KeyBindingHelper.getBoundKeyOf(k).getValue())) {
				continue;
			}
			for (int code : g.candidates()) {
				if (!taken(mc, k, code)) {
					k.setKey(InputConstants.Type.KEYSYM.getOrCreate(code));
					moved.add(g.name() + ": " + PinSettings.keyName(code));
					break;
				}
			}
		}
		KeyMapping.resetMapping();
		mc.options.save();
		cfg.keyCheck = 1;
		cfg.save();
		if (!moved.isEmpty()) {
			Features.notice("Keys changed (taken by other mods)", String.join(", ", moved));
		}
	}

	/** Is this key code used by any other key mapping? */
	private static boolean taken(Minecraft mc, KeyMapping self, int code) {
		for (KeyMapping other : mc.options.keyMappings) {
			if (other != self && !other.isUnbound() && KeyBindingHelper.getBoundKeyOf(other).getValue() == code) {
				return true;
			}
		}
		return false;
	}
}
