package com.texturestudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.texturestudio.PinSettings.Feature;
import com.texturestudio.PinSettings.S;
import com.texturestudio.PinSettings.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Presets: a snapshot of every feature (on / off) and every setting of All Settings, saved under a name in
 * config/pinvisuals/presets and loaded again with one click. Toggle keys stay as they are (they are personal). Three
 * ready-made presets come with the mod.
 */
final class Presets {

	static final String[] BUILT_IN = { "Clean (all off)", "Crystal PvP", "Aesthetic" };
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Presets() {
	}

	static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("pinvisuals").resolve("presets");
	}

	/** Names of the saved presets (without .json), sorted. */
	static List<String> saved() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir())) {
			return out;
		}
		try (Stream<Path> s = Files.list(dir())) {
			s.filter(p -> p.getFileName().toString().endsWith(".json"))
					.map(p -> p.getFileName().toString().replaceAll("\\.json$", "")).sorted(String.CASE_INSENSITIVE_ORDER)
					.forEach(out::add);
		} catch (IOException ignored) {
			// empty list
		}
		return out;
	}

	static String fileName(String name) {
		String n = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		return n.isEmpty() ? "Preset" : n;
	}

	/** Everything that a preset holds, as JSON. */
	static JsonObject snapshot() {
		JsonObject root = new JsonObject();
		JsonObject on = new JsonObject();
		JsonObject vals = new JsonObject();
		for (Feature f : PinSettings.FEATURES) {
			if (f.hasToggle()) {
				on.addProperty(f.id, f.isOn());
			}
			for (S s : f.settings) {
				if (s.type == Type.KEY) {
					continue;
				}
				if (s.type == Type.TEXT) {
					vals.addProperty(s.id, s.text());
				} else {
					vals.addProperty(s.id, s.get());
				}
			}
		}
		root.add("features", on);
		root.add("settings", vals);
		return root;
	}

	static void apply(JsonObject root) {
		JsonObject vals = root.has("settings") ? root.getAsJsonObject("settings") : new JsonObject();
		for (Feature f : PinSettings.FEATURES) {
			for (S s : f.settings) {
				JsonElement v = vals.get(s.id);
				if (v == null || s.type == Type.KEY) {
					continue;
				}
				if (s.type == Type.TEXT) {
					s.setText(v.getAsString());
				} else {
					s.set(v.getAsDouble());
				}
			}
		}
		// switches last: some settings (styles) wear something only while the feature is on
		JsonObject on = root.has("features") ? root.getAsJsonObject("features") : new JsonObject();
		for (Feature f : PinSettings.FEATURES) {
			if (f.hasToggle() && on.has(f.id)) {
				boolean want = on.get(f.id).getAsBoolean();
				if (f.isOn() != want) {
					f.setOn.accept(want);
				}
			}
		}
		Features.flush();
	}

	static void save(String name) throws IOException {
		Files.createDirectories(dir());
		Files.writeString(dir().resolve(fileName(name) + ".json"), GSON.toJson(snapshot()), StandardCharsets.UTF_8);
	}

	static void load(String name) throws IOException {
		Path p = dir().resolve(fileName(name) + ".json");
		apply(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
	}

	static void delete(String name) throws IOException {
		Files.deleteIfExists(dir().resolve(fileName(name) + ".json"));
	}

	/** One of the presets that come with the mod. */
	static void loadBuiltIn(int i) {
		JsonObject root = new JsonObject();
		JsonObject on = new JsonObject();
		JsonObject vals = new JsonObject();
		// start from the defaults with everything off
		for (Feature f : PinSettings.FEATURES) {
			if (f.hasToggle()) {
				on.addProperty(f.id, false);
			}
			for (S s : f.settings) {
				if (s.type != Type.KEY && s.type != Type.TEXT) {
					vals.addProperty(s.id, s.def);
				}
			}
		}
		switch (i) {
			case 1 -> { // Crystal PvP: clear view, readable enemies, more FPS
				for (String f : new String[] { "outline", "pop_chams", "hit_color", "block_outline", "low_fire",
						"low_shield", "no_totem_pop", "no_totem_particles", "no_explosion_particles",
						"fewer_hit_particles", "calm_crystals", "hide_far_items", "hurt_cam",
						"armor_hud" }) {
					on.addProperty(f, true);
				}
				vals.addProperty("outline.mode", 2);
				vals.addProperty("hurt_cam.strength", 20);
				vals.addProperty("block_outline.glow", 60);
			}
			case 2 -> { // Aesthetic: mood, trail and a soft blur
				for (String f : new String[] { "atmosphere", "trail", "motion_blur", "outline", "pop_chams",
						"lyrics", "spotify" }) {
					on.addProperty(f, true);
				}
				vals.addProperty("trail.rainbow", 1);
				vals.addProperty("trail.type", 0);
				vals.addProperty("motion_blur.strength", 30);
				vals.addProperty("lyrics.mode", 1);
				vals.addProperty("outline.color_mode", 1);
			}
			default -> {
				// all off
			}
		}
		root.add("features", on);
		root.add("settings", vals);
		apply(root);
	}

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
