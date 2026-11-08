package com.texturestudio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Die mitgelieferte Pack-Sammlung: ZIP-Dateien unter /packlib in der Mod-JAR.
 * Beim Installieren werden sie in den resourcepacks-Ordner des Spielers kopiert (im Hintergrund).
 */
public final class PackLibrary {

	public record Entry(String file, long size) {
		/** Name ohne Minecraft-Farbcodes und ohne .zip */
		public String displayName() {
			String n = file.replaceAll("§.", "");
			if (n.toLowerCase().endsWith(".zip")) {
				n = n.substring(0, n.length() - 4);
			}
			n = n.replaceFirst("^[!\\s]+", "").trim();
			return n.isEmpty() ? file : n;
		}
	}

	private static List<Entry> cache;

	private PackLibrary() {
	}

	private static Path root() {
		return FabricLoader.getInstance().getModContainer(TextureStudioClient.MOD_ID)
				.flatMap(c -> c.findPath("packlib")).orElse(null);
	}

	public static synchronized List<Entry> entries() {
		if (cache != null) {
			return cache;
		}
		List<Entry> out = new ArrayList<>();
		Path r = root();
		if (r != null && Files.isDirectory(r)) {
			try (Stream<Path> s = Files.list(r)) {
				s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip")).forEach(p -> {
					try {
						out.add(new Entry(p.getFileName().toString(), Files.size(p)));
					} catch (IOException ignored) {
						// unlesbar -> ueberspringen
					}
				});
			} catch (IOException e) {
				TextureStudioClient.LOGGER.warn("Pack-Sammlung nicht lesbar", e);
			}
		}
		out.sort(Comparator.comparing(e -> e.displayName().toLowerCase()));
		cache = List.copyOf(out);
		return cache;
	}

	public static long totalSize(List<Entry> list) {
		long n = 0;
		for (Entry e : list) {
			n += e.size();
		}
		return n;
	}

	public static String mb(long bytes) {
		double mb = bytes / (1024.0 * 1024.0);
		return mb < 10 ? String.format("%.1f MB", mb) : Math.round(mb) + " MB";
	}

	/**
	 * Liegt das Pack schon im Zielordner? Eine gleichnamige Datei zaehlt immer als vorhanden,
	 * damit nie ein volles Original-Pack des Spielers durch die schlanke Stil-Version ersetzt wird.
	 */
	public static boolean installed(Entry e, Path dir) {
		try {
			return Files.exists(dir.resolve(e.file()));
		} catch (RuntimeException ex) {
			return false;
		}
	}

	// ------------------------------------------------------------------ Installation im Hintergrund

	public static final class Job {
		public final int total;
		public final long bytesTotal;
		public volatile int done;
		public volatile int copied;
		public volatile long bytesDone;
		public volatile String current = "";
		public volatile String error;
		public volatile boolean finished;

		Job(int total, long bytesTotal) {
			this.total = total;
			this.bytesTotal = bytesTotal;
		}

		public float progress() {
			return bytesTotal <= 0 ? (finished ? 1f : 0f) : Math.min(1f, bytesDone / (float) bytesTotal);
		}
	}

	public static Job install(List<Entry> list, Path dir) {
		Job job = new Job(list.size(), totalSize(list));
		Thread t = new Thread(() -> {
			Path r = root();
			try {
				Files.createDirectories(dir);
				for (Entry e : list) {
					job.current = e.displayName();
					if (!installed(e, dir)) {
						Path tmp = dir.resolve(e.file() + ".part");
						Files.copy(r.resolve(e.file()), tmp, StandardCopyOption.REPLACE_EXISTING);
						Files.move(tmp, dir.resolve(e.file()), StandardCopyOption.REPLACE_EXISTING);
						job.copied++;
					}
					job.bytesDone += e.size();
					job.done++;
				}
			} catch (Exception ex) {
				job.error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
				TextureStudioClient.LOGGER.error("Pack-Installation fehlgeschlagen", ex);
			} finally {
				job.finished = true;
			}
		}, "PinVisuals-PackInstall");
		t.setDaemon(true);
		t.start();
		return job;
	}
}
