package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Studio -> Sky Designer: build your own sky (colours, glowing strings / lightning / aurora, how many, how thick, how
 * bright) with a live 360 degree preview. "Save and use" writes it as the pack "PinVisuals Sky" and puts it into the
 * Sky collection of your Mix.
 */
final class SkyDesignerScreen extends ToolScreen {

	private static final String[] STYLES = { "Strings", "Lightning", "Aurora" };
	private static final Identifier PREVIEW = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "sky_designer_preview");
	private static final int PW = 360;
	private static final int PH = 90;

	private int lastKey;
	private boolean uploaded;
	private String message = "";
	private long messageAt;
	private boolean saving;

	SkyDesignerScreen(TextureStudioScreen home, Screen back) {
		super("Sky Designer", home, back);
	}

	@Override
	protected String subtitle() {
		return "Make your own sky - it goes into your Mix as \"" + SkyGen.PACK + "\"";
	}

	@Override
	protected int preferredWidth() {
		return 520;
	}

	@Override
	protected int preferredHeight() {
		return 330;
	}

	private SkyGen.Spec spec() {
		return new SkyGen.Spec(cfg.skyStyle, cfg.skyTop, cfg.skyHorizon, cfg.skyLine, cfg.skyCount, cfg.skyWidth,
				cfg.skyGlow, cfg.skySeed);
	}

	@Override
	protected void build() {
		int x = wx + 12;
		int w = ww - 24;
		int y = top() + previewH() + 8;
		int half = (w - 14) / 2;
		int x2 = x + half + 14;
		segment(x, y, half, 16, STYLES, () -> cfg.skyStyle, v -> cfg.skyStyle = v);
		button(x2, y, (half - 6) / 2, 16, "New shape", false, () -> {
			cfg.skySeed = new java.util.Random().nextLong();
			changed();
		});
		button(x2 + (half - 6) / 2 + 6, y, half - (half - 6) / 2 - 6, 16, "Classic", false, () -> {
			cfg.skyStyle = 0;
			cfg.skyTop = 0xFF050507;
			cfg.skyHorizon = 0xFF3A3D44;
			cfg.skyLine = 0xFFFFFFFF;
			cfg.skyCount = 9;
			cfg.skyWidth = 3;
			cfg.skyGlow = 70;
			changed();
		});
		y += 22;
		intSlider(x, y, half, "Lines", "", 1, 30, () -> cfg.skyCount, v -> cfg.skyCount = v);
		intSlider(x2, y, half, "Thickness", "", 1, 10, () -> cfg.skyWidth, v -> cfg.skyWidth = v);
		y += 26;
		intSlider(x, y, half, "Glow", "%", 0, 100, () -> cfg.skyGlow, v -> cfg.skyGlow = v);
		y += 26;
		int cw = (w - 28) / 3;
		label3(x, y, cw);
		y += 11;
		colorPicker(x, y, cw, () -> cfg.skyTop, v -> cfg.skyTop = v);
		colorPicker(x + cw + 14, y, cw, () -> cfg.skyHorizon, v -> cfg.skyHorizon = v);
		colorPicker(x + 2 * (cw + 14), y, cw, () -> cfg.skyLine, v -> cfg.skyLine = v);
		button(x2 + half - 120, top() + previewH() + 8 + 48, 120, 18, saving ? "Saving ..." : "Save and use", true,
				this::save);
	}

	private int labelsY;
	private int labelsX;
	private int labelsW;

	private void label3(int x, int y, int cw) {
		labelsX = x;
		labelsY = y;
		labelsW = cw;
	}

	private int previewH() {
		return Math.round((ww - 24) * PH / (float) PW);
	}

	private void refreshPreview() {
		SkyGen.Spec s = spec();
		int key = s.hashCode();
		if (key == lastKey && uploaded) {
			return;
		}
		lastKey = key;
		int[] px = new SkyGen(s).panorama(PW, PH);
		NativeImage img = new NativeImage(PW, PH, false);
		for (int y = 0; y < PH; y++) {
			for (int x = 0; x < PW; x++) {
				img.setPixel(x, y, px[y * PW + x]);
			}
		}
		Minecraft.getInstance().getTextureManager().register(PREVIEW, new DynamicTexture(() -> "sky designer", img));
		uploaded = true;
	}

	private void save() {
		if (saving) {
			return;
		}
		saving = true;
		message = "Painting your sky ...";
		messageAt = System.currentTimeMillis();
		cfg.save();
		SkyGen gen = new SkyGen(spec());
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			try {
				gen.writePack(mc.getResourcePackDirectory(), 512);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}).whenComplete((v, err) -> mc.execute(() -> {
			saving = false;
			if (err != null) {
				message = "Could not save: " + err.getMessage();
			} else {
				try {
					home.packsChanged();
					PackIndex idx = PackIndex.scan(mc.getResourcePackDirectory());
					cfg.styles.put("sky", SkyGen.PACK);
					cfg.save();
					MixBuilder.apply(mc, idx, cfg);
					message = "Your sky is on (Sets -> Sky: " + SkyGen.PACK + ")";
				} catch (Exception e) {
					message = "Saved, but the Mix failed: " + e.getMessage();
				}
			}
			messageAt = System.currentTimeMillis();
			if (Nav.current() == this) {
				rebuild();
			}
		}));
		rebuild();
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g) {
		refreshPreview();
		int x = wx + 12;
		int w = ww - 24;
		int h = previewH();
		int y = top();
		TextureStudioScreen.rrect(g, x - 1, y - 1, w + 2, h + 2, 4, home.BORDER);
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, PREVIEW, x, y, 0f, 0f, w, h, PW, PH, PW, PH);
		// horizon mark and a little ground so the preview reads as "around you"
		int hy = y + Math.round(h * 90f / 105f);
		FillBatch.fill(g, x, hy, x + w, y + h, 0x9910130F);
		home.text(g, "360° preview", x + 6, y + 5, 0xCCFFFFFF);
		if (labelsW > 0) {
			home.text(g, "Sky top", labelsX, labelsY, home.DIM);
			home.text(g, "Horizon", labelsX + labelsW + 14, labelsY, home.DIM);
			home.text(g, "Lines", labelsX + 2 * (labelsW + 14), labelsY, home.DIM);
		}
	}

	@Override
	protected void drawOver(GuiGraphicsExtractor g) {
		if (!message.isEmpty() && (saving || System.currentTimeMillis() - messageAt < 5000)) {
			int mw = home.uiFont.width(message) + 16;
			int mx0 = wx + (ww - mw) / 2;
			int my0 = wy + wh - 26;
			TextureStudioScreen.rrect(g, mx0, my0, mw, 16, 5, TextureStudioScreen.alpha(home.CARD, 0xF0));
			TextureStudioScreen.outline(g, mx0, my0, mw, 16, 5, home.accent());
			home.ctext(g, message, mx0 + mw / 2, my0 + 4, home.TEXT);
		}
	}
}
