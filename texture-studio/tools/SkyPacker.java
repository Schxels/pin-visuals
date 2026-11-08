import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Packs the sky packs for the built-in library small enough for the jar:
 * keeps only sky data (OptiFine/MCPatcher sky + legacy FabricSkyBoxes + their images), drops MCPatcher copies
 * when an OptiFine copy exists, points FabricSkyBoxes face textures at the shared OptiFine sheet,
 * scales big sheets down and stores them as 256-color PNGs.
 *
 * Usage: java tools/SkyPacker.java <extractedPacksDir> <outDir> [sheetWidth]
 *   each sub folder of extractedPacksDir is one pack; the zip gets the folder name.
 */
public class SkyPacker {

	static boolean DITHER = true;

	public static void main(String[] args) throws Exception {
		DITHER = args.length <= 3 || !args[3].equals("nodither");
		Path src = Path.of(args[0]);
		Path dst = Path.of(args[1]);
		int width = args.length > 2 ? Integer.parseInt(args[2]) : 960;
		Files.createDirectories(dst);
		List<Path> dirs;
		try (var s = Files.list(src)) {
			dirs = s.filter(Files::isDirectory).sorted().toList();
		}
		long total = 0;
		for (Path d : dirs) {
			Path out = dst.resolve(d.getFileName() + ".zip");
			pack(d, out, width);
			long sz = Files.size(out);
			total += sz;
			System.out.printf("%6d KB  %s%n", sz / 1024, out.getFileName());
		}
		System.out.printf("TOTAL %.2f MB%n", total / 1048576.0);
	}

