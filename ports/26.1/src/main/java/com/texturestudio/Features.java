package com.texturestudio;

import com.texturestudio.PinSettings.Bind;
import com.texturestudio.PinSettings.Cat;
import com.texturestudio.PinSettings.Feature;
import com.texturestudio.PinSettings.S;
import com.texturestudio.PinSettings.Type;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Every feature of Pin Visuals with its settings, as shown in the menu ({@link PinMenuScreen}). Older settings stay
 * in {@link MixConfig} (the texture pack manager and the Studio tools still use them) and are bound here; new ones
 * live in config/pinvisuals/settings.json.
 */
final class Features {

	/** Default accent of the menu: a quiet blue that sits well on white. */
	static final int DEFAULT_ACCENT = 0xFF4C6FFF;

	/** The older config; reloaded whenever the menu opens (the texture pack manager keeps its own copy). */
	static MixConfig cfg = new MixConfig();
	private static boolean cfgDirty;
	/** true while this class itself calls Cosmetics.wear (so presets do not overwrite the colour settings). */
	private static boolean internal;

	private Features() {
	}

	// ================================================================== settings read by the features

	// Player outline
	static S outMode;
	static S outWidth;
	static S outColor;
	static S outColorMode;
	static S outSpeed;
	static S outFill;
	static S outRange;
	static S outOthers;
	static S outSelf;
	static S outFriends;
	static S outFriendMode;
	static S outFriendColor;
	// Pop chams
	static S popColor;
	static S popRainbow;
	static S popRise;
	static S popGrow;
	static S popSelf;
	// Hit colour
	static S hitColor;
	static S hitRainbow;
	static S hitSpeed;
	// Atmosphere
	static S atmoParticles;
	static S atmoDensity;
	static S atmoFog;
	static S atmoVignette;
	static S atmoCustom;
	static S atmoColor;
	// Block outline
	static S blockSpeed;
	// Kill effects
	static S killSound;
	static S killVolume;
	// Cosmetics
	static S wingSpeed;
	static S wingSize;
	static S hatSize;
	static S hatFloat;
	// Crosshair
	static S crossOpacity;
	static S crossRainbow;
	static S crossScale;
	static S crossThird;
	// Armor HUD
	static S armorPos;
	static S armorScale;
	static S armorWarn;
	static S armorBlink;
	// Spotify overlay
	static S spotScale;
	static S spotMargin;
	static S spotOpacity;
	// Lyrics
	static S lyricHeight;
	static S lyricScale;
	// Tweaks
	static S fireOffset;
	static S shieldOffset;
	static S itemDistance;
	// Freelook
	static S freeMode;
	static S freeThird;
	static S freeSpeed;
	static S freeInvert;
	// Knife
	static S knifeOn;
	static S knifeModel;
	static S knifeFinish;
	static S knifeAll;
	static S knifeThird;
	static S knifeInspect;
	// Hurt cam
	static S hurtOn;
	static S hurtStrength;
	// Zoom
	static S zoomFactor;
	static S zoomSmooth;
	static S zoomScroll;
	static S zoomCinematic;
	static S zoomHand;
	// Motion blur
	static S blurOn;
	static S blurStrength;
	// Trail
	static S trailOn;
	static S trailType;
	static S trailColor;
	static S trailRainbow;
	static S trailLength;
	static S trailAmount;
	static S trailSize;
	static S trailSprint;
	static S trailFirst;
	// Block break / place animation
	static S breakOn;
	static S breakStyle;
	static S breakColor;
	static S breakHideCracks;
	static S breakSize;
	static S placeOn;
	static S placeStrength;
	static S placeSpeed;
	static S placeFlash;
	// Aura
	static S auraOn;
	static S auraStyle;
	static S auraColor;
	static S auraRainbow;
	static S auraRadius;
	static S auraSpeed;
	static S auraGlow;
	static S auraFirst;
	static S auraPlace;
	static S auraHeight;
	// Item swap
	static S swapOn;
	static S swapStyle;
	static S swapSpeed;
	static S swapHotbar;
	// Custom skin
	static S skinOn;
	static S skinChoice;
	static S skinSlim;
	// Dura guard
	static S duraOn;
	static S duraMode;
	static S duraLeft;
	static S duraPercent;
	static S duraPick;
	static S duraAxe;
	static S duraShovel;
	static S duraHoe;
	static S duraSword;
	static S duraShears;
	static S duraOther;
	static S duraSneak;
	static S duraWarn;
	static S duraSound;
	// Item physics
	static S physicsOn;
	// Swing animation
	static S swingOn;
	static S swingStyle;
	static S swingSpeed;
	static S swingFatigue;
	static S swingAll;
	// Lyrics in the sky (scrambled)
	static S lyricMode;
	static S lyricTilt;
	static S lyricBlocks;
	static S lyricDistance;
	static S lyricSize;
	static S lyricOffset;
	// Menu
	static S menuAccent;
	static S menuAnim;

	// ================================================================== declaration helpers

	private static Feature feature(Cat cat, String id, String name, String desc) {
		Feature f = new Feature(cat, id, name, desc);
		PinSettings.FEATURES.add(f);
		return f;
	}

