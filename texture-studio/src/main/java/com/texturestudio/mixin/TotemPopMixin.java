package com.texturestudio.mixin;

import com.texturestudio.PopChams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity event 35 = "used a totem of undying": shows the pop ghost (see {@link PopChams}).
 * <p>
 * The client handles event 35 directly in the packet listener (particles, sound) and never passes it on to the
 * entity, so it has to be caught here. Injected at the first getEventId() call, i.e. after the switch to the render
 * thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class TotemPopMixin {

	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundEntityEventPacket;getEventId()B", ordinal = 0))
	private void texturestudio$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() != 35) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity e = packet.getEntity(mc.level);
		if (e instanceof Player p) {
			PopChams.onPop(p);
		}
	}
}
