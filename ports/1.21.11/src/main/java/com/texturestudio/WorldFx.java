package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Things drawn into the world itself (not the HUD): block break / place animations and the aura ring. Everything is
 * plain coloured geometry on a white texture, full bright, double sided.
 */
public final class WorldFx {

	static final int FULL_BRIGHT = 0xF000F0;

	private WorldFx() {
	}

	static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(ctx -> render(ctx.matrices(), (SubmitNodeCollector) ctx.commandQueue()));
	}

	static RenderType glow() {
		return RenderTypes.entityTranslucentEmissive(Cosmetics.WHITE);
	}

	static void render(PoseStack pose, SubmitNodeCollector out) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		try {
			Aura.render(pose, out, cam, pt);
			BlockFx.render(pose, out, cam, pt);
		} catch (RuntimeException e) {
			TextureStudioClient.LOGGER.debug("world fx", e);
		}
	}

	/** Submits a batch of coloured geometry with the current pose. */
	static void draw(PoseStack pose, SubmitNodeCollector out, Painter p) {
		out.submitCustomGeometry(pose, glow(), (pp, vc) -> p.paint(new Quads(pp, vc)));
	}

	interface Painter {
		void paint(Quads q);
	}

	/** Small helper that writes double-sided quads. */
	static final class Quads {
		final PoseStack.Pose pose;
		final VertexConsumer vc;

		Quads(PoseStack.Pose pose, VertexConsumer vc) {
			this.pose = pose;
			this.vc = vc;
		}

		private void v(float x, float y, float z, int c, float nx, float ny, float nz) {
			vc.addVertex(pose, x, y, z).setColor(c).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		}

		/** Quad a-b-c-d with one colour per corner, both sides. */
		void quad(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd) {
			float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			float wx = d[0] - a[0], wy = d[1] - a[1], wz = d[2] - a[2];
			float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
			float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (l < 1e-6f) {
				l = 1f;
			}
			nx /= l;
			ny /= l;
			nz /= l;
			v(a[0], a[1], a[2], ca, nx, ny, nz);
			v(b[0], b[1], b[2], cb, nx, ny, nz);
			v(c[0], c[1], c[2], cc, nx, ny, nz);
			v(d[0], d[1], d[2], cd, nx, ny, nz);
			v(d[0], d[1], d[2], cd, -nx, -ny, -nz);
			v(c[0], c[1], c[2], cc, -nx, -ny, -nz);
			v(b[0], b[1], b[2], cb, -nx, -ny, -nz);
			v(a[0], a[1], a[2], ca, -nx, -ny, -nz);
		}

		void quad(float[] a, float[] b, float[] c, float[] d, int col) {
			quad(a, b, c, d, col, col, col, col);
		}

		/** Flat rectangle in the x/y plane (z = 0). */
		void rect(float x0, float y0, float x1, float y1, int col) {
			quad(new float[] { x0, y0, 0 }, new float[] { x1, y0, 0 }, new float[] { x1, y1, 0 }, new float[] { x0, y1, 0 }, col);
		}

		/** Rectangle with a vertical gradient (bottom colour, top colour). */
		void rectV(float x0, float y0, float x1, float y1, int bottom, int top) {
			quad(new float[] { x0, y0, 0 }, new float[] { x1, y0, 0 }, new float[] { x1, y1, 0 }, new float[] { x0, y1, 0 },
					bottom, bottom, top, top);
		}

		/** Axis-aligned box (all six faces). */
		void box(float x0, float y0, float z0, float x1, float y1, float z1, int col) {
			float[] a = { x0, y0, z0 }, b = { x1, y0, z0 }, c = { x1, y1, z0 }, d = { x0, y1, z0 };
			float[] e = { x0, y0, z1 }, f = { x1, y0, z1 }, g = { x1, y1, z1 }, h = { x0, y1, z1 };
			quad(a, b, c, d, col);
			quad(e, f, g, h, col);
			quad(a, b, f, e, col);
			quad(d, c, g, h, col);
			quad(a, d, h, e, col);
			quad(b, c, g, f, col);
		}
	}

	// ------------------------------------------------------------------ colours

	static int argb(int a, int rgb) {
		return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
	}

	static int mix(int c1, int c2, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = Math.round(((c1 >>> 24) & 255) + (((c2 >>> 24) & 255) - ((c1 >>> 24) & 255)) * t);
		int r = Math.round(((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
		int g = Math.round(((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
		int b = Math.round((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	static int rainbow(float hue) {
		return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.75f, 1f) & 0xFFFFFF);
	}

	static float seconds() {
		return (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000f;
	}
}