	private static S bool(Feature f, String id, String name, boolean def) {
		return PinSettings.register(f, new S(f.id + "." + id, name, Type.BOOL, def ? 1 : 0, ""));
	}

	private static S num(Feature f, String id, String name, double def, double min, double max, double step,
			String unit) {
		S s = new S(f.id + "." + id, name, Type.NUM, def, "");
		s.min = min;
		s.max = max;
		s.step = step;
		s.unit = unit;
		return PinSettings.register(f, s);
	}

	private static S color(Feature f, String id, String name, int def, boolean alpha) {
		S s = new S(f.id + "." + id, name, Type.COLOR, def, "");
		s.alpha = alpha;
		return PinSettings.register(f, s);
	}

	private static S choice(Feature f, String id, String name, int def, String... options) {
		S s = new S(f.id + "." + id, name, Type.CHOICE, def, "");
		s.options = options;
		return PinSettings.register(f, s);
	}

	private static S text(Feature f, String id, String name, String def, String hint) {
		S s = new S(f.id + "." + id, name, Type.TEXT, 0, def);
		s.hint = hint;
		return PinSettings.register(f, s);
	}

	/** Own on / off switch for a feature that is new (kept in settings.json, row hidden: the card switch shows it). */
	private static S ownToggle(Feature f, boolean def) {
		S on = PinSettings.register(f, new S(f.id + ".on", "On", Type.BOOL, def ? 1 : 0, "")).when(() -> false);
		f.on = on::b;
		f.setOn = v -> on.set(v ? 1 : 0);
		key(f);
		return on;
	}

	/**
	 * Key setting that is the game's own key binding (zoom, freelook, inspect knife): changing it here changes the key
	 * in the game's Controls too, so there is only one place that matters.
	 */
	private static S gameKey(Feature f, String id, String name, java.util.function.Supplier<net.minecraft.client.KeyMapping> km,
			int def) {
		return PinSettings.register(f, new S(f.id + "." + id, name, Type.KEY, def, "")).bind(new Bind() {
			@Override
			public double get() {
				net.minecraft.client.KeyMapping k = km.get();
				if (k == null || k.isUnbound()) {
					return 0;
				}
				return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(k).getValue();
			}

			@Override
			public void set(double v) {
				net.minecraft.client.KeyMapping k = km.get();
				if (k == null) {
					return;
				}
				int code = (int) Math.round(v);
				k.setKey(code <= 0 ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
						: com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(code));
				net.minecraft.client.KeyMapping.resetMapping();
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				if (mc.options != null) {
					mc.options.save();
				}
			}
		});
	}

	/** Adds the "Toggle key" setting (last row of every feature that can be switched). */
	private static void key(Feature f) {
		f.key = PinSettings.register(f, new S(f.id + ".key", "Toggle key", Type.KEY, 0, ""));
	}

	private static Bind cfgInt(IntSupplier get, IntConsumer set) {
		return new Bind() {
			@Override
			public double get() {
				return get.getAsInt();
			}

			@Override
			public void set(double v) {
				set.accept((int) Math.round(v));
				Tweaks.load(cfg);
				cfgDirty = true;
			}
		};
	}

	private static Bind cfgBool(BooleanSupplier get, Consumer<Boolean> set) {
		return new Bind() {
			@Override
			public double get() {
				return get.getAsBoolean() ? 1 : 0;
			}

			@Override
			public void set(double v) {
				set.accept(v != 0);
				Tweaks.load(cfg);
				cfgDirty = true;
			}
		};
	}

	/** Switch of a feature that is a plain boolean in the older config. */
	private static void cfgToggle(Feature f, BooleanSupplier get, Consumer<Boolean> set) {
		f.on = get;
		f.setOn = v -> {
			set.accept(v);
			Tweaks.load(cfg);
			cfgDirty = true;
		};
		key(f);
	}

	private static String[] names(List<Cosmetics.Item> items) {
		String[] n = new String[items.size()];
		for (int i = 0; i < n.length; i++) {
			n[i] = items.get(i).name();
		}
		return n;
	}

	private static int indexOf(List<Cosmetics.Item> items, String id) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static void wear(Cosmetics.Kind kind, String id) {
		internal = true;
		try {
			Cosmetics.wear(cfg, kind, id);
		} finally {
			internal = false;
		}
	}

	/**
	 * Switch of a feature that is "a style or none" in the older config. With a style setting the picked style is
	 * worn while the feature is on; without one the first style is used just to mark the feature as on.
	 */
	private static S styled(Feature f, Cosmetics.Kind kind, String styleLabel) {
		List<Cosmetics.Item> items = Cosmetics.list(kind);
		S style = styleLabel == null ? null : choice(f, "style", styleLabel, 0, names(items));
		f.on = () -> !Cosmetics.NONE.equals(Cosmetics.worn(kind));
		f.setOn = v -> wear(kind, v ? items.get(style == null ? 0 : style.i()).id() : null);
		if (style != null) {
			style.onChange(() -> {
				if (f.isOn()) {
					wear(kind, items.get(style.i()).id());
				}
			});
		}
		return style;
	}

