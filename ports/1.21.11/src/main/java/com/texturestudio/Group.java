package com.texturestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Eine Sammlung (Crystal, Armor, Totem ...): bestimmt anhand des Texturpfads, welche Texturen dazugehoeren.
 * Ein "Stil" ist dann ein Pack, das mindestens eine Textur dieser Sammlung liefert.
 */
public record Group(String id, String label, int color, boolean extra, Predicate<String> match,
		List<String> previews) {

	public static final List<Group> ALL = new ArrayList<>();

	static {
		add("crystal", "Crystal", 0xFFE879F9, false, r -> r.contains("end_crystal"),
				"item/end_crystal", "entity/end_crystal/end_crystal");
		add("obsidian", "Obsidian", 0xFF5B3FA6, false,
				r -> r.equals("block/obsidian") || r.equals("block/crying_obsidian"),
				"block/obsidian", "block/crying_obsidian");
		add("anchor", "Anchor", 0xFF8B5CF6, false, r -> r.startsWith("block/respawn_anchor"),
				"block/respawn_anchor_top", "block/respawn_anchor_side4");
		add("armor", "Armor", 0xFF9CA3AF, false,
				r -> r.startsWith("entity/equipment/humanoid") || r.startsWith("models/armor/")
						|| (r.startsWith("item/") && endsAny(r, "_helmet", "_chestplate", "_leggings", "_boots")),
				"item/diamond_chestplate", "item/diamond_helmet", "item/netherite_chestplate", "item/iron_chestplate");
		add("tools", "Tools", 0xFF60A5FA, false,
				r -> r.startsWith("item/") && endsAny(r, "_sword", "_axe", "_pickaxe", "_shovel", "_hoe"),
				"item/diamond_sword", "item/netherite_sword", "item/iron_sword", "item/diamond_pickaxe");
		add("gaps", "Gaps & Pearls", 0xFFFBBF24, false,
				r -> r.equals("item/golden_apple") || r.equals("item/enchanted_golden_apple")
						|| r.equals("item/ender_pearl"),
				"item/golden_apple", "item/ender_pearl");
		add("totem", "Totem", 0xFFF59E0B, false, r -> r.contains("totem"), "item/totem_of_undying");
		add("hud", "HUD", 0xFFF97316, false, r -> r.startsWith("gui/sprites/hud/"),
				"gui/sprites/hud/heart/full", "gui/sprites/hud/hotbar_selection");
		add("inventory", "Inventory", 0xFF94A3B8, false,
				r -> r.startsWith("gui/container/") || r.startsWith("gui/sprites/container/"),
				"gui/container/inventory");
		add("fire", "Fire", 0xFFEF4444, false,
				r -> r.startsWith("block/fire_") || r.startsWith("block/soul_fire_"), "block/fire_0", "block/soul_fire_0");
		// Sky: only custom skybox packs (OptiFine / MCPatcher / legacy FabricSkyBoxes data, see isSkyData);
		// sun and moon textures of normal packs are not offered here any more.
		add("sky", "Sky", 0xFF38BDF8, false, r -> false, "environment/celestial/sun", "environment/sun",
				"environment/celestial/moon/full_moon", "environment/moon_phases");
		add("deepslate", "Deepslate", 0xFF475569, false, r -> r.startsWith("block/") && r.contains("deepslate"),
				"block/deepslate", "block/deepslate_bricks");
		add("stone", "Stone", 0xFF9CA3AF, false,
				r -> r.equals("block/stone") || r.equals("block/cobblestone") || r.equals("block/smooth_stone")
						|| r.equals("block/stone_bricks"),
				"block/stone", "block/cobblestone");
		add("snow", "Snow", 0xFFE2E8F0, false, r -> r.equals("block/snow") || r.equals("block/powder_snow"),
				"block/snow");
		add("grass", "Grass", 0xFF4ADE80, false,
				r -> r.startsWith("block/grass_block") || r.equals("block/short_grass")
						|| r.startsWith("block/tall_grass"),
				"block/grass_block_side", "block/grass_block_top");
		add("shield", "Shield", 0xFF22C55E, false, r -> r.startsWith("entity/shield") || r.equals("item/shield"),
				"entity/shield_base_nopattern", "entity/shield/shield_base_nopattern", "item/shield");
		add("mace", "Mace", 0xFF67E8F9, false, r -> r.startsWith("item/mace"), "item/mace");
		add("trident", "Trident", 0xFF2DD4BF, false, r -> r.contains("trident"), "item/trident",
				"entity/trident/trident", "entity/trident");
		add("windcharge", "Wind Charge", 0xFFA5B4FC, false,
				r -> r.contains("wind_charge") || r.startsWith("particle/gust") || r.startsWith("particle/small_gust")
						|| r.startsWith("entity/breeze/breeze_wind"),
				"item/wind_charge", "entity/projectiles/wind_charge", "particle/gust_0");
		add("xpbottle", "XP Bottle", 0xFF84CC16, false, r -> r.equals("item/experience_bottle"),
				"item/experience_bottle");
		add("cobwebs", "Cobwebs", 0xFFF1F5F9, false, r -> r.contains("cobweb"), "block/cobweb");

		// Cosmetics: keine Texturen aus Packs, sondern eigene Modelle (siehe Cosmetics)
		add("cos_cape", "Capes", 0xFF2563EB, false, r -> false);
		add("cos_hat", "Hats", 0xFFFACC15, false, r -> false);
		add("cos_wings", "Wings", 0xFFE2E8F0, false, r -> false);

		// Effects under Extras (not textures: see KillEffects and Lyrics)
		add("fx_kill", "Kill Effects", 0xFFEF4444, true, r -> false);
		add("fx_lyrics", "Lyrics", 0xFF1DB954, true, r -> false);
		add("fx_armor", "Armor HUD", 0xFF4ADE80, true, r -> false);
		add("fx_spotify", "Spotify Overlay", 0xFF1DB954, true, r -> false);
		add("fx_hit", "Hit Colour", 0xFFF43F5E, true, r -> false);
		add("fx_pop", "Pop Chams", 0xFFFACC15, true, r -> false);
		add("fx_atmo", "Atmosphere", 0xFFA855F7, true, r -> false);

		// Extras: alles, was oben nicht erfasst wurde, nach Ordner
		add("x_block", "Blocks", 0xFF84CC16, true, r -> r.startsWith("block/"), "block/dirt", "block/oak_planks");
		add("x_item", "Items", 0xFFFB923C, true, r -> r.startsWith("item/"), "item/diamond", "item/apple");
		add("x_entity", "Entities", 0xFFF472B6, true, r -> r.startsWith("entity/"));
		add("x_gui", "GUI", 0xFFA78BFA, true, r -> r.startsWith("gui/"));
		add("x_particle", "Particles", 0xFF2DD4BF, true, r -> r.startsWith("particle/"));
		add("x_other", "Other", 0xFF6B7280, true, r -> true);
	}

	/** Cosmetic-Sammlung (Umhang/Hut/Fluegel) oder null. */
	public Cosmetics.Kind cosmetic() {
		return switch (id) {
			case "cos_cape" -> Cosmetics.Kind.CAPE;
			case "cos_hat" -> Cosmetics.Kind.HAT;
			case "cos_wings" -> Cosmetics.Kind.WINGS;
			case "fx_kill" -> Cosmetics.Kind.KILL;
			case "fx_lyrics" -> Cosmetics.Kind.LYRICS;
			case "fx_armor" -> Cosmetics.Kind.ARMOR;
			case "fx_spotify" -> Cosmetics.Kind.SPOTIFY;
			case "fx_hit" -> Cosmetics.Kind.HIT;
			case "fx_pop" -> Cosmetics.Kind.POP;
			case "fx_atmo" -> Cosmetics.Kind.ATMO;
			default -> null;
		};
	}

	private static void add(String id, String label, int color, boolean extra, Predicate<String> m,
			String... previews) {
		ALL.add(new Group(id, label, color, extra, m, List.of(previews)));
	}

	private static boolean endsAny(String s, String... suffixes) {
		for (String x : suffixes) {
			if (s.endsWith(x)) {
				return true;
			}
		}
		return false;
	}

	/** assets/ns/textures/block/stone.png -> block/stone */
	public static String rel(String path) {
		int i = path.indexOf("/textures/");
		String r = i < 0 ? path : path.substring(i + "/textures/".length());
		return r.endsWith(".png") ? r.substring(0, r.length() - 4) : r;
	}

	public static Group byId(String id) {
		for (Group g : ALL) {
			if (g.id.equals(id)) {
				return g;
			}
		}
		return ALL.get(0);
	}

	/** Files of a custom skybox (OptiFine/MCPatcher sky folder, legacy FabricSkyBoxes / Nuit, shared imgs). */
	public static boolean isSkyData(String path) {
		if (!path.startsWith("assets/")) {
			return false;
		}
		boolean type = path.endsWith(".png") || path.endsWith(".json") || path.endsWith(".properties");
		return type && (path.contains("/optifine/sky/") || path.contains("/mcpatcher/sky/")
				|| path.startsWith("assets/fabricskyboxes/") || path.startsWith("assets/nuit/")
				|| path.startsWith("assets/imgs/"));
	}

	public static String classify(String path) {
		if (isSkyData(path)) {
			return "sky";
		}
		String r = rel(path);
		for (Group g : ALL) {
			if (g.match.test(r)) {
				return g.id;
			}
		}
		return "x_other";
	}

	public static List<Group> main() {
		return ALL.stream().filter(g -> !g.extra).toList();
	}

	public static List<Group> extras() {
		return ALL.stream().filter(g -> g.extra).toList();
	}
}
