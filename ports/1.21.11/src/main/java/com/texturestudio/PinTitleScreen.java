package com.texturestudio;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Pin Visuals title screen (Settings -> Pin Visuals menu): white, calm, with the logo and big buttons. Shown instead
 * of the vanilla title screen while the option is on; "Vanilla menu" switches back for this session.
 */
final class PinTitleScreen extends ToolScreen {

	/** The player chose the vanilla menu for this session. */
	static boolean bypass;

	private static final int INK = 0xFF111827;
	private static final int INK_SOFT = 0xFF64748B;

	private final String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	private final String modVersion = FabricLoader.getInstance().getModContainer(TextureStudioClient.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");

	PinTitleScreen() {
		this(new TextureStudioScreen(null));
	}

	private PinTitleScreen(TextureStudioScreen home) {
		super("Pin Visuals", home, null);
	}

	@Override
	protected boolean plain() {
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void build() {
		int bw = Math.min(200, width - 40);
		int bh = 22;
		int x = (width - bw) / 2;
		int y = height / 2 + 14;
		big(x, y, bw, bh, "Singleplayer", true,
				() -> Nav.open(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this)));
		y += bh + 6;
		big(x, y, bw, bh, "Multiplayer", false,
				() -> Nav.open(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this)));
		y += bh + 6;
		int half = (bw - 6) / 2;
		big(x, y, half, bh, "Pin Visuals", false, this::openMenu);
		big(x + half + 6, y, bw - half - 6, bh, "Options", false, () -> Nav.open(Nav.options(this)));
		y += bh + 6;
		big(x, y, bw, bh, "Quit Game", false, minecraft::stop);
		String v = "Vanilla menu";
		int vw = home.uiFont.width(v);
		int vx = width - vw - 10;
		int vy = height - 16;
		add(vx - 2, vy - 2, vw + 4, 12, () -> {
			bypass = true;
			Nav.open(new net.minecraft.client.gui.screens.TitleScreen());
		}, null, (g, hov, p) -> home.text(g, v, vx, vy, hov ? home.accent() : INK_SOFT));
	}

	private String note = "";
	private long noteAt;

	/**
	 * The full menu shows items, and item stacks only work once the game bound the item data (after the first
	 * world). Before that a hint is shown instead of a crash.
	 */
	private void openMenu() {
		try {
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
		} catch (RuntimeException e) {
			note = "Join a world once, then press O to open Pin Visuals";
			noteAt = System.currentTimeMillis();
			return;
		}
		Nav.open(new TextureStudioScreen(this));
	}

	@Override
	protected void drawOver(GuiGraphics g) {
		if (!note.isEmpty() && System.currentTimeMillis() - noteAt < 4000) {
			home.ctext(g, note, width / 2, height / 2 + 14 + 4 * 28 + 4, 0xFF7C3AED);
		}
	}

	private void big(int x, int y, int w, int h, String label, boolean primary, Runnable run) {
		add(x, y, w, h, run, null, (g, hov, p) -> {
			int acc = home.accent();
			if (hov) {
				home.softGlow(g, x - 10, y - 8, w + 20, h + 16, acc, primary ? 0x90 : 0x50);
			}
			if (primary) {
				TextureStudioScreen.hgrad(g, x, y, w, h, 7, acc, home.acc3());
				TextureStudioScreen.outline(g, x, y, w, h, 7, TextureStudioScreen.alpha(0xFFFFFF, 0x80));
				home.ctext(g, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
			} else {
				TextureStudioScreen.rrect(g, x, y + 1, w, h, 7, 0x14000000);
				TextureStudioScreen.rrect(g, x, y, w, h, 7, hov ? TextureStudioScreen.mix(0xFFFFFFFF, acc, 0.08f) : 0xFFFFFFFF);
				TextureStudioScreen.outline(g, x, y, w, h, 7, hov ? acc : 0xFFE2E5EE);
				home.ctext(g, label, x + w / 2, y + (h - 8) / 2, hov ? INK : 0xFF334155);
			}
		});
	}

	@Override
	protected void drawContent(GuiGraphics g) {
		int acc = home.accent();
		TextureStudioScreen.vgrad(g, 0, 0, width, height, 0, 0, 0xFFFFFFFF, 0xFFEEF0F8);
		// slow, soft colour light drifting in the background
		double t = System.currentTimeMillis() / 9000.0;
		int r = Math.max(width, height) / 2;
		home.softGlow(g, (int) (width * (0.25 + 0.12 * Math.sin(t))) - r / 2, (int) (height * (0.3 + 0.1 * Math.cos(t * 1.3))) - r / 2,
				r, r, acc, 0x38);
		home.softGlow(g, (int) (width * (0.75 + 0.1 * Math.cos(t * 0.8))) - r / 2, (int) (height * (0.65 + 0.1 * Math.sin(t))) - r / 2,
				r, r, home.acc3(), 0x30);
		int s = 54;
		int lx = (width - s) / 2;
		int ly = height / 2 - 112;
		home.softGlow(g, lx - 20, ly - 16, s + 40, s + 40, acc, 0x60);
		TextureStudioScreen.rrect(g, lx, ly + 2, s, s, 8, 0x22000000);
		TextureStudioScreen.rrect(g, lx, ly, s, s, 8, 0xFFFFFFFF);
		TextureStudioScreen.outline(g, lx, ly, s, s, 8, 0xFFE2E5EE);
		FillBatch.flush(g).blit(RenderPipelines.GUI_TEXTURED, TextureStudioScreen.LOGO, lx + 5, ly + 5, 0, 0, s - 10, s - 10, 64,
				64, 64, 64);
		String title = "PIN VISUALS";
		float sc = 2f;
		int tw = Math.round(home.titleFont.width(title) * sc);
		home.titleText(g, title, (width - tw) / 2, ly + s + 10, sc, INK);
		String sub = "Minecraft " + mcVersion;
		home.ctext(g, sub, width / 2, ly + s + 32, INK_SOFT);
		home.text(g, "Pin Visuals " + modVersion, 10, height - 16, INK_SOFT);
	}
}
