package com.texturestudio.mixin;

import com.texturestudio.Knives;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Knife: your held sword gets the knife's item model (see {@link Knives#swap}). */
@Mixin(ItemModelResolver.class)
public abstract class KnifeModelMixin {

	@ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ItemStack pinvisuals$knife(ItemStack stack, ItemStackRenderState state, ItemStack same,
			ItemDisplayContext ctx, Level level, ItemOwner owner, int seed) {
		return Knives.swap(stack, ctx, owner);
	}
}
