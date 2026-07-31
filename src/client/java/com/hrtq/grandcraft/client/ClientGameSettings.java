package com.hrtq.grandcraft.client;

import com.hrtq.grandcraft.config.GameSettings;

/**
 * The general settings as last sent by the server.
 *
 * <p>The client keeps its own copy because the server's is unreachable from a
 * remote client, and because the renderer needs to read this every frame. Defaults
 * apply until the join packet lands, which is a frame or two of health bars being
 * enabled on a server that has them off — harmless, and self-correcting.
 */
public final class ClientGameSettings {
	private static GameSettings current = GameSettings.DEFAULT;

	private ClientGameSettings() {
	}

	public static GameSettings current() {
		return current;
	}

	public static void set(GameSettings settings) {
		current = settings;
	}
}
