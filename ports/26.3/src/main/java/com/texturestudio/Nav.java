package com.texturestudio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opening screens (the only place that differs between Minecraft versions). */
final class Nav {

	private Nav() {
	}

	static void open(Screen s) {
		Minecraft.getInstance().gui.setScreen(s);
	}

	static Screen options(Screen parent) {
		return new net.minecraft.client.gui.screens.options.OptionsScreen(parent, Minecraft.getInstance().options);
	}

	/** Is this key held right now (key codes as in KeyEvent)? */
	static boolean keyDown(int key) {
		return com.mojang.blaze3d.platform.InputConstants.isKeyDown(key);
	}

	static Screen current() {
		return Minecraft.getInstance().gui.screen();
	}
}
