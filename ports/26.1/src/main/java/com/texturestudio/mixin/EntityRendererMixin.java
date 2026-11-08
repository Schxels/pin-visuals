package com.texturestudio.mixin;

import com.texturestudio.Tweaks;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FPS tweak "hide far item drops": dropped items far from the camera are skipped. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void pinvisuals$farItems(Entity entity, Frustum frustum, double camX, double camY, double camZ,
			CallbackInfoReturnable<Boolean> cir) {
		if (Tweaks.hideFarItems && entity instanceof ItemEntity
				&& entity.distanceToSqr(camX, camY, camZ) > Tweaks.FAR_ITEM_DISTANCE * Tweaks.FAR_ITEM_DISTANCE) {
			cir.setReturnValue(false);
		}
	}
}
