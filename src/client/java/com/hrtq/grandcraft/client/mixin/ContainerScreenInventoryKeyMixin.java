package com.hrtq.grandcraft.client.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops the inventory key doing anything inside a container screen. Escape closes
 * them now.
 *
 * <p>That key belongs to the quick-slot wheel, and a key cannot mean two things.
 * Leaving vanilla's meaning in place broke the wheel's own inventory button in a way
 * that looked like a flicker: a <strong>held</strong> key produces GLFW key-repeat
 * events, screens treat every repeat as a fresh press, so clicking the chest wedge
 * while still holding the key opened the inventory and the next repeat shut it again.
 *
 * <p>This is the only screen path that reads the mapping —
 * {@code AbstractContainerScreen.keyPressed} — and everything with slots inherits it,
 * so one redirect covers the player inventory, chests and the rest.
 *
 * <p>Deliberately not a HEAD cancel. Vanilla gives the focused widget the key first
 * and only then checks this mapping; cancelling earlier would stop the letter E
 * reaching an anvil's name box or the creative search. The number keys that swap a
 * hovered stack into a slot are read further down the same method and are untouched.
 *
 * <h2>Why the ordinal is safe here</h2>
 *
 * {@code matches} is called several times in that method, so this targets the first
 * one. Rather than trust the count, the redirect <em>checks which mapping it was
 * handed</em> and defers to vanilla for anything that is not the inventory key — so
 * if a future version reorders those calls this weakens to a no-op instead of
 * silently disabling middle-click pick-block.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenInventoryKeyMixin {
	@Redirect(
			method = "keyPressed",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z",
					ordinal = 0))
	private boolean grandcraft$ignoreInventoryKeyInScreens(KeyMapping mapping, KeyEvent event) {
		if (mapping == Minecraft.getInstance().options.keyInventory) {
			return false;
		}

		return mapping.matches(event);
	}
}
