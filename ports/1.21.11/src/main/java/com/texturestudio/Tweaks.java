package com.texturestudio;

/** Small gameplay-visual tweaks (read by the mixins every frame, so kept as static fields). */
public final class Tweaks {

	/** Hide the big totem that pops up on screen when a totem of undying is used. */
	public static volatile boolean noTotemPop;
	/** Hide the green/yellow totem particles around the player. */
	public static volatile boolean noTotemParticles;
	/** Draw the burning overlay lower on screen (you can see more while on fire). */
	public static volatile boolean lowFire;
	/** Hold the shield lower in first person. */
	public static volatile boolean lowShield;

	// FPS tweaks (Tweaks -> FPS): things that cost frames in crystal PvP and that Sodium & co. do not touch
	/** No explosion smoke/flash particles (crystals, anchors, TNT). */
	public static volatile boolean noExplosionParticles;
	/** No crit, enchanted-hit, sweep and damage-number particles. */
	public static volatile boolean fewerHitParticles;
	/** End crystals stand still (no spinning, no bouncing). */
	public static volatile boolean calmCrystals;
	/** Dropped items further away than {@link #FAR_ITEM_DISTANCE} blocks are not drawn. */
	public static volatile boolean hideFarItems;

	public static final double FAR_ITEM_DISTANCE = 16;

	/** Spotify lyrics drawn over blocks (true) or hidden where blocks cover the sky (false). */
	public static volatile boolean lyricsThroughBlocks = true;
	public static volatile boolean spotifyRight = false;
	public static volatile float popSeconds = 1.3f;
	public static volatile int atmoRange = 32;
	public static volatile int atmoStrength = 60;

	/** How far the fire overlay is moved down (screen units, the overlay is about 1 high). */
	public static final float LOW_FIRE_OFFSET = -0.3f;
	/** How far the shield is moved down in first person. */
	public static final float LOW_SHIELD_OFFSET = -0.25f;

	private Tweaks() {
	}

	public static void load(MixConfig c) {
		noTotemPop = c.noTotemPop;
		noTotemParticles = c.noTotemParticles;
		lowFire = c.lowFire;
		lowShield = c.lowShield;
		noExplosionParticles = c.noExplosionParticles;
		fewerHitParticles = c.fewerHitParticles;
		calmCrystals = c.calmCrystals;
		hideFarItems = c.hideFarItems;
		lyricsThroughBlocks = c.lyricsThroughBlocks;
		spotifyRight = c.spotifyRight;
		popSeconds = Math.max(0.5f, Math.min(5f, c.popSeconds));
		atmoRange = Math.max(8, Math.min(96, c.atmoRange));
		atmoStrength = Math.max(10, Math.min(100, c.atmoStrength));
	}

	/** Are all FPS tweaks on? */
	public static boolean fpsBoost(MixConfig c) {
		return c.noExplosionParticles && c.fewerHitParticles && c.calmCrystals && c.hideFarItems && c.noTotemParticles;
	}

	/** "FPS Boost": all FPS tweaks on (or all off again when they already are). */
	public static void setFpsBoost(MixConfig c, boolean on) {
		c.noExplosionParticles = on;
		c.fewerHitParticles = on;
		c.calmCrystals = on;
		c.hideFarItems = on;
		c.noTotemParticles = on;
		c.save();
		load(c);
	}
}
