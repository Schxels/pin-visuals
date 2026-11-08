package com.texturestudio;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Laedt Texturen direkt aus den Packs als Vorschau (auch wenn das Pack gar nicht aktiv ist).
 * Lesen, Dekodieren und Verkleinern grosser Bilder (Skies) passiert im Hintergrund; im Render-Thread wird nur
 * hochgeladen, hoechstens {@link #UPLOADS_PER_FRAME} Bilder pro Frame. Bis ein Bild fertig ist, liefert
 * {@link #get} null und {@link #isLoading} true.
 * Alles, was hier registriert wird, wird beim Schliessen des Screens wieder freigegeben.
 */
public final class PreviewCache {

	public record Preview(Identifier id, int width, int height) {
	}

	/** Previews never need more than this; bigger images (sky sheets) are scaled down in the background. */
	private static final int MAX_SIZE = 512;
	private static final int UPLOADS_PER_FRAME = 2;

	private static final ExecutorService DECODER = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "PinVisuals-Preview");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private final Map<String, Preview> cache = new HashMap<>();
	private final Map<String, CompletableFuture<NativeImage>> pending = new HashMap<>();
	private final Set<String> failed = new HashSet<>();
	private int counter;
	private long frame = -1;
	private int uploadsThisFrame;
	private volatile boolean closed;

	public Preview get(PackInfo pack, String path) {
		return load(pack.id() + "|" + path, () -> PackIndex.read(pack, path));
	}

	/** Originaltextur des Spiels (fuer die Vanilla-Karte), Pfad wie "assets/minecraft/textures/....png". */
	public Preview vanilla(String path) {
		return load("vanilla|" + path, () -> {
			String prefix = "assets/minecraft/";
			if (!path.startsWith(prefix)) {
				return null;
			}
			IoSupplier<InputStream> s = Minecraft.getInstance().getVanillaPackResources().fullResources()
					.getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace(path.substring(prefix.length())));
			if (s == null) {
				return null;
			}
			try (InputStream in = s.get()) {
				return in.readAllBytes();
			}
		});
	}

	/** Is this pack image still being prepared in the background? */
	public boolean isLoading(PackInfo pack, String path) {
		return pending.containsKey(pack.id() + "|" + path);
	}

	private interface Reader {
		byte[] read() throws Exception;
	}

	private Preview load(String key, Reader reader) {
		Preview cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		if (failed.contains(key)) {
			return null;
		}
		CompletableFuture<NativeImage> job = pending.get(key);
		if (job == null) {
			pending.put(key, CompletableFuture.supplyAsync(() -> decode(reader), DECODER));
			return null;
		}
		if (!job.isDone() || !uploadAllowed()) {
			return null;
		}
		pending.remove(key);
		NativeImage image = job.getNow(null);
		if (image == null) {
			failed.add(key);
			return null;
		}
		try {
			Identifier id = Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "preview/p" + (counter++));
			Minecraft.getInstance().getTextureManager()
					.register(id, new DynamicTexture(() -> "texturestudio preview", image));
			Preview preview = new Preview(id, image.getWidth(), image.getHeight());
			cache.put(key, preview);
			return preview;
		} catch (RuntimeException e) {
			image.close();
			failed.add(key);
			return null;
		}
	}

	/** Spreads GPU uploads over several frames so opening a collection never stalls. */
	private boolean uploadAllowed() {
		long now = System.nanoTime() / 8_000_000L; // ~8 ms buckets, roughly one frame
		if (now != frame) {
			frame = now;
			uploadsThisFrame = 0;
		}
		if (uploadsThisFrame >= UPLOADS_PER_FRAME) {
			return false;
		}
		uploadsThisFrame++;
		return true;
	}

	/** Background thread: read, decode and (for big images) scale down. Null on failure. */
	private NativeImage decode(Reader reader) {
		if (closed) {
			return null;
		}
		try {
			byte[] data = reader.read();
			if (data == null) {
				return null;
			}
			NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
			int w = image.getWidth();
			int h = image.getHeight();
			int big = Math.max(w, h);
			// animation strips (tall, narrow) keep their size; only really big images are shrunk
			if (big <= MAX_SIZE || w <= 128) {
				return image;
			}
			float f = big / (float) MAX_SIZE;
			int nw = Math.max(1, Math.round(w / f));
			int nh = Math.max(1, Math.round(h / f));
			NativeImage small = new NativeImage(nw, nh, false);
			for (int y = 0; y < nh; y++) {
				int y0 = (int) (y * f);
				int y1 = Math.max(y0 + 1, Math.min(h, (int) ((y + 1) * f)));
				for (int x = 0; x < nw; x++) {
					int x0 = (int) (x * f);
					int x1 = Math.max(x0 + 1, Math.min(w, (int) ((x + 1) * f)));
					small.setPixel(x, y, average(image, x0, y0, x1, y1));
				}
			}
			image.close();
			return small;
		} catch (Exception e) {
			return null;
		}
	}

	/** Average of a pixel block, channel by channel (works for any channel order). */
	private static int average(NativeImage img, int x0, int y0, int x1, int y1) {
		long c0 = 0;
		long c1 = 0;
		long c2 = 0;
		long c3 = 0;
		int n = 0;
		int step = Math.max(1, (x1 - x0) / 4); // sample at most ~4x4 per block
		int stepY = Math.max(1, (y1 - y0) / 4);
		for (int y = y0; y < y1; y += stepY) {
			for (int x = x0; x < x1; x += step) {
				int p = img.getPixel(x, y);
				c0 += (p >>> 24) & 255;
				c1 += (p >>> 16) & 255;
				c2 += (p >>> 8) & 255;
				c3 += p & 255;
				n++;
			}
		}
		return (int) ((c0 / n) << 24 | (c1 / n) << 16 | (c2 / n) << 8 | (c3 / n));
	}

	public void close() {
		closed = true;
		TextureManager tm = Minecraft.getInstance().getTextureManager();
		for (Preview p : cache.values()) {
			tm.release(p.id());
		}
		cache.clear();
		for (CompletableFuture<NativeImage> job : pending.values()) {
			job.thenAccept(img -> {
				if (img != null) {
					img.close();
				}
			});
		}
		pending.clear();
		failed.clear();
	}
}
