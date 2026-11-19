package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Aura (Player): an animated ring that floats around you (gently bobbing and tilting, with little orbs circling
 * along) or lies on the ground under you. Style, colour, radius, height and rotation are settings.
 */
final class Aura {

	static final String[] STYLES = { "Solid", "Dashed", "Double", "Runes" };
	static final String[] PLACES = { "Floating around you", "On the ground" };

	private Aura() {
	}

	static boolean on() {
		return Features.auraOn != null && Features.auraOn.b();
	}

	static void render(PoseStack pose, SubmitNodeCollector out, Vec3 cam, float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (!on() || mc.player.isSpectator()) {
			return;
		}
		if (mc.options.getCameraType().isFirstPerson() && !Features.auraFirst.b()) {
			return;
		}
		Vec3 p = mc.player.getPosition(pt);
		boolean floating = Features.auraPlace.i() == 0;
		float r = Features.auraRadius.f();
		float t = WorldFx.seconds();
		float rot = (float) Math.toRadians(t * 90f * Features.auraSpeed.f() / 100f);
		float glow = Features.auraGlow.f() / 100f;
		int style = Features.auraStyle.i();
		boolean rainbow = Features.auraRainbow.b();
		int base = Features.auraColor.i();
		float pulse = 0.85f + 0.15f * (float) Math.sin(t * 3.0);

		pose.pushPose();
		if (floating) {
			float h = Features.auraHeight.f() + 0.08f * (float) Math.sin(t * 1.7);
			pose.translate(p.x - cam.x, p.y - cam.y + h, p.z - cam.z);
			// a slow wobble, like a ring hanging in the air
			pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(9 * Math.sin(t * 0.9))));
			pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(7 * Math.cos(t * 0.7))));
		} else {
			pose.translate(p.x - cam.x, p.y - cam.y + 0.04, p.z - cam.z);
		}
		WorldFx.draw(pose, out, q -> {
			int n = 96;
			for (int i = 0; i < n; i++) {
				float a0 = (float) (i * Math.PI * 2 / n);
				float a1 = (float) ((i + 1) * Math.PI * 2 / n);
				int c = rainbow ? WorldFx.rainbow(i / (float) n + t * 0.15f) : base;
				boolean dash = ((int) Math.floor(((a0 + rot) / (Math.PI * 2)) * 24)) % 2 == 0;
				if (floating) {
					float w = 0.05f;
					if (glow > 0) {
						// soft light above and below the band
						ribbon(q, a0, a1, r, w / 2, w / 2 + 0.16f, WorldFx.argb((int) (120 * glow * pulse), c), WorldFx.argb(0, c));
						ribbon(q, a0, a1, r, -w / 2, -w / 2 - 0.16f, WorldFx.argb((int) (120 * glow * pulse), c), WorldFx.argb(0, c));
					}
					switch (style) {
						case 1 -> {
							if (dash) {
								ribbon(q, a0, a1, r, -w / 2, w / 2, WorldFx.argb(240, c), WorldFx.argb(240, c));
							}
						}
						case 2 -> {
							ribbon(q, a0, a1, r, w * 0.2f, w * 0.9f, WorldFx.argb(240, c), WorldFx.argb(240, c));
							boolean d2 = ((int) Math.floor(((a0 - rot * 1.6) / (Math.PI * 2)) * 16)) % 2 == 0;
							if (d2) {
								ribbon(q, a0, a1, r * 1.08f, -w * 1.4f, -w * 0.7f, WorldFx.argb(220, c), WorldFx.argb(220, c));
							}
						}
						default -> ribbon(q, a0, a1, r, -w / 2, w / 2, WorldFx.argb(240, c), WorldFx.argb(240, c));
					}
				} else {
					if (glow > 0) {
						band(q, a0, a1, r * 0.55f, r - 0.06f, WorldFx.argb(0, c), WorldFx.argb((int) (90 * glow * pulse), c));
						band(q, a0, a1, r, r + 0.22f * r, WorldFx.argb((int) (110 * glow * pulse), c), WorldFx.argb(0, c));
					}
					switch (style) {
						case 1 -> {
							if (dash) {
								band(q, a0, a1, r - 0.07f, r, WorldFx.argb(235, c), WorldFx.argb(235, c));
							}
						}
						case 2 -> {
							band(q, a0, a1, r - 0.045f, r, WorldFx.argb(235, c), WorldFx.argb(235, c));
							boolean d2 = ((int) Math.floor(((a0 - rot * 1.6) / (Math.PI * 2)) * 16)) % 2 == 0;
							if (d2) {
								band(q, a0, a1, r * 0.72f - 0.05f, r * 0.72f, WorldFx.argb(220, c), WorldFx.argb(220, c));
							}
						}
						default -> band(q, a0, a1, r - 0.06f, r, WorldFx.argb(235, c), WorldFx.argb(235, c));
					}
				}
			}
			if (style == 3) {
				// runes: eight small diamonds turning around the ring
				for (int k = 0; k < 8; k++) {
					float a = rot + (float) (k * Math.PI / 4);
					int c = rainbow ? WorldFx.rainbow(k / 8f + t * 0.15f) : base;
					float s = 0.09f * Math.min(1.2f, r) * (0.9f + 0.25f * (float) Math.sin(t * 4 + k));
					float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
					if (floating) {
						// standing diamonds facing outwards, sitting on the band
						float rr = r + 0.01f;
						float tx = -cz * s * 0.6f, tz = cx * s * 0.6f;
						q.quad(new float[] { cx * rr, -s, cz * rr }, new float[] { cx * rr + tx, 0, cz * rr + tz },
								new float[] { cx * rr, s, cz * rr }, new float[] { cx * rr - tx, 0, cz * rr - tz }, WorldFx.argb(245, c));
					} else {
						float px = cx * r * 0.89f, pz = cz * r * 0.89f;
						float ux = cx * s, uz = cz * s;
						float vx = -uz * 0.6f, vz = ux * 0.6f;
						q.quad(new float[] { px - ux, 0.001f, pz - uz }, new float[] { px + vx, 0.001f, pz + vz },
								new float[] { px + ux, 0.001f, pz + uz }, new float[] { px - vx, 0.001f, pz - vz }, WorldFx.argb(245, c));
					}
				}
			}
			if (floating) {
				// little glowing orbs circling along the ring at different heights
				for (int k = 0; k < 6; k++) {
					float a = -rot * 1.3f + (float) (k * Math.PI / 3);
					float rr = r * (1.12f + 0.05f * (float) Math.sin(t * 2 + k));
					float y = 0.22f * (float) Math.sin(t * 1.8 + k * 1.7);
					float s = 0.035f;
					int c = rainbow ? WorldFx.rainbow(k / 6f + t * 0.2f) : base;
					float ox = (float) Math.cos(a) * rr, oz = (float) Math.sin(a) * rr;
					q.box(ox - s, y - s, oz - s, ox + s, y + s, oz + s, WorldFx.argb(250, WorldFx.mix(c, 0xFFFFFFFF, 0.35f)));
					if (glow > 0) {
						float g = s * 2.6f;
						q.box(ox - g, y - g, oz - g, ox + g, y + g, oz + g, WorldFx.argb((int) (70 * glow), c));
					}
				}
			}
		});
		pose.popPose();
	}

	/** Part of a flat ring between radius r0 and r1, colour c0 inside and c1 outside. */
	private static void band(WorldFx.Quads q, float a0, float a1, float r0, float r1, int c0, int c1) {
		float c0x = (float) Math.cos(a0), c0z = (float) Math.sin(a0);
		float c1x = (float) Math.cos(a1), c1z = (float) Math.sin(a1);
		q.quad(new float[] { c0x * r0, 0, c0z * r0 }, new float[] { c1x * r0, 0, c1z * r0 },
				new float[] { c1x * r1, 0, c1z * r1 }, new float[] { c0x * r1, 0, c0z * r1 }, c0, c0, c1, c1);
	}

	/** Part of a standing ribbon at radius r from height y0 (colour c0) to y1 (colour c1). */
	private static void ribbon(WorldFx.Quads q, float a0, float a1, float r, float y0, float y1, int c0, int c1) {
		float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
		float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
		q.quad(new float[] { x0, y0, z0 }, new float[] { x1, y0, z1 }, new float[] { x1, y1, z1 }, new float[] { x0, y1, z0 },
				c0, c0, c1, c1);
	}
}
