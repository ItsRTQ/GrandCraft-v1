package com.hrtq.grandcraft.network;

import com.hrtq.grandcraft.GrandCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player is holding the guard key, or has let go.
 *
 * <p>One boolean, and deliberately nothing else. It does not say what the player is
 * blocking with — the server reads the off-hand itself at the moment that matters,
 * so a modified client cannot claim to be holding a shield it does not have. It does
 * not say when the guard should come up either: whether the guard is legal, how long
 * it takes to raise, what holding it costs and what an absorbed hit costs are all
 * decided server side.
 *
 * <p>Sent on both edges <em>and</em> repeated every
 * {@link com.hrtq.grandcraft.combat.CombatConstants#GUARD_KEEPALIVE_TICKS} while the
 * key is down. The repeat is what makes a lost release harmless: the server forgets
 * an un-renewed hold and puts the guard down by itself, rather than leaving a player
 * who disconnected mid-block standing behind one forever.
 */
public record GuardPayload(boolean held) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GuardPayload> TYPE =
			new CustomPacketPayload.Type<>(GrandCraft.id("guard"));

	public static final StreamCodec<ByteBuf, GuardPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeBoolean(payload.held()),
			buf -> new GuardPayload(buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
