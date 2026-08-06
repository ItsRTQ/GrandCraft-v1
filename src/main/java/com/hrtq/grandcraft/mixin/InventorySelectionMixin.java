package com.hrtq.grandcraft.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens how many slots the player may hold from nine to eleven, which is what fills
 * the radial wheel's last two wedges.
 *
 * <p><strong>This is the whole change.</strong> The wheel has always named eleven slots
 * in {@code RadialMenu.SLOT_FOR_WEDGE} and always refused the ones past
 * {@link Inventory#getSelectionSize()}, so raising that number here unlocks them with
 * no edit to any client code — which is exactly why the wheel was built to ask rather
 * than to carry a constant of its own.
 *
 * <h2>Why these two methods and nothing else</h2>
 *
 * The nine-slot limit is two static methods, each holding a single inlined {@code 9}.
 * A jar-wide constant-pool scan (2026-08-05, both jars) finds only seven classes
 * referencing either, and every one of them is a use this change wants:
 *
 * <ul>
 *   <li>{@code Inventory.setSelectedSlot} throws outside {@code isHotbarSlot};</li>
 *   <li>{@code MouseHandler.onScroll} wraps the scroll wheel over the range;</li>
 *   <li>{@code ServerGamePacketListenerImpl.handleSetCarriedItem} and
 *       {@code ClientPacketListener} validate the carried-item packet;</li>
 *   <li>{@code CreativeModeInventoryScreen} loops the range when saving or loading a
 *       creative hotbar preset, and guards one click case with it;</li>
 *   <li>{@code Hotbar.SIZE} reads {@code getSelectionSize()} in its own
 *       {@code <clinit>}, so the preset format follows automatically.</li>
 * </ul>
 *
 * <p><strong>{@code InventoryMenu.isHotbarSlot} is a different method</strong> over
 * container-slot indices, and is deliberately untouched — nothing about the inventory
 * <em>screen</em> changes here.
 *
 * <h2>Common, not client, and that is load-bearing</h2>
 *
 * The server validates the carried-item packet against {@code getSelectionSize()} and
 * {@code setSelectedSlot} throws on a slot outside {@code isHotbarSlot}. A client-only
 * widening would let the wheel pick slot 9, then have the server refuse the packet — so
 * this mixin lives in the common source set and both sides answer eleven.
 *
 * <h2>What it costs</h2>
 *
 * Slots 9 and 10 are the first two boxes of the inventory grid's top row, and nothing
 * here moves them — the inventory screen stays vanilla (see {@code memory.md},
 * <em>Settled</em>). So those two stacks appear both in the wheel and in the grid.
 * That is the accepted price of leaving every container screen alone.
 *
 * <p>One regression, creative-only: {@code Hotbar}'s codec validates a saved preset
 * with {@code Util.fixedSize(list, SIZE)}, so creative hotbar presets saved before this
 * change hold nine entries where eleven are now expected and will be read as empty.
 */
@Mixin(Inventory.class)
public class InventorySelectionMixin {
	/**
	 * How many slots the player may hold. Eleven because the wheel has twelve wedges and
	 * one of them is the inventory button.
	 *
	 * <p>Deliberately the only constant naming that number anywhere in the mod: the
	 * wheel derives its own answer from {@code Inventory.getSelectionSize()}, so "what
	 * the wheel offers" and "what the player may hold" cannot drift apart.
	 */
	private static final int SELECTION_SIZE = 11;

	@Inject(method = "getSelectionSize", at = @At("HEAD"), cancellable = true)
	private static void grandcraft$selectionSize(CallbackInfoReturnable<Integer> info) {
		info.setReturnValue(SELECTION_SIZE);
	}

	@Inject(method = "isHotbarSlot", at = @At("HEAD"), cancellable = true)
	private static void grandcraft$isHotbarSlot(int slot, CallbackInfoReturnable<Boolean> info) {
		info.setReturnValue(slot >= 0 && slot < SELECTION_SIZE);
	}
}
