package com.texturestudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Baut aus der Auswahl ein Ordner-Pack ("PinVisuals-Mix") im resourcepacks-Ordner,
 * legt es ganz nach oben in die Pack-Liste und laedt die Ressourcen neu.
 *
 * Ein Ordner statt einer ZIP, damit sich der Inhalt auch unter Windows neu schreiben laesst,
 * waehrend das Pack aktiv ist.
 */
public final class MixBuilder {

	public static final String MIX_DIR_NAME = "PinVisuals-Mix";
	/** Name aus der Zeit, als die Mod noch "Texture Studio" hiess; wird beim Anwenden aufgeraeumt. */
	public static final String LEGACY_DIR_NAME = "TextureStudio-Mix";
	private static final String MIX_PACK_ID = "file/" + MIX_DIR_NAME;
	private static final String LEGACY_PACK_ID = "file/" + LEGACY_DIR_NAME;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private MixBuilder() {
	}

	/** Schreibt das Mix-Pack und aktiviert es. Gibt die Anzahl der enthaltenen Texturen zurueck. */
	public static int apply(Minecraft mc, PackIndex index, MixConfig config) throws IOException {
		Path out = mc.getResourcePackDirectory().resolve(MIX_DIR_NAME);
		try {
			deleteRecursively(mc.getResourcePackDirectory().resolve(LEGACY_DIR_NAME)); // altes, selbst erzeugtes Mix-Pack
		} catch (IOException | RuntimeException e) {
			TextureStudioClient.LOGGER.warn("Altes Mix-Pack konnte nicht geloescht werden: {}", e.toString());
		}
		int count = build(out, index, config);
		activate(mc);
		return count;
	}

	private static int build(Path out, PackIndex index, MixConfig config) throws IOException {
		deleteRecursively(out);
		Files.createDirectories(out);

		int count = 0;
		String mcmeta = null;

		for (Map.Entry<String, String> sel : config.styles.entrySet()) {
			PackInfo pack = index.byId(sel.getValue());
			if (pack == null) {
				continue; // Pack wurde entfernt
			}
			List<String> texs = index.textures(sel.getKey(), pack);
			for (String path : texs) {
				byte[] png = PackIndex.read(pack, path);
				if (png == null) {
					continue;
				}
				write(out, path, png);
				count++;

				// Animationsdaten (z. B. fire_0.png.mcmeta) mitnehmen
				byte[] anim = PackIndex.read(pack, path + ".mcmeta");
				if (anim != null) {
					write(out, path + ".mcmeta", anim);
				}

				// Alte Pfade zusaetzlich unter dem heutigen Pfad ablegen (falls das Pack ihn nicht selbst hat)
				String modern = LegacyPaths.modern(path);
				if (modern != null && !texs.contains(modern)) {
					write(out, modern, png);
				}
				if (path.equals(LegacyPaths.MOON_PHASES)) {
					splitMoon(out, png, texs);
				}
			}
			if (mcmeta == null) {
				mcmeta = mcmetaFrom(pack);
			}
		}

		if (mcmeta == null && !index.packs.isEmpty()) {
			mcmeta = mcmetaFrom(index.packs.get(0));
		}
		if (mcmeta == null) {
			// Notnagel, falls kein Pack eine lesbare pack.mcmeta hat.
			// Falls das Mix-Pack als "inkompatibel" markiert wird: pack_format hier anpassen.
			mcmeta = "{\"pack\":{\"description\":\"Texture Studio Mix\",\"pack_format\":75}}";
		}
		Files.writeString(out.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
		return count;
	}

	/** Uebernimmt die pack.mcmeta eines vorhandenen Packs (damit das Format zur Spielversion passt). */
	private static String mcmetaFrom(PackInfo pack) {
		try {
			byte[] raw = PackIndex.read(pack, "pack.mcmeta");
			if (raw == null) {
				return null;
			}
			String text = new String(raw, StandardCharsets.UTF_8).replace("﻿", "");
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			JsonObject packObj = root.getAsJsonObject("pack");
			if (packObj == null) {
				return null;
			}
			packObj.addProperty("description", "Texture Studio Mix");
			root.remove("overlays");
			root.remove("filter");
			return GSON.toJson(root);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Altes 4x2-Raster moon_phases.png in die acht Einzeldateien von 26.2 zerlegen. */
	private static void splitMoon(Path out, byte[] png, List<String> have) {
		try (NativeImage sheet = NativeImage.read(png)) {
			int fw = sheet.getWidth() / 4;
			int fh = sheet.getHeight() / 2;
			if (fw <= 0 || fh <= 0) {
				return;
			}
			for (int i = 0; i < 8; i++) {
				String target = LegacyPaths.moonFile(i);
				if (have.contains(target)) {
					continue;
				}
				try (NativeImage frame = new NativeImage(fw, fh, false)) {
					int ox = (i % 4) * fw;
					int oy = (i / 4) * fh;
					for (int y = 0; y < fh; y++) {
						for (int x = 0; x < fw; x++) {
							frame.setPixel(x, y, sheet.getPixel(ox + x, oy + y));
						}
					}
					Path f = out.resolve(target);
					Files.createDirectories(f.getParent());
					frame.writeToFile(f);
				}
			}
		} catch (IOException | RuntimeException e) {
			TextureStudioClient.LOGGER.warn("moon_phases konnte nicht zerlegt werden: {}", e.toString());
		}
	}

	private static void write(Path root, String entry, byte[] data) throws IOException {
		Path target = root.resolve(entry);
		Files.createDirectories(target.getParent());
		Files.write(target, data);
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(p);
			}
		}
	}

	private static void activate(Minecraft mc) throws IOException {
		PackRepository repo = mc.getResourcePackRepository();
		repo.reload();
		if (!repo.isAvailable(MIX_PACK_ID)) {
			throw new IOException("Mix-Pack wurde vom Spiel nicht erkannt");
		}
		List<String> before = new ArrayList<>(repo.getSelectedIds());
		List<String> after = new ArrayList<>(before);
		after.remove(LEGACY_PACK_ID);
		after.remove(MIX_PACK_ID);
		after.add(MIX_PACK_ID); // ganz hinten = hoechste Prioritaet
		repo.setSelected(after);
		TextureStudioClient.LOGGER.info("Aktive Packs nach Anwenden: {}", repo.getSelectedIds());

		if (after.equals(before)) {
			// Pack war schon aktiv, nur der Inhalt hat sich geaendert -> Reload erzwingen
			mc.reloadResourcePacks();
		} else {
			mc.options.updateResourcePacks(repo); // laedt bei geaenderter Liste selbst neu
		}
	}
}
