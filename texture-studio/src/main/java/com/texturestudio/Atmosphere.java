package com.texturestudio;

import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/**
 * Atmosphere (Extras): a coloured mood around you. Three parts that work together:
 * <ul>
 * <li>fog: Minecraft's own fog is pulled in to the chosen range and tinted (see mixin/FogRendererMixin),</li>
 * <li>mist and smoke: big soft coloured dust puffs near the ground plus drifting smoke and a style particle
 * (snowflakes, cherry petals, embers ...) inside the range,</li>
 * <li>a faint coloured vignette at the screen edges.</li>
 * </ul>
 * Range (blocks) and strength (%) are set with the sliders in the menu (Tweaks.atmoRange / atmoStrength).
 */
public final class Atmosphere {

	private static final Random RNG = new Random();
	private static final double MIN_DIST = 5.5;
	/** 0..1, fades the whole effect in and out when it is switched. */
	private static float blend;
	private static String lastId = Cosmetics.NONE;
	private static long seed = 1;

	private Atmosphere() {
	}

	public static boolean enabled() {
		return !Cosmetics.NONE.equals(Cosmetics.atmosphere);
	}

	/** Fog colour of a style. */
	public static int fog(String id) {
		return switch (id) {
			case "abyss" -> 0xFF1D4ED8;
			case "ember" -> 0xFFC2410C;
			case "frost" -> 0xFFBFDBFE;
			case "toxic" -> 0xFF4D7C0F;
			case "sakura" -> 0xFFF9A8D4;
			case "void" -> 0xFF140F2E;
			case "firefly" -> 0xFF1F3B2C;
			default -> 0xFF7C3AED; // mystic
		};
	}

	/** Colours of the mist puffs (2) and the menu card (sky top, sky bottom). */
	public static int[] palette(String id) {
		return switch (id) {
			case "abyss" -> new int[] { 0xFF3B82F6, 0xFF22D3EE, 0xFF0B1B4D, 0xFF1D4ED8 };
			case "ember" -> new int[] { 0xFFF97316, 0xFFEF4444, 0xFF2A0A04, 0xFFC2410C };
			case "frost" -> new int[] { 0xFFE0F2FE, 0xFFBAE6FD, 0xFF6A8CB8, 0xFFDBEAFE };
			case "toxic" -> new int[] { 0xFF84CC16, 0xFF22C55E, 0xFF0F1F05, 0xFF4D7C0F };
			case "sakura" -> new int[] { 0xFFF9A8D4, 0xFFFBCFE8, 0xFF8A3F6B, 0xFFF9A8D4 };
			case "void" -> new int[] { 0xFF4C1D95, 0xFF1E1B4B, 0xFF05030C, 0xFF201845 };
			case "firefly" -> new int[] { 0xFFFDE047, 0xFF4ADE80, 0xFF06120C, 0xFF1F3B2C };
			default -> new int[] { 0xFFA855F7, 0xFFE879F9, 0xFF1A0B33, 0xFF7C3AED };
		};
	}

	/** The style's own particle (null = only mist and smoke). */
	private static ParticleOptions accent(String id) {
		return switch (id) {
			case "abyss" -> ParticleTypes.GLOW;
			case "ember" -> ParticleTypes.SMALL_FLAME;
			case "frost" -> ParticleTypes.SNOWFLAKE;
			case "toxic" -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case "sakura" -> ParticleTypes.CHERRY_LEAVES;
			case "void" -> ParticleTypes.REVERSE_PORTAL;
			case "firefly" -> ParticleTypes.FIREFLY;
			default -> ParticleTypes.END_ROD;
		};
	}

	private static ParticleOptions smoke(String id) {
		return switch (id) {
			case "ember", "toxic", "void" -> ParticleTypes.LARGE_SMOKE;
			default -> ParticleTypes.WHITE_SMOKE;
		};
	}

	private static float strength() {
		return Math.max(0.1f, Math.min(1f, Tweaks.atmoStrength / 100f));
	}