	private static final Cosmetics.Kind[] STYLE_KINDS = { Cosmetics.Kind.ATMO, Cosmetics.Kind.KILL, Cosmetics.Kind.CAPE,
			Cosmetics.Kind.HAT, Cosmetics.Kind.WINGS, Cosmetics.Kind.ARMOR, Cosmetics.Kind.SPOTIFY, Cosmetics.Kind.LYRICS };
	private static final S[] STYLE_SETTINGS = new S[STYLE_KINDS.length];

	// ================================================================== the features

	static void init() {
		if (!PinSettings.FEATURES.isEmpty()) {
			return;
		}
		cfg = MixConfig.load();

		// ---------------------------------------------------------------- Render
		Feature f = feature(Cat.RENDER, "block_outline", "Block Outline",
				"Colour, thickness and glow of the outline around the block you look at");
		cfgToggle(f, () -> cfg.blockOutlineOn, v -> cfg.blockOutlineOn = v);
		color(f, "color", "Colour", 0xFF22D3EE, false)
				.bind(cfgInt(() -> cfg.blockOutlineColor | 0xFF000000, v -> cfg.blockOutlineColor = v | 0xFF000000))
				.when(() -> !cfg.blockOutlineRainbow);
		bool(f, "rainbow", "Rainbow", false)
				.bind(cfgBool(() -> cfg.blockOutlineRainbow, v -> cfg.blockOutlineRainbow = v));
		blockSpeed = num(f, "speed", "Rainbow speed", 1, 0.2, 4, 0.1, "x").when(() -> cfg.blockOutlineRainbow);
		num(f, "width", "Thickness", 4, 1, 10, 1, "")
				.bind(cfgInt(() -> cfg.blockOutlineWidth, v -> cfg.blockOutlineWidth = v));
		num(f, "glow", "Glow", 40, 0, 100, 1, "%")
				.bind(cfgInt(() -> cfg.blockOutlineGlow, v -> cfg.blockOutlineGlow = v));

		f = feature(Cat.RENDER, "atmosphere", "Atmosphere", "Coloured fog, mist and particles around you");
		STYLE_SETTINGS[0] = styled(f, Cosmetics.Kind.ATMO, "Style");
		num(f, "range", "Range", 32, 8, 96, 1, " blocks").bind(cfgInt(() -> cfg.atmoRange, v -> cfg.atmoRange = v));
		num(f, "strength", "Strength", 60, 10, 100, 1, "%")
				.bind(cfgInt(() -> cfg.atmoStrength, v -> cfg.atmoStrength = v));
		atmoFog = bool(f, "fog", "Coloured fog", true);
		atmoParticles = bool(f, "particles", "Particles", true);
		atmoDensity = num(f, "density", "Particle amount", 100, 10, 200, 5, "%").when(() -> atmoParticles.b());
		atmoVignette = bool(f, "vignette", "Screen vignette", true);
		atmoCustom = bool(f, "custom", "Own fog colour", false);
		atmoColor = color(f, "color", "Fog colour", 0xFF7C3AED, false).when(() -> atmoCustom.b());
		key(f);

		f = feature(Cat.RENDER, "kill_effects", "Kill Effects", "Particles and sound where a player you killed dies");
		STYLE_SETTINGS[1] = styled(f, Cosmetics.Kind.KILL, "Effect");
		killSound = bool(f, "sound", "Sound", true);
		killVolume = num(f, "volume", "Volume", 100, 10, 200, 5, "%").when(() -> killSound.b());
		key(f);

		f = feature(Cat.RENDER, "viewmodel", "Viewmodel", "Size, position and angle of the item in your hand");
		cfgToggle(f, () -> cfg.vmOn, v -> cfg.vmOn = v);
		num(f, "scale", "Size", 100, 30, 200, 1, "%").bind(cfgInt(() -> cfg.vmScale, v -> cfg.vmScale = v));
		num(f, "x", "Left / right", 0, -100, 100, 1, "").bind(cfgInt(() -> cfg.vmX, v -> cfg.vmX = v));
		num(f, "y", "Up / down", 0, -100, 100, 1, "").bind(cfgInt(() -> cfg.vmY, v -> cfg.vmY = v));
		num(f, "z", "Near / far", 0, -100, 100, 1, "").bind(cfgInt(() -> -cfg.vmZ, v -> cfg.vmZ = -v));
		num(f, "rx", "Tilt", 0, -180, 180, 1, "°").bind(cfgInt(() -> cfg.vmRotX, v -> cfg.vmRotX = v));
		num(f, "ry", "Turn", 0, -180, 180, 1, "°").bind(cfgInt(() -> cfg.vmRotY, v -> cfg.vmRotY = v));
		num(f, "rz", "Roll", 0, -180, 180, 1, "°").bind(cfgInt(() -> cfg.vmRotZ, v -> cfg.vmRotZ = v));

		f = feature(Cat.RENDER, "hurt_cam", "Hurt Cam", "Less or no camera shake when you take damage");
		hurtOn = ownToggle(f, false);
		hurtStrength = num(f, "strength", "Shake strength", 30, 0, 100, 1, "%");

		f = feature(Cat.RENDER, "zoom", "Zoom", "Smooth zoom while you hold the zoom key");
		gameKey(f, "zoom_key", "Zoom key", () -> Zoom.key, com.mojang.blaze3d.platform.InputConstants.KEY_C);
		zoomFactor = num(f, "factor", "Zoom strength", 4, 1.5, 12, 0.5, "x");
		zoomSmooth = num(f, "smooth", "Smoothness", 60, 0, 100, 5, "%");
		zoomScroll = bool(f, "scroll", "Scroll to zoom in / out", true);
		zoomCinematic = bool(f, "cinematic", "Smooth camera while zooming", false);
		zoomHand = bool(f, "hide_hand", "Hide hand while zooming", true);

		f = feature(Cat.RENDER, "freelook", "Freelook",
				"Look around without turning your player");
		gameKey(f, "freelook_key", "Freelook key", () -> Freelook.key,
				com.mojang.blaze3d.platform.InputConstants.KEY_LALT);
		freeMode = choice(f, "mode", "Key", 0, "Hold", "Press to switch");
		freeThird = bool(f, "third", "Switch to third person", true);
		freeSpeed = num(f, "speed", "Mouse speed", 100, 25, 250, 5, "%");
		freeInvert = bool(f, "invert", "Invert up / down", false);

		f = feature(Cat.RENDER, "block_break", "Block Break Animation",
				"A battery that charges up, a colour that fills the block or a glow instead of the cracks");
		breakOn = ownToggle(f, false);
		breakStyle = choice(f, "style", "Style", 0, BlockFx.BREAK_STYLES);
		breakColor = color(f, "color", "Colour", 0xFFA855F7, false).when(() -> breakStyle.i() != 0);
		breakSize = num(f, "size", "Battery size", 100, 50, 200, 5, "%").when(() -> breakStyle.i() == 0);
		breakHideCracks = bool(f, "hide_cracks", "Hide the cracks", true);

		f = feature(Cat.RENDER, "block_place", "Block Place Animation", "Placed blocks pop up for a moment");
		placeOn = ownToggle(f, false);
		placeStrength = num(f, "strength", "Pop size", 18, 5, 45, 1, "%");
		placeSpeed = num(f, "speed", "Speed", 100, 40, 250, 5, "%");
		placeFlash = bool(f, "flash", "White flash", true);

		f = feature(Cat.RENDER, "item_physics", "Item Physics", "Dropped items lie flat on the ground instead of floating and spinning");
		physicsOn = ownToggle(f, false);

		f = feature(Cat.RENDER, "motion_blur", "Motion Blur", "A light motion blur when you move the camera");
		blurOn = ownToggle(f, false);
		blurStrength = num(f, "strength", "Strength", 50, 10, 90, 10, "%");

		f = feature(Cat.RENDER, "low_fire", "Low Fire", "Draws the burning overlay lower so you see more");
		cfgToggle(f, () -> cfg.lowFire, v -> cfg.lowFire = v);
		fireOffset = num(f, "offset", "How far down", 30, 5, 60, 1, "%").onChange(Features::applyTweaks);

		f = feature(Cat.RENDER, "low_shield", "Low Shield", "Holds the shield lower in first person");
		cfgToggle(f, () -> cfg.lowShield, v -> cfg.lowShield = v);
		shieldOffset = num(f, "offset", "How far down", 25, 5, 60, 1, "%").onChange(Features::applyTweaks);

		f = feature(Cat.RENDER, "no_totem_pop", "No Totem Animation",
				"Hides the big totem that pops up on screen when a totem is used");
		cfgToggle(f, () -> cfg.noTotemPop, v -> cfg.noTotemPop = v);

		// ---------------------------------------------------------------- Player
		f = feature(Cat.PLAYER, "outline", "Player Outline", "Coloured line around players (hidden behind blocks)");
		styled(f, Cosmetics.Kind.OUTLINE, null);
		outMode = choice(f, "mode", "Mode", 0, "Outline", "Outline + Fill", "Glow");
		outWidth = num(f, "width", "Line width", 1, 0.4, 3, 0.1, "x");
		outColorMode = choice(f, "color_mode", "Colour from", 0, "Picked colour", "Rainbow", "Team", "Health");
		outColor = color(f, "color", "Colour", 0xFF22D3EE, true).when(() -> outColorMode.i() == 0);
		outSpeed = num(f, "speed", "Rainbow speed", 1, 0.2, 4, 0.1, "x").when(() -> outColorMode.i() == 1);
		outFill = num(f, "fill", "Fill opacity", 25, 5, 80, 1, "%").when(() -> outMode.i() == 1);
		outRange = num(f, "range", "Max distance", 160, 8, 160, 1, " blocks");
		outOthers = bool(f, "others", "Other players", true);
		outSelf = bool(f, "self", "Yourself (third person)", false);
		outFriends = text(f, "friends", "Friends", "", "Names, separated by commas");
		outFriendMode = choice(f, "friend_mode", "Friends are shown", 1, "Like everyone", "In their own colour",
				"Not at all");
		outFriendColor = color(f, "friend_color", "Friend colour", 0xFF4ADE80, true)
				.when(() -> outFriendMode.i() == 1);
		outFriends.onChange(PlayerOutline::friendsChanged);
		key(f);

		f = feature(Cat.PLAYER, "pop_chams", "Pop Chams", "A coloured ghost where a player popped a totem");
		styled(f, Cosmetics.Kind.POP, null);
		popColor = color(f, "color", "Colour", 0xA0EF4444, true).when(() -> !popRainbow.b());
		popRainbow = bool(f, "rainbow", "Rainbow", false);
		num(f, "time", "Ghost time", 1.3, 0.5, 5, 0.1, " s").bind(new Bind() {
			@Override
			public double get() {
				return cfg.popSeconds;
			}

			@Override
			public void set(double v) {
				cfg.popSeconds = (float) (Math.round(v * 10) / 10.0);
				Tweaks.load(cfg);
				cfgDirty = true;
			}
		});
		popRise = num(f, "rise", "Rise height", 0.9, 0, 3, 0.1, " blocks");
		popGrow = num(f, "grow", "Grow while fading", 12, 0, 60, 1, "%");
		popSelf = bool(f, "self", "Also for your own pops", false);
		key(f);

		f = feature(Cat.PLAYER, "hit_color", "Hit Colour", "Colour of the flash when a mob or player is hit");
		styled(f, Cosmetics.Kind.HIT, null);
		hitColor = color(f, "color", "Colour and strength", 0xB22F7BFF, true).when(() -> !hitRainbow.b());
		hitRainbow = bool(f, "rainbow", "Rainbow", false);
		hitSpeed = num(f, "speed", "Rainbow speed", 1, 0.2, 4, 0.1, "x").when(() -> hitRainbow.b());
		key(f);

		f = feature(Cat.PLAYER, "cape", "Cape", "Your own cape (only you see it)");
		STYLE_SETTINGS[2] = styled(f, Cosmetics.Kind.CAPE, "Cape");
		key(f);

		f = feature(Cat.PLAYER, "hat", "Hat", "A hat on your head (only you see it)");
		STYLE_SETTINGS[3] = styled(f, Cosmetics.Kind.HAT, "Hat");
		hatSize = num(f, "size", "Size", 100, 60, 160, 5, "%");
		hatFloat = bool(f, "float", "Floating hats bob up and down", true);
		key(f);

		f = feature(Cat.PLAYER, "wings", "Wings", "Wings on your back (only you see them)");
		STYLE_SETTINGS[4] = styled(f, Cosmetics.Kind.WINGS, "Wings");
		wingSize = num(f, "size", "Size", 100, 60, 160, 5, "%");
		wingSpeed = num(f, "speed", "Flap speed", 100, 0, 300, 5, "%");
		key(f);

		f = feature(Cat.PLAYER, "trail", "Trail", "A particle trail behind you while you walk");
		trailOn = ownToggle(f, false);
		trailType = choice(f, "type", "Particles", 0, Trail.NAMES);
		trailColor = color(f, "color", "Colour", 0xFFA855F7, false).when(() -> Trail.coloured(trailType.i()) && !trailRainbow.b());
		trailRainbow = bool(f, "rainbow", "Rainbow", false).when(() -> Trail.coloured(trailType.i()));
		trailLength = num(f, "length", "Length", 1.2, 0.2, 4, 0.1, " s");
		trailAmount = num(f, "amount", "Amount", 4, 1, 12, 1, " per block");
		trailSize = num(f, "size", "Size", 100, 40, 250, 5, "%").when(() -> Trail.coloured(trailType.i()));
		trailSprint = bool(f, "sprint", "Only while sprinting", false);
		trailFirst = bool(f, "first_person", "Also in first person", true);

		f = feature(Cat.PLAYER, "aura", "Aura", "An animated ring that floats around you (or lies on the ground)");
		auraOn = ownToggle(f, false);
		auraPlace = choice(f, "place", "Where", 0, Aura.PLACES);
		auraHeight = num(f, "height", "Height", 1.0, 0.2, 2.2, 0.05, " blocks").when(() -> auraPlace.i() == 0);
		auraStyle = choice(f, "style", "Style", 0, Aura.STYLES);
		auraColor = color(f, "color", "Colour", 0xFFA855F7, false).when(() -> !auraRainbow.b());
		auraRainbow = bool(f, "rainbow", "Rainbow", false);
		auraRadius = num(f, "radius", "Radius", 0.9, 0.4, 3, 0.05, " blocks");
		auraSpeed = num(f, "speed", "Rotation", 60, -300, 300, 10, "%");
		auraGlow = num(f, "glow", "Glow", 60, 0, 100, 5, "%");
		auraFirst = bool(f, "first_person", "Also in first person", true);

		f = feature(Cat.PLAYER, "knife", "Knife",
				"Your sword looks like a CS knife (only on your screen)");
		knifeOn = ownToggle(f, false);
		knifeModel = choice(f, "model", "Knife", 0, Knives.MODEL_NAMES);
		knifeFinish = choice(f, "finish", "Finish", 0, Knives.FINISH_NAMES);
		knifeAll = choice(f, "items", "Replaces", 0, "All swords", "Diamond and netherite swords", "Netherite sword");
		knifeThird = bool(f, "third", "Also in third person", true);
		knifeInspect = bool(f, "inspect", "Inspect animation", true);
		gameKey(f, "inspect_key", "Inspect key", () -> Knives.inspectKey,
				com.mojang.blaze3d.platform.InputConstants.KEY_Y).when(() -> knifeInspect.b());

		f = feature(Cat.PLAYER, "swing", "Swing Animation", "Slower, faster or 1.7-style arm swing (only on your screen)");
		swingOn = ownToggle(f, false);
		swingStyle = choice(f, "style", "Style", 0, "Custom speed", "1.7 (no cooldown dip)", "Mining fatigue");
		swingSpeed = num(f, "speed", "Swing speed", 70, 25, 300, 5, "%").when(() -> swingStyle.i() != 2);
		swingFatigue = num(f, "fatigue", "Fatigue level", 1, 1, 10, 1, "").when(() -> swingStyle.i() == 2);
		swingAll = bool(f, "mining", "Also while mining", true);

		f = feature(Cat.PLAYER, "item_swap", "Item Swap Animation",
				"The new item glides into your hand and the hotbar selection slides over");
		swapOn = ownToggle(f, false);
		swapStyle = choice(f, "style", "Hand animation", 0, ItemSwap.STYLES);
		swapSpeed = num(f, "speed", "Speed", 100, 40, 250, 5, "%");
		swapHotbar = bool(f, "hotbar", "Sliding hotbar selection", true);

		f = feature(Cat.PLAYER, "dura_guard", "Dura Guard", "Stops mining when your tool is about to break");
		duraOn = ownToggle(f, false);
		duraMode = choice(f, "mode", "Stop at", 0, DuraGuard.MODES);
		duraLeft = num(f, "left", "Durability left", 10, 1, 200, 1, "").when(() -> duraMode.i() == 0);
		duraPercent = num(f, "percent", "Percent left", 3, 1, 50, 1, "%").when(() -> duraMode.i() == 1);
		duraPick = bool(f, "pickaxe", "Pickaxes", true);
		duraAxe = bool(f, "axe", "Axes", true);
		duraShovel = bool(f, "shovel", "Shovels", true);
		duraHoe = bool(f, "hoe", "Hoes", false);
		duraSword = bool(f, "sword", "Swords (also stops hits)", false);
		duraShears = bool(f, "shears", "Shears", false);
		duraOther = bool(f, "other", "Other tools", false);
		duraSneak = bool(f, "sneak", "Sneak to keep going", true);
		duraWarn = bool(f, "warn", "Show a warning", true);
		duraSound = bool(f, "sound", "Warning sound", true);

		f = feature(Cat.PLAYER, "custom_skin", "Custom Skin", "Wear another skin, e.g. an alien (only on your screen)");
		skinOn = ownToggle(f, false);
		skinChoice = choice(f, "skin", "Skin", 0, Skins.NAMES);
		skinSlim = bool(f, "slim", "Slim arms", false);

		// ---------------------------------------------------------------- Texture packs
		f = feature(Cat.TEXTURES, "pack_manager", "Texture Pack Manager",
				"Browse your packs, mix styles per collection, preview and apply them");
		f.actionLabel = "Open";

		f = feature(Cat.TEXTURES, "pack_browser", "Pack Browser",
				"Search resource packs on Modrinth and download them into your packs folder");
		f.actionLabel = "Open";

		// ---------------------------------------------------------------- HUD
		f = feature(Cat.HUD, "crosshair", "Crosshair", "Your own crosshair instead of the vanilla one");
		cfgToggle(f, () -> cfg.crossOn, v -> cfg.crossOn = v);
		choice(f, "shape", "Shape", 0, "Cross", "Dot", "Circle", "T", "X", "Box")
				.bind(cfgInt(() -> cfg.crossShape, v -> cfg.crossShape = v));
		color(f, "color", "Colour", 0xFFFFFFFF, false)
				.bind(cfgInt(() -> cfg.crossColor | 0xFF000000, v -> cfg.crossColor = v | 0xFF000000))
				.when(() -> !crossRainbow.b());
		crossRainbow = bool(f, "rainbow", "Rainbow", false);
		crossOpacity = num(f, "opacity", "Opacity", 100, 10, 100, 1, "%");
		crossScale = num(f, "scale", "Scale", 1, 1, 4, 1, "x");
		num(f, "gap", "Gap", 2, 0, 10, 1, " px").bind(cfgInt(() -> cfg.crossGap, v -> cfg.crossGap = v));
		num(f, "length", "Length", 4, 1, 12, 1, " px").bind(cfgInt(() -> cfg.crossLen, v -> cfg.crossLen = v));
		num(f, "thickness", "Thickness", 1, 1, 4, 1, " px")
				.bind(cfgInt(() -> cfg.crossThick, v -> cfg.crossThick = v));
		bool(f, "dot", "Centre dot", false).bind(cfgBool(() -> cfg.crossDot, v -> cfg.crossDot = v));
		bool(f, "outline", "Dark outline", true).bind(cfgBool(() -> cfg.crossOutline, v -> cfg.crossOutline = v));
		crossThird = bool(f, "third_person", "Show in third person", false);

		f = feature(Cat.HUD, "armor_hud", "Armor HUD", "Your armor with its durability next to the hotbar");
		STYLE_SETTINGS[5] = styled(f, Cosmetics.Kind.ARMOR, "Style");
		armorPos = choice(f, "position", "Position", 0, "Left of hotbar", "Right of hotbar", "Top left",
				"Top right");
		armorScale = num(f, "scale", "Size", 100, 50, 200, 5, "%");
		armorWarn = num(f, "warn", "Warn below", 15, 0, 50, 1, "%");
		armorBlink = bool(f, "blink", "Blink when almost broken", true);
		key(f);

		f = feature(Cat.HUD, "spotify", "Spotify Overlay", "Now-playing card with cover, title and progress");
		STYLE_SETTINGS[6] = styled(f, Cosmetics.Kind.SPOTIFY, "Style");
		choice(f, "side", "Corner", 0, "Top left", "Top right")
				.bind(cfgInt(() -> cfg.spotifyRight ? 1 : 0, v -> cfg.spotifyRight = v == 1));
		spotScale = num(f, "scale", "Size", 100, 60, 200, 5, "%");
		spotOpacity = num(f, "opacity", "Opacity", 100, 20, 100, 1, "%");
		spotMargin = num(f, "margin", "Distance from the edge", 6, 0, 80, 1, " px");
		key(f);

		f = feature(Cat.HUD, "lyrics", "Spotify Lyrics", "Synced lyrics of the current song in the sky");
		STYLE_SETTINGS[7] = styled(f, Cosmetics.Kind.LYRICS, "Style");
		bool(f, "through_blocks", "Visible through blocks", true)
				.bind(cfgBool(() -> cfg.lyricsThroughBlocks, v -> cfg.lyricsThroughBlocks = v));
		lyricMode = choice(f, "mode", "Look", 0, "Calm line", "Scrambled in the sky");
		lyricHeight = num(f, "height", "Height above the horizon", 34, 5, 70, 1, "°").when(() -> lyricMode.i() == 0);
		lyricScale = num(f, "scale", "Text size", 100, 60, 180, 5, "%").when(() -> lyricMode.i() == 0);
		lyricTilt = num(f, "tilt", "Word tilt", 12, 0, 25, 1, "°").when(() -> lyricMode.i() == 1);
		lyricBlocks = num(f, "blocks", "Lines at once", 3, 1, 6, 1, "").when(() -> lyricMode.i() == 1);
		lyricDistance = num(f, "distance", "Distance", 60, 20, 160, 5, " blocks").when(() -> lyricMode.i() == 1);
		lyricSize = num(f, "size", "Size in the sky", 100, 40, 250, 5, "%").when(() -> lyricMode.i() == 1);
		lyricOffset = num(f, "offset", "Sync offset", 0, -3000, 3000, 50, " ms");
		key(f);

		// ---------------------------------------------------------------- Misc
		f = feature(Cat.MISC, "presets", "Presets", "Save your whole setup under a name and load it again in one click");
		f.actionLabel = "Open";

		f = feature(Cat.MISC, "no_totem_particles", "No Totem Particles",
				"Hides the green and yellow particles around a player who used a totem");
		cfgToggle(f, () -> cfg.noTotemParticles, v -> cfg.noTotemParticles = v);

		f = feature(Cat.MISC, "no_explosion_particles", "No Explosion Particles",
				"No smoke and flash particles of crystals, anchors and TNT");
		cfgToggle(f, () -> cfg.noExplosionParticles, v -> cfg.noExplosionParticles = v);

		f = feature(Cat.MISC, "fewer_hit_particles", "Fewer Hit Particles",
				"No crit, enchanted-hit, sweep and damage particles");
		cfgToggle(f, () -> cfg.fewerHitParticles, v -> cfg.fewerHitParticles = v);

		f = feature(Cat.MISC, "calm_crystals", "Calm Crystals", "End crystals stand still instead of spinning");
		cfgToggle(f, () -> cfg.calmCrystals, v -> cfg.calmCrystals = v);

		f = feature(Cat.MISC, "hide_far_items", "Hide Far Items", "Dropped items far away are not drawn");
		cfgToggle(f, () -> cfg.hideFarItems, v -> cfg.hideFarItems = v);
		itemDistance = num(f, "distance", "Hide beyond", 16, 4, 64, 1, " blocks").onChange(Features::applyTweaks);

		f = feature(Cat.MISC, "title_screen", "Pin Visuals Title Screen",
				"White Pin Visuals title and loading screen instead of the vanilla ones");
		cfgToggle(f, () -> cfg.pinMenu, v -> cfg.pinMenu = v);

		f = feature(Cat.MISC, "menu", "Menu", "Animations of this menu (colours: Themes in the main menu)");
		menuAccent = color(f, "accent", "Accent colour", DEFAULT_ACCENT, false).when(() -> false); // colours come from Themes
		menuAnim = bool(f, "animations", "Animations", true);

		PinSettings.load();
		PlayerOutline.friendsChanged();
		applyTweaks();
		syncStyles();
	}

