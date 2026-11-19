package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Your own cape (Studio -> Cape Designer): a 10x16 design (the cape's back as others would see it) saved in
 * config/pinvisuals/cape.png. From it a full cape texture (128x64, same layout as the built-in capes) is built.
 */
public final class CapeStore {

	public static final int W = 10;
	public static final int H = 16;
	public static final Identifier ID = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID,
			"cosmetics/cape_custom");
	private static boolean loaded;

	private CapeStore() {
	}

	static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("pinvisuals").resolve("cape.png");
	}

	/** The design (ARGB, W*H), or a default gradient. */
	public static int[] load() {
		int[] px = new int[W * H];
		Path f = file();
		if (Files.isRegularFile(f)) {
			try (NativeImage img = NativeImage.read(Files.newInputStream(f))) {
				if (img.getWidth() == W && img.getHeight() == H) {
					for (int y = 0; y < H; y++) {
						for (int x = 0; x < W; x++) {
							px[y * W + x] = img.getPixel(x, y);
						}
					}
					return px;
				}
			} catch (Exception e) {
				TextureStudioClient.LOGGER.warn("Pin Visuals: cape design could not be read", e);
			}
		}
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				float t = y / (float) (H - 1);
				px[y * W + x] = mix(0xFF7C3AED, 0xFF22D3EE, t);
			}
		}
		return px;
	}

	public static void save(int[] px) throws java.io.IOException {
		Path f = file();
		Files.createDirectories(f.getParent());
		try (NativeImage img = new NativeImage(W, H, false)) {
			for (int y = 0; y < H; y++) {
				for (int x = 0; x < W; x++) {
					img.setPixel(x, y, px[y * W + x] | 0xFF000000);
				}
			}
			img.writeToFile(f);
		}
		upload(px);
	}

	/** Texture id of the custom cape (built on first use). */
	public static Identifier texture() {
		if (!loaded) {
			loaded = true;
			upload(load());
		}
		return ID;
	}

	private static void upload(int[] px) {
		NativeImage img = new NativeImage(128, 64, true);
		int edge = average(px);
		// whole layout first in the edge colour (sides, top, bottom), then front and back faces
		for (int y = 0; y < 34; y++) {
			for (int x = 0; x < 44; x++) {
				img.setPixel(x, y, edge);
			}
		}
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int c = px[y * W + x] | 0xFF000000;
				int m = px[y * W + (W - 1 - x)] | 0xFF000000;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						img.setPixel((1 + x) * 2 + dx, (1 + y) * 2 + dy, c); // outside (seen from behind)
						img.setPixel((12 + x) * 2 + dx, (1 + y) * 2 + dy, mix(m, 0xFF000000, 0.25f)); // inside
					}
				}
			}
		}
		Minecraft mc = Minecraft.getInstance();
		mc.getTextureManager().register(ID, new DynamicTexture(() -> "pin visuals custom cape", img));
	}

	private static int average(int[] px) {
		long r = 0;
		long g = 0;
		long b = 0;
		for (int c : px) {
			r += (c >> 16) & 255;
			g += (c >> 8) & 255;
			b += c & 255;
		}
		int n = Math.max(1, px.length);
		return mix(0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n), 0xFF000000, 0.35f);
	}

	static int mix(int a, int b, float t) {
		int rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
	}
}
