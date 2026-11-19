package com.texturestudio;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

/**
 * Knife (Player): your sword is drawn as a CS-style knife (butterfly, karambit, bayonet, flip knife) in one of six
 * finishes. Only the look changes and only on your screen: the item itself stays a sword. The inspect key plays a
 * trick (the butterfly knife flips open like in the Butterfly Knife mod, the others twirl).
 */
public final class Knives {

	static final String[] MODELS = { "butterfly", "karambit", "bayonet", "flip" };
	static final String[] MODEL_NAMES = { "Butterfly", "Karambit", "Bayonet", "Flip Knife" };
	static final String[] FINISHES = { "steel", "fade", "doppler", "crimson", "gold", "night" };
	static final String[] FINISH_NAMES = { "Steel", "Fade", "Doppler", "Crimson Web", "Gold", "Night" };

	static KeyMapping inspectKey;
	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static long inspectStart = -1;
	private static float lastTick = -1;

	private Knives() {
	}

	private static boolean on() {
		return Features.knifeOn != null && Features.knifeOn.b();
	}

	/** Is this stack drawn as a knife? */
	static boolean replaces(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !on()) {
			return false;
		}
		return switch (Features.knifeAll.i()) {
			case 1 -> stack.is(Items.DIAMOND_SWORD) || stack.is(Items.NETHERITE_SWORD);
			case 2 -> stack.is(Items.NETHERITE_SWORD);
			default -> stack.is(ItemTags.SWORDS);
		};
	}

	private static String finish() {
		return FINISHES[Math.max(0, Math.min(FINISHES.length - 1, Features.knifeFinish.i()))];
	}

	static String model() {
		return MODELS[Math.max(0, Math.min(MODELS.length - 1, Features.knifeModel.i()))];
	}

	/** A client-only stick that only carries the knife's item model (and the glint of the real sword). */
	static ItemStack fake(String model, boolean foil) {
		String key = model + (foil ? "+" : "");
		return STACKS.computeIfAbsent(key, k -> {
			ItemStack s = new ItemStack(Items.STICK);
			s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(TextureStudioClient.MOD_ID, "knife/" + model));
			if (foil) {
				s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return s;
		});
	}

	/** The stack to draw instead of the held one (called for every item model lookup of your own hands). */
	public static ItemStack swap(ItemStack stack, ItemDisplayContext ctx, Object owner) {
		Minecraft mc = Minecraft.getInstance();
		if (owner != mc.player || !replaces(stack)) {
			return stack;
		}
		boolean first = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		boolean third = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		if (!first && !(third && Features.knifeThird.b())) {
			return stack;
		}
		return fake(model() + "_" + finish(), stack.hasFoil());
	}

	/** Part of the butterfly knife for the trick animation. */
	static ItemStack part(String part) {
		return fake("bfly_" + part + "_" + finish(), false);
	}

	static void tick(Minecraft mc) {
		if (inspectKey == null) {
			return;
		}
		while (inspectKey.consumeClick()) {
			if (on() && Features.knifeInspect.b() && mc.player != null && replaces(mc.player.getMainHandItem())) {
				inspectStart = System.nanoTime();
			}
		}
		if (mc.player == null || !replaces(mc.player.getMainHandItem())) {
			inspectStart = -1;
		}
		// quiet metal click whenever the butterfly latch snaps
		float now = mc.player != null && "butterfly".equals(model()) && System.nanoTime() - trickSeen < 200_000_000L
				? inspectTime() : -1f;
		if (now >= 0 && lastTick >= 0) {
			for (float c : KnifeAnimation.CLICKS) {
				if (lastTick < c && now >= c) {
					mc.player.playSound(net.minecraft.sounds.SoundEvents.TRIPWIRE_CLICK_ON, 0.35f, 1.7f);
				}
			}
		}
		lastTick = now;
	}

	/** Seconds since the inspect key was pressed, or -1 when no trick is playing. */
	static float inspectTime() {
		if (inspectStart < 0) {
			return -1f;
		}
		float t = (System.nanoTime() - inspectStart) / 1_000_000_000f;
		float len = "butterfly".equals(model()) && System.nanoTime() - trickSeen < 200_000_000L ? KnifeAnimation.LENGTH
				: KnifeInspect.length(model());
		if (t > len) {
			inspectStart = -1;
			return -1f;
		}
		return t;
	}

	/** CS-style inspect of the karambit / bayonet / flip knife (first person, main hand), see KnifeInspect. */
	public static void inspectTransform(PoseStack pose, HumanoidArm arm) {
		Minecraft mc = Minecraft.getInstance();
		boolean trickDrawn = System.nanoTime() - trickSeen < 200_000_000L;
		if (mc.player == null || arm != mc.player.getMainArm() || ("butterfly".equals(model()) && trickDrawn)) {
			return;
		}
		float t = inspectTime();
		if (t < 0) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float[] k = KnifeInspect.sample(model(), t);
		pose.translate(side * k[0], k[1], k[2]);
		pose.translate(side * k[6], k[7], k[8]);
		pose.rotate(new Quaternionf().rotateY((float) Math.toRadians(side * k[4])));
		pose.rotate(new Quaternionf().rotateX((float) Math.toRadians(k[3])));
		pose.rotate(new Quaternionf().rotateZ((float) Math.toRadians(side * k[5])));
		pose.translate(-side * k[6], -k[7], -k[8]);
	}

	/** Butterfly trick running right now (first person)? */
	static boolean butterflyTrick() {
		boolean on = "butterfly".equals(model()) && inspectTime() >= 0;
		if (on) {
			trickSeen = System.nanoTime(); // the trick renderer is there (not in every Minecraft version)
		}
		return on;
	}

	private static long trickSeen;
}
