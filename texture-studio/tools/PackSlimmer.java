import com.texturestudio.Group;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Macht aus vollen Texture Packs schlanke "Stil-Packs" fuer Pin Visuals:
 * behaelt nur Texturen, die einer Haupt-Sammlung (Crystal, Totem, Tools ...) zugeordnet sind,
 * dazu deren .mcmeta, die pack.mcmeta und ein kleines pack.png. Zu grosse Texturen werden verkleinert.
 *
 * Aufruf (nach einem Build, damit Group.class existiert):
 *   java -cp build/classes/java/main tools/PackSlimmer.java <quellordner> <zielordner> [maxPixel]
 */
public class PackSlimmer {

	public static void main(String[] args) throws Exception {
		Path src = Path.of(args[0]);
		Path dst = Path.of(args[1]);
		int max = args.length > 2 ? Integer.parseInt(args[2]) : 128;
		Files.createDirectories(dst);
		long inTotal = 0;
		long outTotal = 0;
		List<Path> zips;
		try (var s = Files.list(src)) {
			zips = s.filter(p -> p.toString().toLowerCase().endsWith(".zip")).sorted().toList();
		}
		for (Path zip : zips) {
			Path out = dst.resolve(zip.getFileName().toString());
			int[] counts = slim(zip, out, max);
			long a = Files.size(zip);
			long b = Files.exists(out) ? Files.size(out) : 0;
			inTotal += a;
			outTotal += b;
			System.out.printf("%8d KB -> %6d KB  %4d Texturen  %s%n", a / 1024, b / 1024, counts[0],
					zip.getFileName());
		}
		System.out.printf("GESAMT: %.1f MB -> %.2f MB%n", inTotal / 1048576.0, outTotal / 1048576.0);
	}

	private static boolean isTexture(String name) {
		if (!name.startsWith("assets/") || !name.endsWith(".png")) {
			return false;
		}
		String[] parts = name.split("/");
		return parts.length >= 4 && parts[2].equals("textures");
	}

	/** Gibt {Anzahl Texturen} zurueck; Packs ohne passende Texturen werden nicht geschrieben. */
	private static int[] slim(Path zip, Path out, int max) throws IOException {
		int n = 0;
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		try (ZipFile zf = new ZipFile(zip.toFile()); ZipOutputStream zos = new ZipOutputStream(buf)) {
			zos.setLevel(Deflater.BEST_COMPRESSION);
			List<? extends ZipEntry> entries = zf.stream().filter(e -> !e.isDirectory()).toList();
			for (ZipEntry e : entries) {
				String name = e.getName();
				if (isTexture(name) && !Group.classify(name).startsWith("x_")) {
					byte[] data = read(zf, e);
					// entity model textures (crystal, shield, trident...) often use 1px outlines that vanish when
					// shrunk, so they are kept in full size (they are small files anyway)
					put(zos, name, name.contains("/textures/entity/") ? data : shrink(data, max));
					n++;
					ZipEntry meta = zf.getEntry(name + ".mcmeta");
					if (meta != null) {
						put(zos, name + ".mcmeta", read(zf, meta));
					}
				}
			}
			if (n == 0) {
				return new int[] { 0 };
			}
			ZipEntry mc = zf.getEntry("pack.mcmeta");
			put(zos, "pack.mcmeta", mc != null ? read(zf, mc)
					: "{\"pack\":{\"description\":\"Pin Visuals\",\"pack_format\":75}}".getBytes());
			ZipEntry png = zf.getEntry("pack.png");
			if (png != null) {
				byte[] icon = icon(read(zf, png));
				put(zos, "pack.png", icon);
			}
		}
		Files.write(out, buf.toByteArray());
		return new int[] { n };
	}

	private static byte[] read(ZipFile zf, ZipEntry e) throws IOException {
		try (InputStream in = zf.getInputStream(e)) {
			return in.readAllBytes();
		}
	}

	private static void put(ZipOutputStream zos, String name, byte[] data) throws IOException {
		zos.putNextEntry(new ZipEntry(name));
		zos.write(data);
		zos.closeEntry();
	}

	/** Pack-Icon weich auf 32x32 verkleinern. */
	private static byte[] icon(byte[] data) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null) {
				return data;
			}
			BufferedImage small = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			var g = small.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(img, 0, 0, 32, 32, null);
			g.dispose();
			ByteArrayOutputStream o = new ByteArrayOutputStream();
			ImageIO.write(small, "png", o);
			return o.toByteArray().length < data.length ? o.toByteArray() : data;
		} catch (Exception ex) {
			return data;
		}
	}

	/** Verkleinert Bilder, deren Breite max uebersteigt (Hoehe proportional, damit Animations-Streifen passen). */
	private static byte[] shrink(byte[] data, int max) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null || img.getWidth() <= max) {
				return data;
			}
			// Breite auf max, Hoehe im gleichen Verhaeltnis (Animations-Streifen bleiben ganze Bilder)
			int w = max;
			int h = Math.max(1, Math.round(img.getHeight() * (max / (float) img.getWidth())));
			BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			var g = small.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(img, 0, 0, w, h, null);
			g.dispose();
			ByteArrayOutputStream o = new ByteArrayOutputStream();
			ImageIO.write(small, "png", o);
			byte[] res = o.toByteArray();
			return res.length < data.length ? res : data;
		} catch (Exception ex) {
			return data;
		}
	}
}
