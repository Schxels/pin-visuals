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

	public static volatile double FAR_ITEM_DISTANCE = 16;

	/** Spotify lyrics drawn over blocks (true) or hidden where blocks cover the sky (false). */
	public static volatile boolean lyricsThroughBlocks = true;
	public static volatile boolean spotifyRight = false;
	public static volatile float popSeconds = 1.3f;
	public static volatile int atmoRange = 32;
	public static volatile int atmoStrength = 60;

	/** How far the fire overlay is moved down (screen units, the overlay is about 1 high). */
	public static volatile float LOW_FIRE_OFFSET = -0.3f;
	/** How far the shield is moved down in first person. */
	public static volatile float LOW_SHIELD_OFFSET = -0.25f;

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
		crossOn = c.crossOn;
		crossShape = Math.max(0, Math.min(5, c.crossShape));
		crossColor = c.crossColor;
		crossGap = Math.max(0, Math.min(10, c.crossGap));
		crossLen = Math.max(1, Math.min(12, c.crossLen));
		crossThick = Math.max(1, Math.min(4, c.crossThick));
		crossDot = c.crossDot;
		crossOutline = c.crossOutline;
		blockOutlineOn = c.blockOutlineOn;
		blockOutlineColor = c.blockOutlineColor;
		blockOutlineRainbow = c.blockOutlineRainbow;
		blockOutlineWidth = Math.max(1, Math.min(10, c.blockOutlineWidth));
		blockOutlineGlow = Math.max(0, Math.min(100, c.blockOutlineGlow));
		vmOn = c.vmOn;
		vmX = Math.max(-100, Math.min(100, c.vmX));
		vmY = Math.max(-100, Math.min(100, c.vmY));
		vmZ = Math.max(-100, Math.min(100, c.vmZ));
		vmRotX = Math.max(-180, Math.min(180, c.vmRotX));
		vmRotY = Math.max(-180, Math.min(180, c.vmRotY));
		vmRotZ = Math.max(-180, Math.min(180, c.vmRotZ));
		vmScale = Math.max(30, Math.min(200, c.vmScale));
		pinMenu = c.pinMenu;
	}

	// Studio settings (mirrored from MixConfig, see there)
	public static volatile boolean crossOn;
	public static volatile int crossShape;
	public static volatile int crossColor = 0xFFFFFFFF;
	public static volatile int crossGap = 2;
	public static volatile int crossLen = 4;
	public static volatile int crossThick = 1;
	public static volatile boolean crossDot;
	public static volatile boolean crossOutline = true;
	public static volatile boolean blockOutlineOn;
	public static volatile int blockOutlineColor = 0xFF22D3EE;
	public static volatile boolean blockOutlineRainbow;
	public static volatile int blockOutlineWidth = 4;
	public static volatile int blockOutlineGlow = 40;
	public static volatile boolean vmOn;
	public static volatile int vmX;
	public static volatile int vmY;
	public static volatile int vmZ;
	public static volatile int vmRotX;
	public static volatile int vmRotY;
	public static volatile int vmRotZ;
	public static volatile int vmScale = 100;
	public static volatile boolean pinMenu;

	/** Block outline colour now (ARGB), rainbow moves through the hues. */
	public static int blockOutlineColorNow() {
		if (blockOutlineRainbow) {
			return 0xFF000000 | Features.rainbow(Features.blockSpeed == null ? 1 : Features.blockSpeed.get(), 3000L);
		}
		return blockOutlineColor;
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
