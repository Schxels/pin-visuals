package com.texturestudio.mixin;

import com.texturestudio.Tweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pin Visuals loading screen (Settings -> Pin Visuals menu): paints a white screen with the Pin Visuals mark and a
 * thin colour progress bar over the vanilla one, fading exactly like it. Only plain shapes (no text, no textures),
 * because during the very first load nothing else is ready yet.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

	@Shadow
	private float currentProgress;
	@Shadow
	private long fadeOutStart;
	@Shadow
	private long fadeInStart;
	@Shadow
	@Final
	private boolean fadeIn;

	// with the Pin Visuals look the vanilla parts are hidden (else they shine through while both fade out)

	@Redirect(method = "extractRenderState", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/ARGB;white(F)I"))
	private int pinvisuals$hideLogo(float alpha) {
		return Tweaks.pinMenu ? 0 : net.minecraft.util.ARGB.white(alpha);
	}

	@Redirect(method = "extractRenderState", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;replaceAlpha(II)I"))
	private int pinvisuals$whiteBackground(int color, int alpha) {
		return ((Tweaks.pinMenu ? 0xF4F5FA : color) & 0xFFFFFF) | (alpha << 24);
	}

	@Redirect(method = "extractRenderState", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
	private void pinvisuals$hideBar(LoadingOverlay self, GuiGraphicsExtractor g, int x0, int y0, int x1, int y1,
			float a) {
		if (!Tweaks.pinMenu) {
			extractProgressBar(g, x0, y0, x1, y1, a);
		}
	}

	@Redirect(method = "extractRenderState", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/ARGB;setVector4fFromARGB32(Lorg/joml/Vector4f;I)Lorg/joml/Vector4f;"))
	private org.joml.Vector4f pinvisuals$clearColor(org.joml.Vector4f v, int color) {
		return net.minecraft.util.ARGB.setVector4fFromARGB32(v, Tweaks.pinMenu ? 0xFFF4F5FA : color);
	}

	@Shadow
	private void extractProgressBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float a) {
		throw new AssertionError();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void pinvisuals$whiteLoading(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!Tweaks.pinMenu) {
			return;
		}
		long now = net.minecraft.util.Util.getMillis();
		float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
		float in = fadeInStart > -1L ? (now - fadeInStart) / 500f : -1f;
		float a;
		if (out >= 1f) {
			a = 1f - Math.min(1f, out - 1f);
		} else if (fadeIn) {
			a = Math.max(0f, Math.min(1f, in));
		} else {
			a = 1f;
		}
		if (a <= 0.003f) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		int al = Math.round(a * 255);
		// background: white to a soft lavender
		int bands = 24;
		for (int i = 0; i < bands; i++) {
			int y0 = h * i / bands;
			int y1 = h * (i + 1) / bands;
			g.fill(0, y0, w, y1, argb(al, mix(0xFFFFFF, 0xEEF0F8, i / (float) (bands - 1))));
		}
		// the mark: a rounded square with a gradient and a white "P"
		int s = Math.max(32, Math.min(64, h / 6));
		int x = (w - s) / 2;
		int y = h / 2 - s;
		int px = s / 8;
		for (int i = 0; i < s; i++) {
			int ins = i < px ? px - i : i >= s - px ? i - (s - px) + 1 : 0;
			int c = mix(0x7C3AED, 0x22D3EE, i / (float) s);
			g.fill(x + ins, y + i, x + s - ins, y + i + 1, argb(al, c));
		}
		String[] p = { "#####.", "##..##", "##..##", "#####.", "##....", "##....", "##...." };
		int cell = Math.max(2, s / 12);
		int gx = x + (s - 6 * cell) / 2;
		int gy = y + (s - 7 * cell) / 2;
		for (int r = 0; r < p.length; r++) {
			for (int c = 0; c < p[r].length(); c++) {
				if (p[r].charAt(c) == '#') {
					g.fill(gx + c * cell, gy + r * cell, gx + (c + 1) * cell, gy + (r + 1) * cell, argb(al, 0xFFFFFF));
				}
			}
		}
		// progress bar
		int bw = Math.min(220, w * 2 / 5);
		int bx = (w - bw) / 2;
		int by = y + s + 22;
		g.fill(bx, by, bx + bw, by + 4, argb(Math.round(al * 0.25f), 0x94A3B8));
		int fw = Math.round(bw * Math.max(0f, Math.min(1f, currentProgress)));
		for (int i = 0; i < fw; i++) {
			g.fill(bx + i, by, bx + i + 1, by + 4, argb(al, mix(0x7C3AED, 0x22D3EE, i / (float) bw)));
		}
	}

	private static int argb(int a, int rgb) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (gg << 8) | bb;
	}
}
