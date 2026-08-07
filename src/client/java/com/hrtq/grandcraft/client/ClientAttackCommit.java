package com.hrtq.grandcraft.client;

/**
 * An attack this client has sent and not yet heard back about.
 *
 * <p><strong>This exists to close one hole: the round trip.</strong>
 * {@code MinecraftAttackLockoutMixin} drops any click thrown while the player is
 * mid-swing, and it reads the phase the server sent — which is the right source, because
 * it is the same clock the server enforces against. But the phase for a swing cannot
 * arrive until the swing has been to the server and back, and a spam-clicking player gets
 * several clicks into that gap. Each one passed the lockout (the phase still said
 * neutral), swung vanilla's arm, and was then refused server-side: an attack that looked
 * like it happened and did nothing. The phantom.
 *
 * <p>So the client latches its own commitment the moment it sends one, and treats itself
 * as swinging until the server answers. The latch is not authority — the server still
 * decides, and {@link ClientCombatPhases} still governs everything past the first reply.
 * It only covers the interval where the client has asked and does not yet know.
 *
 * <h2>Committed on send, not on click</h2>
 *
 * <p>Set from the two places that actually tell the server an attack happened — the
 * entity attack in {@code MultiPlayerGameModeAttackMixin} and the whiff report in
 * {@code MinecraftAttackMissMixin} — because those are exactly the inputs the server
 * books a phase for. <strong>A click on a block must not latch it</strong>: mining starts
 * no phase, and latching there would lock the player out of their own next pickaxe swing.
 *
 * <h2>Cleared by the answer, with a window as the backstop</h2>
 *
 * <p>Any phase packet for the local player clears it, whatever it says, because at that
 * point the client knows and {@link ClientCombatPhases} takes over. The window only
 * matters when no packet comes at all, which is the refusal case — out of stamina, or
 * guarding. Then the latch expires on its own.
 *
 * <p>{@link #ANSWER_WINDOW_MILLIS} is therefore two things at once: the longest round
 * trip this covers, and the longest a <em>refused</em> attack can delay the next input.
 * 250ms covers a 200ms round trip on top of a server tick. Raise it if phantoms come back
 * on a high-latency server; lower it if a refused swing feels sticky. It cannot cause a
 * wrong refusal — the server was going to refuse anything inside this window anyway.
 */
public final class ClientAttackCommit {
	private static final long ANSWER_WINDOW_MILLIS = 250L;

	private static long committedMillis;

	private ClientAttackCommit() {
	}

	/** The client has just told the server it is attacking. */
	public static void commit(long nowMillis) {
		committedMillis = nowMillis;
	}

	/** Whether an attack is out there unanswered, and input should be swallowed. */
	public static boolean pending(long nowMillis) {
		return committedMillis > 0L && nowMillis - committedMillis < ANSWER_WINDOW_MILLIS;
	}

	/**
	 * The server has answered, or the world has gone away.
	 *
	 * <p>Also called on disconnect: a latch left set would swallow the first click in the
	 * next world.
	 */
	public static void clear() {
		committedMillis = 0L;
	}
}
