package com.texturestudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings of the new menu: typed values with a default, a range and an optional binding to an older config field.
 * Unbound values are stored in config/pinvisuals/settings.json and read by the features every frame (volatile
 * fields, no file access while rendering). Every change applies at once; the file is written when the mouse is
 * released or the menu closes.
 */
final class PinSettings {

	enum Type {
		BOOL, NUM, COLOR, CHOICE, KEY, TEXT
	}

	/** Sidebar categories. */
	enum Cat {
		RENDER("Render"), PLAYER("Player"), TEXTURES("Texture Packs"), HUD("HUD"), MISC("Misc");

		final String label;

		Cat(String label) {
			this.label = label;
		}
	}

	/** Value kept somewhere else (the older MixConfig). */
	interface Bind {
		double get();

		void set(double v);
	}

	/** One setting. Numbers, switches, colours, choices and keys are all kept as a double. */
	static final class S {
		final String id;
		final String name;
		final Type type;
		final double def;
		final String defText;
		double min;
		double max = 1;
		double step = 1;
		String unit = "";
		String[] options = new String[0];
		boolean alpha;
		String hint = "";
		Bind bind;
		Runnable onChange;
		BooleanSupplier visible;
		private volatile double v;
		private volatile String text;

		S(String id, String name, Type type, double def, String defText) {
			this.id = id;
			this.name = name;
			this.type = type;
			this.def = def;
			this.defText = defText;
			this.v = def;
			this.text = defText;
		}

		double get() {
			return bind != null ? bind.get() : v;
		}

		boolean b() {
			return get() != 0;
		}

		int i() {
			return (int) Math.round(get());
		}

		float f() {
			return (float) get();
		}

		/** ARGB. */
		int color() {
			return (int) (long) get();
		}

		String text() {
			return text == null ? "" : text;
		}

		boolean shown() {
			return visible == null || visible.getAsBoolean();
		}

		void set(double nv) {
			if (type == Type.NUM) {
				nv = Math.max(min, Math.min(max, nv));
				if (step > 0) {
					nv = min + Math.round((nv - min) / step) * step;
					nv = Math.max(min, Math.min(max, nv));
				}
			} else if (type == Type.CHOICE) {
				nv = Math.max(0, Math.min(Math.max(0, options.length - 1), Math.round(nv)));
			} else if (type == Type.BOOL) {
				nv = nv != 0 ? 1 : 0;
			}
			if (nv == get()) {
				return;
			}
			if (bind != null) {
				bind.set(nv);
			} else {
				v = nv;
				dirty = true;
			}
			if (onChange != null) {
				onChange.run();
			}
		}

		/** Sets the stored value without notifying anyone (loading, syncing with the older config). */
		void setQuiet(double nv) {
			if (bind == null) {
				v = nv;
			}
		}

		void setColor(int argb) {
			set(argb);
		}

		void setText(String s) {
			String n = s == null ? "" : s;
			if (!n.equals(text)) {
				text = n;
				dirty = true;
				if (onChange != null) {
					onChange.run();
				}
			}
		}

		boolean isDefault() {
			return type == Type.TEXT ? text().equals(defText) : Math.abs(get() - def) < 1e-9;
		}

		void reset() {
			if (type == Type.TEXT) {
				setText(defText);
			} else {
				set(def);
			}
		}

		/** Number of decimals shown for a number. */
		int decimals() {
			return step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
		}

		String format() {
			return switch (type) {
				case NUM -> String.format(Locale.ROOT, "%." + decimals() + "f", get()) + unit;
				case COLOR -> hex(color(), alpha);
				case CHOICE -> options.length == 0 ? "" : options[Math.max(0, Math.min(options.length - 1, i()))];
				case BOOL -> b() ? "On" : "Off";
				case KEY -> keyName(i());
				case TEXT -> text();
			};
		}

		// ---- fluent helpers used when the features are declared
		S when(BooleanSupplier vis) {
			this.visible = vis;
			return this;
		}

		S bind(Bind b) {
			this.bind = b;
			return this;
		}

		S onChange(Runnable r) {
			this.onChange = r;
			return this;
		}
	}