	/** Values that the mixins read from {@link Tweaks}. */
	static void applyTweaks() {
		Tweaks.LOW_FIRE_OFFSET = -fireOffset.f() / 100f;
		Tweaks.LOW_SHIELD_OFFSET = -shieldOffset.f() / 100f;
		Tweaks.FAR_ITEM_DISTANCE = itemDistance.get();
	}

	/** Reads the older config again (the texture pack manager may have changed it) and applies it. */
	static void reload() {
		flush();
		cfg = MixConfig.load();
		Cosmetics.load(cfg);
		Tweaks.load(cfg);
		syncStyles();
	}

	/** The style dropdowns show what is worn right now. */
	private static void syncStyles() {
		for (int i = 0; i < STYLE_KINDS.length; i++) {
			S s = STYLE_SETTINGS[i];
			if (s == null) {
				continue;
			}
			int idx = indexOf(Cosmetics.list(STYLE_KINDS[i]), Cosmetics.worn(STYLE_KINDS[i]));
			if (idx >= 0 && idx != s.i()) {
				s.setQuiet(idx);
				PinSettings.dirty = true;
			}
		}
	}

	/** Saves both config files if something changed. */
	static void flush() {
		if (cfgDirty) {
			cfgDirty = false;
			cfg.save();
		}
		PinSettings.saveIfDirty();
	}

