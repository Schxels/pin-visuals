package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Player outline: a coloured line around players that is hidden by blocks (no wallhack). Width, colour (picked,
 * rainbow, team, health), mode (outline, outline + fill, glow), distance and who gets one come from the settings
 * (see {@link Features}).
 * <p>
 * How: the player model is drawn 8 more times in the flat outline colour, each copy moved a little sideways (seen
 * from the camera) and pushed a bit away from the camera. Pushing away is done by scaling around the camera, so the
 * copies look exactly as big as the player but lie behind him: the real player covers them, only the rim around
 * him stays visible, and blocks in front hide the copies like any other geometry.
 */
public final class PlayerOutline {

	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int COPIES = 8;
	/** How far (blocks) the copies are pushed behind the player. */
	private static final float PUSH = 0.24f;

	private PlayerOutline() {
	}

	public static boolean enabled() {
		return !Cosmetics.NONE.equals(Cosmetics.outline);
	}

	/** Outline colour (RGB) of a style at the given time. */
	public static int color(String id, long ms) {
		return switch (id) {
			case "blue" -> 0x2F7BFF;
			case "purple" -> 0xA855F7;
			case "pink" -> 0xFF4FC3;
			case "red" -> 0xEF4444;
			case "green" -> 0x33E05A;
			case "gold" -> 0xFFB800;
			case "white" -> 0xFFFFFF;
			case "rainbow" -> HitColor.hsv((ms % 2400L) / 2400f) & 0xFFFFFF;
			default -> 0x22D3EE; // cyan
		};
	}

	private static volatile java.util.Set<String> friends = java.util.Set.of();

	/** The friends list (setting "Friends": names separated by commas) changed. */
	static void friendsChanged() {
		java.util.Set<String> set = new java.util.HashSet<>();
		if (Features.outFriends != null) {
			for (String n : Features.outFriends.text().split("[,;\\s]+")) {
				if (!n.isBlank()) {
					set.add(n.trim().toLowerCase(java.util.Locale.ROOT));
				}
			}
		}
		friends = set;
	}

	/** Green (full health) over yellow to red. */
	private static int healthColor(float f) {
		f = Math.max(0f, Math.min(1f, f));
		return f > 0.5f ? Cosmetics.lerp(0xFFFACC15, 0xFF33E05A, (f - 0.5f) * 2f)
				: Cosmetics.lerp(0xFFEF4444, 0xFFFACC15, f * 2f);
	}

	/** Called from {@link CosmeticLayer} for every player; local = this is yourself. */
	public static void submit(PlayerModel model, PoseStack pose, SubmitNodeCollector out, AvatarRenderState s,
			boolean local) {
		if (!enabled() || s.isInvisible) {
			return;
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (local) {
			if (!Features.outSelf.b() || mc.options.getCameraType().isFirstPerson()) {
				return;
			}
		}
		net.minecraft.world.entity.Entity ent = mc.level == null ? null : mc.level.getEntity(s.id);
		boolean friend = false;
		if (!local && ent != null && !friends.isEmpty()) {
			friend = friends.contains(ent.getName().getString().toLowerCase(java.util.Locale.ROOT));
		}
		int friendMode = Features.outFriendMode.i();
		if (!local) {
			if (friend && friendMode == 2) {
				return;
			}
			if (!Features.outOthers.b() && !(friend && friendMode == 1)) {
				return;
			}
		}
		Matrix4f m0 = pose.last().pose();
		// middle of the body (model y points down, origin at the neck)
		Vector3f p = m0.transformPosition(new Vector3f(0, 0.45f, 0));
		float dist = p.length();
		if (dist < 0.8f || dist > Features.outRange.f()) {
			return;
		}
		// colour: friend colour, picked colour, rainbow, team or health
		int color;
		if (friend && friendMode == 1) {
			color = Features.outFriendColor.color();
		} else {
			switch (Features.outColorMode.i()) {
				case 1 -> color = 0xFF000000 | Features.rainbow(Features.outSpeed.get(), 2400L);
				case 2 -> color = ent != null ? 0xFF000000 | ent.getTeamColor() : Features.outColor.color();
				case 3 -> color = ent instanceof net.minecraft.world.entity.LivingEntity le
						? healthColor(le.getHealth() / Math.max(1f, le.getMaxHealth()))
						: Features.outColor.color();
				default -> color = Features.outColor.color();
			}
		}
		if ((color >>> 24) < 8) {
			return;
		}
		Vector3f dir = new Vector3f(p).div(dist);
		Vector3f right = new Vector3f(dir).cross(0, 1, 0);
		if (right.lengthSquared() < 1.0e-4f) {
			right.set(1, 0, 0);
		}
		right.normalize();
		Vector3f up = new Vector3f(right).cross(dir).normalize();
		// thick enough to show past armor up close, about the same on screen further away
		float width = (0.075f + dist * 0.0026f) * Features.outWidth.f();
		int mode = Features.outMode.i();
		if (mode == 2) {
			// glow: a wider, faint ring underneath the line
			int soft = ((color >>> 24) * 70 / 255) << 24 | (color & 0xFFFFFF);
			ring(model, pose, out, s, right, up, width * 2.4f, (dist + PUSH * 1.5f) / dist, soft);
		}
		ring(model, pose, out, s, right, up, width, (dist + PUSH) / dist, color);
		if (mode == 1) {
			// fill: the model once more on top of the player, see-through
			int a = Math.round((color >>> 24) * Features.outFill.f() / 100f);
			out.submitModel(model, s, pose, RenderTypes.entityTranslucentEmissive(Cosmetics.WHITE), FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, (a << 24) | (color & 0xFFFFFF), null, 0);
		}
	}

	/** The model drawn COPIES times in a flat colour, each moved sideways (seen from the camera) and pushed back. */
	private static void ring(PlayerModel model, PoseStack pose, SubmitNodeCollector out, AvatarRenderState s,
			Vector3f right, Vector3f up, float width, float scale, int color) {
		for (int i = 0; i < COPIES; i++) {
			double a = i * Math.PI * 2 / COPIES;
			float ox = (float) (right.x * Math.cos(a) + up.x * Math.sin(a)) * width;
			float oy = (float) (right.y * Math.cos(a) + up.y * Math.sin(a)) * width;
			float oz = (float) (right.z * Math.cos(a) + up.z * Math.sin(a)) * width;
			pose.pushPose();
			Matrix4f m = pose.last().pose();
			m.translateLocal(ox, oy, oz);
			m.scaleLocal(scale);
			out.submitModel(model, s, pose, RenderTypes.entityTranslucentEmissive(Cosmetics.WHITE), FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, color, null, 0);
			pose.popPose();
		}
	}
}