	/** A feature card: name, description, an optional on/off state and its settings. */
	static final class Feature {
		final String id;
		final String name;
		final String desc;
		final Cat cat;
		final List<S> settings = new ArrayList<>();
		BooleanSupplier on;
		Consumer<Boolean> setOn;
		/** Features without a switch can have a button instead (e.g. "Open"). */
		Runnable action;
		String actionLabel = "";
		S key;

		Feature(Cat cat, String id, String name, String desc) {
			this.cat = cat;
			this.id = id;
			this.name = name;
			this.desc = desc;
		}

		boolean hasToggle() {
			return on != null && setOn != null;
		}

		boolean isOn() {
			return on != null && on.getAsBoolean();
		}

		void toggle() {
			if (hasToggle()) {
				setOn.accept(!isOn());
			}
		}

		void resetAll() {
			for (S s : settings) {
				if (s != key) {
					s.reset();
				}
			}
		}

		boolean allDefault() {
			for (S s : settings) {
				if (s != key && !s.isDefault()) {
					return false;
				}
			}
			return true;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	static final Map<String, S> ALL = new LinkedHashMap<>();
	static final List<Feature> FEATURES = new ArrayList<>();
	static volatile boolean dirty;

	private PinSettings() {
	}

	static S register(Feature f, S s) {
		ALL.put(s.id, s);
		f.settings.add(s);
		return s;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("pinvisuals").resolve("settings.json");
	}

	/** Reads config/pinvisuals/settings.json (missing or broken file = defaults). */
	static void load() {
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(r);
			if (!root.isJsonObject()) {
				return;
			}
			JsonObject o = root.getAsJsonObject();
			for (S s : ALL.values()) {
				if (s.bind != null || !o.has(s.id)) {
					continue;
				}
				JsonElement e = o.get(s.id);
				if (!e.isJsonPrimitive()) {
					continue;
				}
				switch (s.type) {
					case TEXT -> s.text = e.getAsString();
					case BOOL -> s.v = e.getAsBoolean() ? 1 : 0;
					case COLOR -> s.v = parseHex(e.getAsString(), (int) (long) s.def);
					case CHOICE -> s.v = Math.max(0, Math.min(Math.max(0, s.options.length - 1), e.getAsInt()));
					case KEY -> s.v = e.getAsInt();
					case NUM -> s.v = Math.max(s.min, Math.min(s.max, e.getAsDouble()));
				}
			}
		} catch (IOException | RuntimeException ex) {
			TextureStudioClient.LOGGER.warn("Pin Visuals: could not read settings.json, using defaults", ex);
		}
		dirty = false;
	}

	/** Writes the file if something changed. */
	static void saveIfDirty() {
		if (!dirty) {
			return;
		}
		dirty = false;
		JsonObject o = new JsonObject();
		for (S s : ALL.values()) {
			if (s.bind != null) {
				continue;
			}
			switch (s.type) {
				case TEXT -> o.addProperty(s.id, s.text());
				case BOOL -> o.addProperty(s.id, s.b());
				case COLOR -> o.addProperty(s.id, hex(s.color(), true));
				case CHOICE, KEY -> o.addProperty(s.id, s.i());
				case NUM -> o.addProperty(s.id, s.get());
			}
		}
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
				GSON.toJson(o, w);
			}
		} catch (IOException ignored) {
			// saving is best effort
		}
	}

	// ------------------------------------------------------------------ text forms

	static String hex(int argb, boolean withAlpha) {
		return withAlpha ? String.format(Locale.ROOT, "#%08X", argb)
				: String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
	}

	/** "#RRGGBB" or "#AARRGGBB" (the # is optional); anything else gives the fallback. */
	static int parseHex(String s, int fallback) {
		if (s == null) {
			return fallback;
		}
		String t = s.trim();
		if (t.startsWith("#")) {
			t = t.substring(1);
		}
		try {
			if (t.length() == 6) {
				return 0xFF000000 | Integer.parseInt(t, 16);
			}
			if (t.length() == 8) {
				return (int) Long.parseLong(t, 16);
			}
		} catch (NumberFormatException ignored) {
			// fall through
		}
		return fallback;
	}

	/** Readable name of a GLFW key code (0 = no key). */
	static String keyName(int key) {
		if (key <= 0) {
			return "None";
		}
		try {
			return com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
		} catch (RuntimeException ignored) {
			// unknown key
		}
		return "Key " + key;
	}
}