	/**
	 * A colour preset was picked in the texture pack manager (Extras): copy its colour into the settings so both
	 * menus agree.
	 */
	static void presetChosen(Cosmetics.Kind kind, String id) {
		if (internal || id == null || Cosmetics.NONE.equals(id) || outColor == null) {
			return;
		}
		boolean rainbow = "rainbow".equals(id);
		switch (kind) {
			case OUTLINE -> {
				outColorMode.set(rainbow ? 1 : 0);
				if (!rainbow) {
					outColor.setColor(0xFF000000 | PlayerOutline.color(id, 0));
				}
			}
			case POP -> {
				popRainbow.set(rainbow ? 1 : 0);
				if (!rainbow) {
					popColor.setColor(0xA0000000 | PopChams.color(id, 0));
				}
			}
			case HIT -> {
				hitRainbow.set(rainbow ? 1 : 0);
				if (!rainbow) {
					hitColor.setColor(HitColor.color(id, 0));
				}
			}
			default -> {
				return;
			}
		}
		PinSettings.saveIfDirty();
	}

	static int accent() {
		return menuAccent == null ? DEFAULT_ACCENT : 0xFF000000 | menuAccent.color();
	}

	/** Rainbow colour (opaque RGB) for the given speed factor. */
	static int rainbow(double speed, long periodMs) {
		double p = periodMs / Math.max(0.05, speed);
		return HitColor.hsv((float) ((System.currentTimeMillis() % (long) Math.max(1, p)) / p)) & 0xFFFFFF;
	}