	/** Called every client tick: fades in / out and spawns mist, smoke and the style particles. */
	public static void tick(Minecraft mc) {
		boolean on = enabled() && mc.level != null && mc.player != null;
		if (on) {
			lastId = Cosmetics.atmosphere;
		}
		blend = on ? Math.min(1f, blend + 0.04f) : Math.max(0f, blend - 0.04f);
		if (!on || mc.isPaused()) {
			return;
		}
		ClientLevel level = mc.level;
		Player p = mc.player;
		String id = Cosmetics.atmosphere;
		float s = strength();
		float range = Math.max(6, Math.min(40, Tweaks.atmoRange)); // particles stay close, the fog goes further
		int[] pal = palette(id);
		float areaK = (float) Math.sqrt(range / 24f);
		int mist = Math.round((1 + 5 * s) * areaK * blend);
		for (int i = 0; i < mist; i++) {
			double[] pos = spot(p, range, -1.0, 3.0);
			int c = pal[RNG.nextInt(2)];
			level.addParticle(new DustParticleOptions(c & 0xFFFFFF, 1.6f + RNG.nextFloat() * 1.2f), pos[0], pos[1],
					pos[2], wind(), 0.004, wind());
		}
		// tinted swirls rising slowly (coloured "smoke")
		int swirl = Math.round((1 + 2 * s) * areaK * blend);
		for (int i = 0; i < swirl; i++) {
			if (RNG.nextFloat() < 0.6f) {
				double[] pos = spot(p, range, -0.5, 2.0);
				int c = pal[RNG.nextInt(2)];
				level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, alpha(c, 0xB0)), pos[0],
						pos[1], pos[2], 0, 0, 0);
			}
		}
		if (RNG.nextFloat() < 0.35f + 0.5f * s) {
			double[] pos = spot(p, range, 0.0, 4.0);
			level.addParticle(smoke(id), pos[0], pos[1], pos[2], wind(), 0.01, wind());
		}
		ParticleOptions acc = accent(id);
		int accN = Math.round((0.6f + 2.4f * s) * areaK);
		for (int i = 0; i < accN; i++) {
			if (RNG.nextFloat() < 0.7f) {
				boolean falling = acc == ParticleTypes.SNOWFLAKE || acc == ParticleTypes.CHERRY_LEAVES;
				double[] pos = spot(p, range, falling ? 3.0 : -0.5, falling ? 9.0 : 5.0);
				double vy = falling ? -0.03 : acc == ParticleTypes.SMALL_FLAME ? 0.015 : 0.0;
				level.addParticle(acc, pos[0], pos[1], pos[2], wind() * 2, vy, wind() * 2);
			}
		}
	}

	/** Random point in a disc of the given radius around p (denser close to the player), y relative to the feet. */
	private static double[] spot(Player p, float radius, double y0, double y1) {
		double a = RNG.nextDouble() * Math.PI * 2;
		// never right next to the camera: close puffs would cover the screen as big squares
		double r = Math.min(radius, MIN_DIST + (radius - MIN_DIST) * Math.sqrt(RNG.nextDouble()));
		return new double[] { p.getX() + Math.cos(a) * r, p.getY() + y0 + RNG.nextDouble() * (y1 - y0),
				p.getZ() + Math.sin(a) * r };
	}

	private static double wind() {
		return (RNG.nextDouble() - 0.5) * 0.02;
	}

	/** Should the fog be changed for this camera (effect on, camera not in water / lava / powder snow)? */
	public static boolean fogActive(Camera camera) {
		return blend > 0.001f && camera.getFluidInCamera() == FogType.NONE;
	}

	/**
	 * Changes Minecraft's fog for this frame (called by the fog mixin).
	 *
	 * @param v environmental start, environmental end, render distance start, render distance end, sky end, cloud
	 *          end (in blocks), changed in place
	 * @param color fog colour, changed in place
	 */
	public static void applyFog(float[] v, Vector4f color) {
		float s = strength();
		float b = blend;
		float range = Math.max(8, Tweaks.atmoRange);
		v[1] = lerp(v[1], Math.min(v[1], range), b);
		v[0] = lerp(v[0], Math.min(v[0], range * (0.6f - 0.55f * s)), b);
		// the sky and clouds melt into the colour too, a bit further out
		v[4] = lerp(v[4], Math.min(v[4], range * (7f - 4f * s)), b);
		v[5] = lerp(v[5], Math.min(v[5], range * 2.5f), b);
		int c = fog(lastId);
		float k = b * (0.45f + 0.5f * s);
		color.set(lerp(color.x, ((c >> 16) & 255) / 255f, k), lerp(color.y, ((c >> 8) & 255) / 255f, k),
				lerp(color.z, (c & 255) / 255f, k), color.w);
	}

	/** Faint coloured vignette at the screen edges (HUD, below everything else). */
	public static void extractHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (blend <= 0.01f || Minecraft.getInstance().player == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		int c = palette(lastId)[3] & 0xFFFFFF;
		int a = Math.round(0x70 * strength() * blend);
		if (a < 3) {
			return;
		}
		int band = h / 3;
		FillBatch.flush(g).fillGradient(0, 0, w, band, (a << 24) | c, c);
		FillBatch.flush(g).fillGradient(0, h - band, w, h, c, (a << 24) | c);
		int side = w / 6;
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			int al = Math.round(a * (1f - i / (float) steps) * (1f - i / (float) steps));
			if (al <= 0) {
				continue;
			}
			int x0 = side * i / steps;
			int x1 = side * (i + 1) / steps;
			FillBatch.fill(g, x0, 0, x1, h, (al << 24) | c);
			FillBatch.fill(g, w - x1, 0, w - x0, h, (al << 24) | c);
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int alpha(int c, int a) {
		return (c & 0xFFFFFF) | (a << 24);
	}
}