	private static void pack(Path dir, Path out, int width) throws IOException {
		List<Path> files;
		try (var s = Files.walk(dir)) {
			files = s.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
		}
		boolean hasOptifine = files.stream().anyMatch(f -> rel(dir, f).contains("/optifine/sky/"));
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(buf)) {
			zos.setLevel(Deflater.BEST_COMPRESSION);
			for (Path f : files) {
				String name = rel(dir, f);
				String low = name.toLowerCase();
				if (low.endsWith(".ds_store") || low.endsWith("thumbs.db")) {
					continue;
				}
				if (hasOptifine && name.contains("/mcpatcher/sky/")) {
					continue; // same data as optifine/sky
				}
				byte[] data = Files.readAllBytes(f);
				if (name.equals("pack.png")) {
					put(zos, name, icon(data));
				} else if (name.equals("pack.mcmeta")) {
					put(zos, name, data);
				} else if (name.startsWith("assets/fabricskyboxes/") && name.endsWith(".png") && hasOptifine) {
					continue; // FabricSkyBoxes faces are replaced by the shared OptiFine sheet (see json rewrite)
				} else if (name.startsWith("assets/fabricskyboxes/") && name.endsWith(".json")) {
					put(zos, name, rewriteFaces(new String(data, StandardCharsets.UTF_8), hasOptifine)
							.getBytes(StandardCharsets.UTF_8));
				} else if (name.startsWith("assets/") && name.endsWith(".png")) {
					put(zos, name, data.length > 48 * 1024 ? sheet(data, width) : data);
				} else if (name.startsWith("assets/")) {
					put(zos, name, data);
				}
			}
		}
		Files.write(out, buf.toByteArray());
	}

	private static final Pattern FACE = Pattern.compile(
			"\"textures\"\\s*:\\s*\\{[^}]*?\"[a-z]+\"\\s*:\\s*\"fabricskyboxes:sky/([A-Za-z0-9_]+?)_(?:top|bottom|north|south|east|west)\\.png\"[^}]*\\}",
			Pattern.DOTALL);

	/** "square-textured" with six face files -> "single-sprite-square-textured" on the OptiFine sheet. */
	private static String rewriteFaces(String json, boolean hasOptifine) {
		if (!hasOptifine) {
			return json;
		}
		Matcher m = FACE.matcher(json);
		if (!m.find()) {
			return json;
		}
		String base = m.group(1);
		String out = json.substring(0, m.start()) + "\"texture\": \"minecraft:optifine/sky/world0/" + base + ".png\""
				+ json.substring(m.end());
		return out.replaceFirst("\"type\"\\s*:\\s*\"square-textured\"", "\"type\": \"single-sprite-square-textured\"");
	}

	private static String rel(Path dir, Path f) {
		return dir.relativize(f).toString().replace('\\', '/');
	}

	private static void put(ZipOutputStream zos, String name, byte[] data) throws IOException {
		zos.putNextEntry(new ZipEntry(name));
		zos.write(data);
		zos.closeEntry();
	}

	private static byte[] icon(byte[] data) throws IOException {
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
		if (img == null) {
			return data;
		}
		return png(scale(img, 32, 32));
	}

	/** Big sky sheet: scale to the given width (keeping 3:2) and store as a 256-color PNG. */
	private static byte[] sheet(byte[] data, int width) throws IOException {
		BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
		if (img == null) {
			return data;
		}
		int w = Math.min(width, img.getWidth());
		int h = Math.max(1, Math.round(img.getHeight() * (w / (float) img.getWidth())));
		BufferedImage small = scale(img, w, h);
		byte[] indexed = png(quantize(small));
		return indexed.length < data.length ? indexed : data;
	}

	/** Smooth downscale in several halving steps (keeps cloud detail without aliasing). */
	private static BufferedImage scale(BufferedImage img, int w, int h) {
		BufferedImage cur = toArgb(img);
		while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
			cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2);
		}
		return draw(cur, w, h);
	}

	private static BufferedImage draw(BufferedImage img, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		var g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static BufferedImage toArgb(BufferedImage img) {
		if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
			return img;
		}
		return draw(img, img.getWidth(), img.getHeight());
	}

	private static byte[] png(BufferedImage img) throws IOException {
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		ImageIO.write(img, "png", o);
		return o.toByteArray();
	}

	// ------------------------------------------------------------------ 256-color quantizer (median cut, RGBA)

	private static BufferedImage quantize(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int[] px = img.getRGB(0, 0, w, h, null, 0, w);
		int[] palette = medianCut(px, 256);
		int n = palette.length;
		byte[] r = new byte[n];
		byte[] g = new byte[n];
		byte[] b = new byte[n];
		byte[] a = new byte[n];
		for (int i = 0; i < n; i++) {
			a[i] = (byte) (palette[i] >>> 24);
			r[i] = (byte) (palette[i] >> 16);
			g[i] = (byte) (palette[i] >> 8);
			b[i] = (byte) palette[i];
		}
		IndexColorModel cm = new IndexColorModel(8, n, r, g, b, a);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, cm);
		var raster = out.getRaster();
		// light ordered dither: hides banding in smooth gradients but compresses far better than error diffusion
		int[][] bayer = { { 0, 8, 2, 10 }, { 12, 4, 14, 6 }, { 3, 11, 1, 9 }, { 15, 7, 13, 5 } };
		java.util.HashMap<Integer, Integer> cache = new java.util.HashMap<>();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = px[y * w + x];
				int d = DITHER ? (bayer[y & 3][x & 3] - 8) / 3 : 0; // -2..2
				int ca = (c >>> 24);
				int cr = clamp(((c >> 16) & 255) + d);
				int cg = clamp(((c >> 8) & 255) + d);
				int cb = clamp((c & 255) + d);
				int key = (ca << 24) | (cr << 16) | (cg << 8) | cb;
				Integer idx = cache.get(key);
				if (idx == null) {
					idx = nearest(palette, ca, cr, cg, cb);
					cache.put(key, idx);
				}
				raster.setSample(x, y, 0, idx);
			}
		}
		return out;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static int nearest(int[] pal, int a, int r, int g, int b) {
		int best = 0;
		long bd = Long.MAX_VALUE;
		for (int i = 0; i < pal.length; i++) {
			int p = pal[i];
			long da = ((p >>> 24) - a);
			long dr = (((p >> 16) & 255) - r);
			long dg = (((p >> 8) & 255) - g);
			long db = ((p & 255) - b);
			long d = 2 * da * da + 3 * dr * dr + 4 * dg * dg + 2 * db * db;
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	private static int[] medianCut(int[] px, int colors) {
		List<int[]> boxes = new ArrayList<>();
		boxes.add(px.clone());
		while (boxes.size() < colors) {
			// split the box with the widest channel range (weighted by size)
			int bi = -1;
			long bestScore = -1;
			int bestCh = 0;
			for (int i = 0; i < boxes.size(); i++) {
				int[] box = boxes.get(i);
				if (box.length < 2) {
					continue;
				}
				for (int ch = 0; ch < 4; ch++) {
					int lo = 255;
					int hi = 0;
					for (int c : box) {
						int v = (c >>> (ch * 8)) & 255;
						lo = Math.min(lo, v);
						hi = Math.max(hi, v);
					}
					long score = (long) (hi - lo) * (long) Math.sqrt(box.length);
					if (score > bestScore) {
						bestScore = score;
						bi = i;
						bestCh = ch;
					}
				}
			}
			if (bi < 0 || bestScore <= 0) {
				break;
			}
			int[] box = boxes.remove(bi);
			final int sh = bestCh * 8;
			Integer[] sorted = Arrays.stream(box).boxed().toArray(Integer[]::new);
			Arrays.sort(sorted, Comparator.comparingInt(c -> (c >>> sh) & 255));
			int mid = sorted.length / 2;
			int[] lo = new int[mid];
			int[] hi = new int[sorted.length - mid];
			for (int i = 0; i < sorted.length; i++) {
				if (i < mid) {
					lo[i] = sorted[i];
				} else {
					hi[i - mid] = sorted[i];
				}
			}
			boxes.add(lo);
			boxes.add(hi);
		}
		int[] pal = new int[boxes.size()];
		for (int i = 0; i < boxes.size(); i++) {
			long sa = 0;
			long sr = 0;
			long sg = 0;
			long sb = 0;
			int[] box = boxes.get(i);
			for (int c : box) {
				sa += c >>> 24;
				sr += (c >> 16) & 255;
				sg += (c >> 8) & 255;
				sb += c & 255;
			}
			int n = Math.max(1, box.length);
			pal[i] = (int) ((sa / n) << 24 | (sr / n) << 16 | (sg / n) << 8 | (sb / n));
		}
		return pal;
	}
}
