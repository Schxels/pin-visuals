package com.texturestudio;

/** What the mixins (another package) need to know from the settings. */
public final class Hooks {

	private Hooks() {
	}

	/** Hurt cam on: shake strength 0..1; off: -1 (vanilla). */
	public static float hurtStrength() {
		if (Features.hurtOn == null || !Features.hurtOn.b()) {
			return -1f;
		}
		return Features.hurtStrength.f() / 100f;
	}

	/** Hide the first-person hand right now (while zoomed in)? */
	public static boolean hideHand() {
		return Features.zoomHand != null && Features.zoomHand.b() && Zoom.active();
	}

	/** Own arm swing duration in ticks (vanilla duration given), or the vanilla one. */
	public static int swingDuration(int base, boolean mining) {
		if (Features.swingOn == null || !Features.swingOn.b() || (mining && !Features.swingAll.b())) {
			return base;
		}
		if (Features.swingStyle.i() == 2) {
			return Math.max(base, 6 + Features.swingFatigue.i() * 2);
		}
		return Math.max(1, (int) Math.round(base * 100.0 / Features.swingSpeed.get()));
	}

	/** Swing style "1.7": no dip of the item after a hit. */
	public static boolean oldSwing() {
		return Features.swingOn != null && Features.swingOn.b() && Features.swingStyle.i() == 1;
	}

	/** Butterfly knife trick pose right now (knifeRot, handleA, handleB, holdA), or null. */
	public static float[] butterflyPose() {
		if (!Knives.butterflyTrick()) {
			return null;
		}
		KnifeAnimation.Pose p = KnifeAnimation.at(Knives.inspectTime());
		return new float[] { p.knifeRot(), p.handleA(), p.handleB(), p.holdA(), p.wrist(), p.dx(), p.dy(), p.roll() };
	}

	public static net.minecraft.world.item.ItemStack butterflyPart(String part) {
		return Knives.part(part);
	}

	/** Knife inspect twirl (karambit, bayonet, flip knife). */
	public static void knifeInspect(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.entity.HumanoidArm arm) {
		Knives.inspectTransform(pose, arm);
	}

	/** Freelook state for the mixins. */
	public static boolean freelook() {
		return Freelook.active();
	}

	/** Block break animation hides your own cracks? */
	public static boolean hideCracks() {
		return BlockFx.hideCracks();
	}

	/** Item swap animation on (no vanilla lower-and-raise)? */
	public static boolean smoothSwap() {
		return ItemSwap.on();
	}

	/** Extra x of the hotbar selection while it slides. */
	public static int hotbarGlide() {
		return ItemSwap.hotbarOffset();
	}

	/** Item swap transform of the hand. */
	public static void swapTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.entity.HumanoidArm arm) {
		ItemSwap.transform(pose, arm);
	}

	/** Custom skin of your own player, or null for the normal one. */
	public static net.minecraft.world.entity.player.PlayerSkin customSkin(Object player,
			net.minecraft.world.entity.player.PlayerSkin normal) {
		return Skins.skin(player, normal);
	}

	/** Dura Guard: is the held tool too worn to mine / hit right now? */
	public static boolean duraBlocks(boolean attack) {
		return DuraGuard.blocks(attack);
	}

	/** Item physics on? */
	public static boolean itemPhysics() {
		return Features.physicsOn != null && Features.physicsOn.b();
	}
}