	// ================================================================== toggle keys and the little toast

	private static final java.util.Map<Feature, Boolean> KEY_DOWN = new java.util.HashMap<>();
	private static String toast = "";
	private static boolean toastOn;
	private static long toastAt;

	/** Called every client tick: a feature's toggle key switches it while no screen is open. */
	static void tickKeys(Minecraft mc) {
		if (Nav.current() != null || mc.player == null) {
			KEY_DOWN.clear();
			return;
		}
		for (Feature f : PinSettings.FEATURES) {
			if (f.key == null || !f.hasToggle()) {
				continue;
			}
			int k = f.key.i();
			if (k <= 0) {
				continue;
			}
			boolean down = Nav.keyDown(k);
			boolean was = Boolean.TRUE.equals(KEY_DOWN.put(f, down));
			if (down && !was) {
				f.toggle();
				flush();
				toast = f.name;
				noticeDetail = null;
				toastLen = 1600;
				toastOn = f.isOn();
				toastAt = System.currentTimeMillis();
			}
		}
	}

	/** A note in the toast pill (two parts, the second one in the accent colour), e.g. after keys were moved. */
	static void notice(String title, String detail) {
		toast = title;
		noticeDetail = detail;
		toastAt = System.currentTimeMillis();
		toastLen = 5000; // stays a bit longer
	}

