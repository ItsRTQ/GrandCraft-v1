package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.MeleeDamage;
import com.hrtq.grandcraft.skill.Acrobat;
import com.hrtq.grandcraft.skill.CombatMaster;
import com.hrtq.grandcraft.skill.SkillMilestones;
import com.hrtq.grandcraft.skill.SkillObjective;
import com.hrtq.grandcraft.stats.WeaponScaling;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla's melee damage calculation with GrandCraft's.
 *
 * <p>Six injections, and they split cleanly into two kinds. Three <em>substitute</em>
 * our number for vanilla's and are gated on the player being on the weapon path; three
 * <em>delete</em> vanilla mechanics outright and are deliberately not gated on
 * anything, because a removal that a config switch can undo is not a removal.
 *
 * <h2>What is deleted, and why here</h2>
 * <ul>
 *   <li><strong>The attack-cooldown damage curve.</strong> In 26.2 this lives in its
 *       own method, {@code baseDamageScaleFactor}, which is a far better seam than the
 *       {@code ATTACK_SPEED} modifier this was once planned as: that route would have
 *       pinned {@code fullyCharged} true and fired sweep attacks on nearly every
 *       walking sword swing. Removing it also closes a defect the mod has carried since
 *       the attack indicator was rebuilt — the client showed our lockout while the
 *       server still scaled damage by vanilla's, so an early swing quietly dealt as
 *       little as a fifth of its damage with nothing on screen to say so.</li>
 *   <li><strong>Crit on fall.</strong> A 1.5x multiplier for being airborne is exactly
 *       the "damage number that changes outcomes without changing decisions" the design
 *       rules out, and it fights the rule that leaving the ground is a commitment.</li>
 *   <li><strong>Sweep attacks.</strong> Free damage to everything nearby for holding
 *       still, on a system built around committing to one target at a time.</li>
 * </ul>
 *
 * <p>All three were already on the vanilla-removal list. Note {@code baseDamageScaleFactor}
 * is also reached from {@code Player.stabAttack}, the separate spear path, which is
 * therefore de-scaled too — consistent, though nothing stat-gates that path yet.
 *
 * <h2>Why redirects rather than one big overwrite</h2>
 * Each of the three redirected calls appears exactly once in {@code attack} (verified
 * against the 26.2 bytecode at offsets 27, 48 and 60), which is what makes a redirect
 * unambiguous. Reimplementing {@code attack} wholesale would mean owning knockback,
 * sound, durability, statistics, food exhaustion and the projectile-deflection path
 * forever, and re-checking every one of them each Minecraft update. This way vanilla
 * keeps doing all the parts we have no opinion about.
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

	/**
	 * Vanilla's enchantment maths, so the redirect below can ask what it would have
	 * said before deciding what to do with the answer.
	 *
	 * <p>Declared on {@code Player} itself, so the shadow resolves — an inherited member
	 * would not.
	 */
	@Shadow
	protected abstract float getEnchantedDamage(Entity target, float damage, DamageSource source);

	/**
	 * The whole substitution: what would have been the weapon's damage becomes the
	 * character's.
	 *
	 * <p>Redirecting the attribute read rather than the finished value is what keeps
	 * everything downstream — the {@code damage > 0} test, the enchantment term, the
	 * damage event — working on a number of the same kind it always was.
	 */
	@Redirect(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;"
							+ "getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double grandcraft$statScaledDamage(Player self, Holder<Attribute> attribute) {
		double vanilla = self.getAttributeValue(attribute);

		if (!MeleeDamage.appliesTo(self)) {
			return vanilla;
		}

		WeaponScaling scaling = MeleeDamage.forSwing(self, self.getMainHandItem());
		MeleeDamage.reportSwing(self, scaling);

		// A swing that reached a target, counted for the skill-line milestones. Safe
		// here for the reason the guard above exists: appliesTo is false on the client,
		// so this cannot run on the prediction pass and cannot double-count.
		//
		// Deliberately not inside reportSwing, which looks like the seam and is not —
		// it returns early when the weapon requirement is met, so it only ever sees
		// penalised swings.
		if (self instanceof ServerPlayer player) {
			SkillMilestones.count(player, SkillObjective.STRIKE, 1);
		}

		// The Warrior's Combat Master window, spent here: this is a swing that reached a
		// target, which is exactly what the user specified closes it — landing damage is
		// not required. Reading and consuming are one call, so a swing cannot be
		// empowered without spending the window or spend it without being empowered.
		//
		// Applied to the character's damage rather than to the finished figure, so it
		// rides through the enchantment term and the damage event as a number of the
		// same kind those already expect. It also reaches the defender's guard: a block
		// costs stamina in proportion to the damage absorbed, so an empowered blow
		// drains a raised guard half again as hard.
		// The Outlaw's Acrobat, on the same term and for the same reasons: a swing thrown
		// while hanging off a wall is worth less, and because the multiplier goes here it
		// also drains a defender's guard proportionally less. Exactly 1.0 for anyone not
		// on a wall, so the ordinary swing is arithmetically untouched.
		return scaling.damage() * CombatMaster.empowerSwing(self) * Acrobat.swingScale(self);
	}

	/**
	 * Neutralises the charge term in the enchantment bonus.
	 *
	 * <p>Vanilla weights the enchantment contribution by how charged the swing was,
	 * which is the same cooldown curve killed below wearing a different hat. Left in, an
	 * enchantment would still be worth less on an early swing than on a late one, and
	 * the pacing would go on living in a number nothing else reads any more.
	 *
	 * <p>Returning 1.0 also pins {@code fullyCharged} true inside {@code attack}. That
	 * is harmless precisely <em>because</em> of the three deletions: everything it gates
	 * is either gone (crit, sweep) or wanted unconditionally (the sprint knockback
	 * sound).
	 */
	@Redirect(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;getAttackStrengthScale(F)F"))
	private float grandcraft$fullCharge(Player self, float partialTick) {
		return MeleeDamage.appliesTo(self) ? 1.0F : self.getAttackStrengthScale(partialTick);
	}

	/**
	 * Brings enchantments onto the same scale as the weapon they are on.
	 *
	 * <p>Vanilla reads this as {@code enchanted - damage}, so returning
	 * {@code damage + adjustedBonus} hands it exactly the bonus we want it to see while
	 * leaving the arithmetic around it untouched.
	 *
	 * <p>The shadowed call below is a different call site from the one being redirected,
	 * so it reaches the real method rather than recursing.
	 */
	@Redirect(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;getEnchantedDamage("
							+ "Lnet/minecraft/world/entity/Entity;F"
							+ "Lnet/minecraft/world/damagesource/DamageSource;)F"))
	private float grandcraft$scaleEnchantments(Player self, Entity target, float damage,
			DamageSource source) {
		float vanilla = this.getEnchantedDamage(target, damage, source);

		if (!MeleeDamage.appliesTo(self)) {
			return vanilla;
		}

		WeaponScaling scaling = MeleeDamage.forSwing(self, self.getMainHandItem());
		return damage + scaling.enchantBonus(vanilla - damage);
	}

	/**
	 * Kills the attack-cooldown damage curve. Every swing now pays in full.
	 *
	 * <p>Ungated on purpose — see the class note. Endlag and stamina are what pace an
	 * attack in this mod, and two pacing systems multiplied together is how a swing ends
	 * up dealing a fifth of what the interface says it will.
	 */
	@Inject(method = "baseDamageScaleFactor()F", at = @At("HEAD"), cancellable = true)
	private void grandcraft$noCooldownScaling(CallbackInfoReturnable<Float> info) {
		info.setReturnValue(1.0F);
	}

	/** Kills crit-on-fall. */
	@Inject(
			method = "canCriticalAttack(Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"), cancellable = true)
	private void grandcraft$noCriticals(Entity target, CallbackInfoReturnable<Boolean> info) {
		info.setReturnValue(false);
	}

	/** Kills sweep attacks. Sweeping Edge goes inert with them, which is intended. */
	@Inject(method = "isSweepAttack(ZZZ)Z", at = @At("HEAD"), cancellable = true)
	private void grandcraft$noSweep(boolean fullyCharged, boolean critical, boolean sprintKnockback,
			CallbackInfoReturnable<Boolean> info) {
		info.setReturnValue(false);
	}
}
