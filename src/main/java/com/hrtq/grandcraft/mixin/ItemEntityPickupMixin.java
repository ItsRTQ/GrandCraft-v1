package com.hrtq.grandcraft.mixin;

import com.hrtq.grandcraft.combat.Downed;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a downed player hoovering up the floor they are lying on.
 *
 * <p>The one "you cannot do that while down" rule with no cancellable event behind
 * it. Blocks, items, entities and mining are all Fabric callbacks — see
 * {@code GrandCraftCombat.registerDownedVetoes} — and picking things up is not,
 * because vanilla never treated it as an interaction the player <em>makes</em>. It
 * happens by walking into the item, which is exactly what a crawling player does.
 *
 * <p>{@code playerTouch} rather than {@code Player.take} or the inventory add: this
 * is the method that decides a pickup is going to happen at all, so cancelling here
 * leaves the item on the ground untouched rather than picked up and dropped again.
 *
 * <p>The item is not consumed and its pickup delay is left alone, so it stays where
 * it is and is there to be collected on standing up. Not merely acceptable — it is
 * the point: what you dropped when you went down is still yours if someone picks you
 * up, which is a small part of why a revive is worth having.
 */
@Mixin(ItemEntity.class)
public class ItemEntityPickupMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void grandcraft$noPickupWhileDowned(Player player, CallbackInfo info) {
		// Server-side only in effect: the combat controller is not a synced attachment,
		// so on the client this always answers false and the call falls through. That is
		// correct rather than merely harmless — vanilla's own pickup is server-decided
		// and the client is told about it.
		if (Downed.isDowned(player)) {
			info.cancel();
		}
	}
}
