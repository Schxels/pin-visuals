package com.texturestudio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Viele Packs nutzen noch Texturpfade aelterer Minecraft-Versionen, die 26.2 nicht mehr laedt.
 * Hier steht, wohin solche Dateien heute gehoeren, damit der Mix (und die Vorschau) sie trotzdem nutzen kann.
 */
public final class LegacyPaths {

	public static final String TEX = "assets/minecraft/textures/";
	public static final String MOON_PHASES = TEX + "environment/moon_phases.png";

	/** Reihenfolge der Mondphasen im alten 4x2-Raster moon_phases.png -> neue Einzeldateien. */
	public static final String[] MOON_FILES = { "full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
			"new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous" };

	private static final Pattern ARMOR = Pattern.compile("models/armor/([a-z_]+?)_layer_([12])(_overlay)?\\.png");

	private LegacyPaths() {
	}

	public static String moonFile(int i) {
		return TEX + "environment/celestial/moon/" + MOON_FILES[i] + ".png";
	}

	/** Neuer Pfad fuer einen alten, oder null, wenn der Pfad schon aktuell ist (moon_phases: Sonderfall). */
	public static String modern(String full) {
		if (!full.startsWith(TEX)) {
			return null;
		}
		String rel = full.substring(TEX.length());
		switch (rel) {
			case "environment/sun.png":
				return TEX + "environment/celestial/sun.png";
			case "entity/shield_base_nopattern.png", "entity/shield.png":
				return TEX + "entity/shield/shield_base_nopattern.png";
			case "entity/shield_base.png":
				return TEX + "entity/shield/shield_base.png";
			case "entity/trident.png":
				return TEX + "entity/trident/trident.png";
			case "entity/trident_riptide.png":
				return TEX + "entity/trident/trident_riptide.png";
			default:
				break;
		}
		Matcher m = ARMOR.matcher(rel);
		if (m.matches()) {
			String mat = m.group(1).equals("golden") ? "gold" : m.group(1);
			String layer = m.group(2).equals("1") ? "humanoid" : "humanoid_leggings";
			String overlay = m.group(3) == null ? "" : "_overlay";
			return TEX + "entity/equipment/" + layer + "/" + mat + overlay + ".png";
		}
		return null;
	}
}