	private static String noticeDetail;
	private static long toastLen = 1600;

	/** HUD element: "Feature  ON/OFF" pill at the top for a moment after a toggle key was pressed. */
	static void toastHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		long age = System.currentTimeMillis() - toastAt;
		if (toast.isEmpty() || age > toastLen) {
			return;
		}
		float a = age < 150 ? age / 150f : age > toastLen - 300 ? (toastLen - age) / 300f : 1f;
		Font font = MenuFonts.get("nunito");
		String state = noticeDetail != null ? noticeDetail : toastOn ? "ON" : "OFF";
		int tw = font.width(toast) + 8 + font.width(state);
		int w = tw + 20;
		int x = (g.guiWidth() - w) / 2;
		int y = 8 - Math.round((1f - a) * 6);
		int al = Math.round(255 * a);
		if (al < 8) {
			return;
		}
		TextureStudioScreen.rrect(g, x, y, w, 18, 8, TextureStudioScreen.alpha(0xFFFFFF, al * 240 / 255));
		TextureStudioScreen.outline(g, x, y, w, 18, 8, TextureStudioScreen.alpha(0xD9DDE6, al));
		FillBatch.flush(g).text(font, toast, x + 10, y + 5, TextureStudioScreen.alpha(0x1B2030, al), false);
		FillBatch.flush(g).text(font, state, x + 10 + font.width(toast) + 8, y + 5,
				TextureStudioScreen.alpha(toastOn ? accent() : 0x9AA1B2, al), false);
	}
}
