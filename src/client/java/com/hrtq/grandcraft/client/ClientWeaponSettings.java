package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.combat.WeaponSettings;

/**
 * The weapon settings as last sent by the server.
 *
 * <p>The client keeps its own copy for the same reason it keeps
 * {@link ClientStatSettings}: the server's is unreachable from a remote client. Here it
 * is the item tooltip that needs them — a weapon's damage is now a fact about its
 * holder, and working it out takes the category's scaling weights and the shared
 * down-scale as well as the player's own stats.
 */
public final class ClientWeaponSettings {
	private static WeaponSettings current = WeaponSettings.DEFAULT;

	private ClientWeaponSettings() {
	}

	public static WeaponSettings current() {
		return current;
	}

	public static void set(WeaponSettings settings) {
		current = settings;
	}
}
