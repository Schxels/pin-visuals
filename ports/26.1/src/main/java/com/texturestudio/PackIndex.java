package com.texturestudio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Liest alle Packs aus dem resourcepacks-Ordner und ordnet deren Texturen den Sammlungen zu. */
public final class PackIndex {

	public final List<PackInfo> packs = new ArrayList<>();
	/** Sammlungs-ID -> (Pack -> dessen Texturen in dieser Sammlung) */
	public final Map<String, Map<PackInfo, List<String>>> byGroup = new HashMap<>();
	public int totalTextures;

	public static PackIndex scan(Path packDir) {
		PackIndex index = new PackIndex();
		if (!Files.isDirectory(packDir)) {
			return index;
		}
		List<Path> candidates;
		try (Stream<Path> s = Files.list(packDir)) {
			candidates = s.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).toList();
		} catch (IOException e) {
			return index;
		}
		for (Path p : candidates) {
			String name = p.getFileName().toString();
			if (name.equals(MixBuilder.MIX_DIR_NAME) || name.equals(MixBuilder.LEGACY_DIR_NAME)) {
				continue; // das eigene Mix-Pack nie als Quelle verwenden
			}
			boolean zip = Files.isRegularFile(p) && name.toLowerCase().endsWith(".zip");
			if (!zip && !Files.isDirectory(p)) {
				continue;
			}
			PackInfo info = new PackInfo(name, p, zip);
			List<String> found;
			try {
				found = listTextures(info);
			} catch (IOException | RuntimeException e) {
				continue; // kaputtes Pack ueberspringen
			}
			if (found.isEmpty()) {
				continue;
			}
			Collections.sort(found);
			if (!goodCrystal(info, found)) {
				index.hiddenCrystal.add(info);
			}
			index.packs.add(info);
			index.totalTextures += found.size();
			for (String tex : found) {
				index.byGroup.computeIfAbsent(Group.classify(tex), k -> new LinkedHashMap<>())
						.computeIfAbsent(info, k -> new ArrayList<>()).add(tex);
			}
		}
		return index;
	}

	/** Packs whose crystal does not look like a crystal (see goodCrystal); they are left out of the Crystal list. */
	public final java.util.Set<PackInfo> hiddenCrystal = new java.util.HashSet<>();

	/** Alle Packs, die in dieser Sammlung etwas liefern. */
	public List<PackInfo> styles(String groupId) {
		Map<PackInfo, List<String>> m = byGroup.get(groupId);
		if (m == null) {
			return List.of();
		}
		List<PackInfo> l = new ArrayList<>(m.keySet());
		if (groupId.equals("crystal")) {
			l.removeAll(hiddenCrystal);
		}
		return l;
	}

	private static final String CRYSTAL_TEX = "assets/minecraft/textures/entity/end_crystal/end_crystal.png";

	/** Opaque pixels of the vanilla end crystal texture, one long per row of the 64x32 layout. */
	private static final long[] VANILLA_CRYSTAL = {
			0x00FFFF0000FFFF00L, 0x00FFFF0000818100L, 0x00FFFF0000818100L, 0x00FFFF0000818100L,
			0x00FFFF0000818100L, 0x00FFFF0000818100L, 0x00FFFF0000818100L, 0x00FFFF0000FFFF00L,
			0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFF81818181L, 0xFFFFFFFF81818181L, 0xFFFFFFFF81818181L,
			0xFFFFFFFF81818181L, 0xFFFFFFFF81818181L, 0xFFFFFFFF81818181L, 0xFFFFFFFFFFFFFFFFL,
			0x0000000FFFFFF000L, 0x0000000FFFFFF000L, 0x0000000FFFFFF000L, 0x0000000FFFFFF000L,
			0x0000000FFFFFF000L, 0x0000000FFFFFF000L, 0x0000000FFFFFF000L, 0x0000000FFFFFF000L,
			0x0000000FFFFFF000L, 0x0000000FFFFFF000L, 0x0000000FFFFFF000L, 0x0000000FFFFFF000L,
			0x0001FFFFFFFFFFFFL, 0x0001FFFFFFFFFFFFL, 0x0001FFFFFFFFFFFFL, 0x0001FFFFFFFFFFFFL };

	/**
	 * Does this pack's crystal really look like a crystal? It must change the crystal texture itself (packs that only
	 * swap the item in hand would be shown with some other texture), at least 15% of the normal crystal shape must be
	 * visible (no invisible or outline-only crystals) and the layout must match (no textures made for other models).
	 * Packs without any crystal texture are not affected.
	 */
	private static boolean goodCrystal(PackInfo info, List<String> found) {
		boolean any = false;
		for (String t : found) {
			if (Group.classify(t).equals("crystal")) {
				any = true;
				break;
			}
		}
		if (!any) {
			return true;
		}
		if (!found.contains(CRYSTAL_TEX)) {
			return false;
		}
		try {
			byte[] png = read(info, CRYSTAL_TEX);
			if (png == null) {
				return false;
			}
			try (com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(png)) {
				int w = img.getWidth();
				int h = img.getHeight();
				int inter = 0;
				int mine = 0;
				int van = 0;
				for (int y = 0; y < 32; y++) {
					for (int x = 0; x < 64; x++) {
						boolean v = (VANILLA_CRYSTAL[y] >>> x & 1L) != 0;
						boolean m = ((img.getPixel(x * w / 64, y * h / 32) >>> 24) & 255) > 40;
						if (v) {
							van++;
						}
						if (m) {
							mine++;
							if (v) {
								inter++;
							}
						}
					}
				}
				float cover = inter / (float) van;
				float extra = (mine - inter) / (float) Math.max(1, mine);
				return cover >= 0.15f && extra <= 0.3f;
			}
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	public List<String> textures(String groupId, PackInfo pack) {
		Map<PackInfo, List<String>> m = byGroup.get(groupId);
		if (m == null || pack == null) {
			return List.of();
		}
		List<String> l = m.get(pack);
		return l == null ? List.of() : l;
	}

	private static boolean isTexture(String name) {
		if (Group.isSkyData(name)) {
			return true; // custom sky: the whole sky data counts as the pack's "Sky" style
		}
		if (!name.startsWith("assets/") || !name.endsWith(".png")) {
			return false;
		}
		String[] parts = name.split("/");
		return parts.length >= 4 && parts[2].equals("textures");
	}

	private static List<String> listTextures(PackInfo pack) throws IOException {
		List<String> result = new ArrayList<>();
		if (pack.zip()) {
			try (ZipFile zf = new ZipFile(pack.path().toFile())) {
				zf.stream().filter(e -> !e.isDirectory()).map(ZipEntry::getName).filter(PackIndex::isTexture)
						.forEach(result::add);
			}
		} else {
			Path assets = pack.path().resolve("assets");
			if (Files.isDirectory(assets)) {
				try (Stream<Path> walk = Files.walk(assets)) {
					walk.filter(Files::isRegularFile).forEach(f -> {
						String rel = pack.path().relativize(f).toString().replace('\\', '/');
						if (isTexture(rel)) {
							result.add(rel);
						}
					});
				}
			}
		}
		return result;
	}

	/** Liest eine Datei aus einem Pack. Gibt null zurueck, wenn sie nicht existiert. */
	public static byte[] read(PackInfo pack, String entry) throws IOException {
		if (pack.zip()) {
			try (ZipFile zf = new ZipFile(pack.path().toFile())) {
				ZipEntry e = zf.getEntry(entry);
				if (e == null) {
					return null;
				}
				try (InputStream in = zf.getInputStream(e)) {
					return in.readAllBytes();
				}
			}
		}
		Path f = pack.path().resolve(entry);
		return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
	}

	public PackInfo byId(String id) {
		for (PackInfo p : packs) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}
}
