package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Items;

/** Zeichnet Umhang, Hut und Fluegel des eigenen Spielers (in der Welt und in Vorschauen). */
public class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	private static final int FULL_BRIGHT = 0xF000F0;

	private final PlayerCapeModel capeModel;

	public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.capeModel = new PlayerCapeModel(models.bakeLayer(ModelLayers.PLAYER_CAPE));
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot,
			float xRot) {
		PopChams.Ghost ghost = PopChams.ghost(s.id);
		if (ghost != null) {
			// totem pop ghost: the posed player model as a glowing see-through silhouette that fades out
			float f = PopChams.progress(ghost);
			int a = (int) Math.round(0xA0 * (1 - Math.pow(f, 2.5))); // stays clear, fades at the end
			if (a > 2) {
				int c = PopChams.color(Cosmetics.popChams, System.currentTimeMillis());
				float grow = 1f + f * 0.12f;
				pose.pushPose();
				pose.scale(grow, grow, grow);
				out.submitModel(getParentModel(), s, pose, RenderTypes.entityTranslucentEmissive(Cosmetics.WHITE),
						FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (a << 24) | c, null, 0, null);
				pose.popPose();
			}
			return;
		}
		if (s.isInvisible || !Cosmetics.isLocal(s)) {
			return;
		}
		boolean elytra = s.chestEquipment != null && s.chestEquipment.is(Items.ELYTRA);

		String cape = Cosmetics.cape;
		if (!Cosmetics.NONE.equals(cape) && !elytra) {
			pose.pushPose();
			if (s.chestEquipment != null && !s.chestEquipment.isEmpty()) {
				pose.translate(0.0F, -0.053125F, 0.06875F); // wie Vanilla: Umhang ueber der Brustplatte
			}
			out.submitModel(capeModel, s, pose, RenderTypes.entitySolid(Cosmetics.capeTexture(cape)), light,
					OverlayTexture.NO_OVERLAY, s.outlineColor, null);
			pose.popPose();
		}

		String hat = Cosmetics.hat;
		if (!Cosmetics.NONE.equals(hat)) {
			Cosmetics.Hat h = Cosmetics.hat(hat);
			pose.pushPose();
			getParentModel().head.translateAndRotate(pose);
			pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
			if (s.headEquipment != null && !s.headEquipment.isEmpty()) {
				pose.translate(0, -1.2f, 0); // ueber dem Helm
			}
			if (h.floating()) {
				pose.translate(0, (float) Math.sin(s.ageInTicks * 0.08f) * 0.5f, 0);
			}
			int l = h.glow() ? FULL_BRIGHT : light;
			RenderType rt = RenderTypes.entityCutoutNoCull(Cosmetics.WHITE);
			out.submitCustomGeometry(pose, rt, (p, vc) -> {
				for (Cosmetics.Box b : h.boxes()) {
					box(p, vc, b, l);
				}
			});
			pose.popPose();
		}

		String wings = Cosmetics.wings;
		if (!Cosmetics.NONE.equals(wings) && !elytra) {
			Cosmetics.Wing w = Cosmetics.wing(wings);
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
			pose.translate(0, 3.0f, 2.6f);
			float speed = s.isFallFlying ? 0.55f : 0.12f;
			float amp = s.isFallFlying ? 28f : 9f;
			float open = s.isFallFlying ? 72f : s.isCrouching ? 22f : 46f;
			float flap = (float) Math.sin(s.ageInTicks * speed) * amp;
			int l = w.glow() ? FULL_BRIGHT : light;
			RenderType rt = RenderTypes.entityCutout(Cosmetics.WHITE);
			for (int side = -1; side <= 1; side += 2) {
				final int sd = side;
				pose.pushPose();
				pose.translate(side * 0.6f, 0, 0);
				// nach hinten gefaltet; side +1 = linke Koerperseite des Modells (+x)
				pose.mulPose(Axis.YP.rotationDegrees(-side * (90 - open - flap)));
				pose.mulPose(Axis.ZP.rotationDegrees(side * 8f));
				out.submitCustomGeometry(pose, rt, (p, vc) -> wing(p, vc, w, sd, l));
				pose.popPose();
			}
			pose.popPose();
		}
	}

	/** Fluegel als flache Pixelflaeche in der x/y-Ebene (x nach aussen), z = 0. */
	private static void wing(PoseStack.Pose p, VertexConsumer vc, Cosmetics.Wing w, int side, int light) {
		float s = w.cell();
		for (Cosmetics.Layer layer : w.layers()) {
			int[][] px = layer.px();
			float z = layer.z();
			for (int r = 0; r < px.length; r++) {
				for (int c = 0; c < px[r].length; c++) {
					int col = px[r][c];
					if (col == 0) {
						continue;
					}
					float x0 = side * c * s;
					float x1 = side * (c + 1) * s;
					float y0 = (r - w.rootY()) * s;
					float y1 = y0 + s;
					// both sides (one is culled depending on the view); normal pointing up = evenly bright
					quad(p, vc, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, col, light, 0, -1, 0);
					quad(p, vc, x0, y1, z, x1, y1, z, x1, y0, z, x0, y0, z, col, light, 0, -1, 0);
				}
			}
		}
	}

	private static void box(PoseStack.Pose p, VertexConsumer vc, Cosmetics.Box b, int light) {
		float x0 = b.x0();
		float y0 = b.y0();
		float z0 = b.z0();
		float x1 = b.x1();
		float y1 = b.y1();
		float z1 = b.z1();
		int c = b.color();
		// y zeigt im Modell nach unten: y0 ist oben
		quad(p, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c, light, 0, -1, 0); // oben
		quad(p, vc, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, c, light, 0, 1, 0); // unten
		quad(p, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, c, light, 0, 0, -1); // vorne
		quad(p, vc, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, c, light, 0, 0, 1); // hinten
		quad(p, vc, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, c, light, -1, 0, 0); // links
		quad(p, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, c, light, 1, 0, 0); // rechts
	}

	private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by,
			float bz, float cx, float cy, float cz, float dx, float dy, float dz, int color, int light, float nx,
			float ny, float nz) {
		v(p, vc, ax, ay, az, 0, 0, color, light, nx, ny, nz);
		v(p, vc, bx, by, bz, 1, 0, color, light, nx, ny, nz);
		v(p, vc, cx, cy, cz, 1, 1, color, light, nx, ny, nz);
		v(p, vc, dx, dy, dz, 0, 1, color, light, nx, ny, nz);
	}

	private static void v(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float vv,
			int color, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(color).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, nx, ny, nz);
	}
}
