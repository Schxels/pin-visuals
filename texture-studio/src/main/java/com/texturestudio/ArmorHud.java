package com.texturestudio;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Armor HUD: the worn armor pieces stacked left of the hotbar, each with its remaining durability.
 * Styles: "bars" (percent + bar), "glass" (the same on a glass panel), "compact" (remaining points only).
 */
public final class ArmorHud {

	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET };
	private static final int ROW = 18;

	private ArmorHud() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		String style = Cosmetics.armorHud;
		Minecraft mc = Minecraft.getInstance();
		if (Cosmetics.NONE.equals(style) || mc.player == null || mc.player.isSpectator()) {
			return;
		}
		draw(g, mc.font, mc.player, style, g.guiWidth(), g.guiHeight());
	}

	private static void draw(GuiGraphicsExtractor g, Font font, Player p, String style, int w, int h) {
		ItemStack[] items = new ItemStack[4];
		int n = 0;
		for (int i = 0; i < 4; i++) {
			ItemStack s = p.getItemBySlot(SLOTS[i]);
			if (!s.isEmpty()) {
				items[i] = s;
				n++;
			}
		}
		if (n == 0) {
			return;
		}
		boolean compact = "compact".equals(style);
		int panelW = compact ? 46 : 58;
		int right = w / 2 - 91 - 6;
		int x = right - panelW;
		int y = h - 2 - n * ROW;
		long now = System.currentTimeMillis();

		if ("glass".equals(style)) {
			int px = x - 4;
			int py = y - 4;
			int pw = panelW + 8;
			int ph = n * ROW + 6;
			FillBatch.fill(g, px + 1, py, px + pw - 1, py + ph, 0x90100A1E);
			FillBatch.fill(g, px, py + 1, px + pw, py + ph - 1, 0x90100A1E);
			FillBatch.fill(g, px + 2, py, px + pw - 2, py + 1, 0x60FFFFFF);
			FillBatch.fill(g, px, py + 2, px + 1, py + ph - 2, 0x407DD3FC);
			FillBatch.fill(g, px + pw - 1, py + 2, px + pw, py + ph - 2, 0x407DD3FC);
			FillBatch.fill(g, px + 2, py + ph - 1, px + pw - 2, py + ph, 0x407DD3FC);
		}

		int row = 0;
		for (int i = 0; i < 4; i++) {
			ItemStack s = items[i];
			if (s == null) {
				continue;
			}
			int ry = y + row * ROW;
			row++;
			FillBatch.flush(g).fakeItem(s, x, ry);
			if (!s.isDamageableItem() || s.getMaxDamage() <= 0) {
				continue;
			}
			int max = s.getMaxDamage();
			int left = max - s.getDamageValue();
			float f = Math.max(0f, Math.min(1f, left / (float) max));
			int col = color(f);
			if (f < 0.15f && (now / 300) % 2 == 0) {
				col = 0xFFFFFFFF; // blink when almost broken
			}
			int tx = x + 19;
			if (compact) {
				FillBatch.flush(g).text(font, Integer.toString(left), tx, ry + 4, col, true);
			} else {
				String pct = Math.round(f * 100) + "%";
				FillBatch.flush(g).text(font, pct, tx, ry + 1, col, true);
				int bw = panelW - 20;
				FillBatch.fill(g, tx, ry + 11, tx + bw, ry + 14, 0xA0000000);
				FillBatch.fill(g, tx, ry + 11, tx + Math.max(1, Math.round(bw * f)), ry + 13, col | 0xFF000000);
			}
		}
	}

	/** Green (full) -> yellow -> red (almost broken). */
	static int color(float f) {
		if (f > 0.6f) {
			return 0xFF4ADE80;
		}
		if (f > 0.3f) {
			return 0xFFFACC15;
		}
		return 0xFFF87171;
	}
}
