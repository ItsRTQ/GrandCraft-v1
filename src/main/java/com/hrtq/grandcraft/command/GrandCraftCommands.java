package com.hrtq.grandcraft.command;

import com.hrtq.grandcraft.entity.GrandCraftEntities;
import com.hrtq.grandcraft.network.GrandCraftNetworking;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import com.hrtq.grandcraft.progression.EssenceAwards;
import com.hrtq.grandcraft.skill.SkillLoadouts;
import com.hrtq.grandcraft.skill.SkillMilestones;
import com.hrtq.grandcraft.skill.SkillObjective;
import com.hrtq.grandcraft.stats.PlayerStats;
import com.hrtq.grandcraft.progression.EssenceProgress;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class GrandCraftCommands {
	/**
	 * Bound on {@code /grandcraft set essence}. Not a design ceiling on levelling —
	 * nothing else caps it — but the level loop runs once per level, so an argument
	 * with no upper bound would let a typo hang the server thread.
	 */
	private static final int MAX_SET_LEVEL = 1000;

	/**
	 * Bound on {@code /grandcraft give essence}, for the same reason: awarding walks a
	 * level at a time and each pass consumes at least one Essence.
	 */
	private static final int MAX_GIVE_ESSENCE = 1_000_000;

	/**
	 * Bound on {@code /grandcraft give milestone}. No loop behind this one — it is a
	 * single addition — so the bound is only there to keep the stored counter within
	 * an int no matter how many times it is run.
	 */
	private static final int MAX_GIVE_MILESTONE = 1_000_000;

	private GrandCraftCommands() {
	}

	/**
	 * Completes against the mob names GrandCraft registers, rather than every entity
	 * in the game — the point of a separate command is that it only offers ours.
	 */
	private static final SuggestionProvider<CommandSourceStack> SUMMONABLE_MOBS =
			(context, builder) -> SharedSuggestionProvider.suggest(
					GrandCraftEntities.summonableNames(), builder);

	/** The objectives a milestone can ask for, straight off the enum. */
	private static final SuggestionProvider<CommandSourceStack> OBJECTIVES =
			(context, builder) -> SharedSuggestionProvider.suggest(
					Arrays.stream(SkillObjective.values()).map(SkillObjective::getSerializedName),
					builder);

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("grandcraft")
						.then(Commands.literal("summon")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("mob", StringArgumentType.word())
										.suggests(SUMMONABLE_MOBS)
										.executes(context -> summon(context.getSource(),
												StringArgumentType.getString(context, "mob")))))
						// "set" replaces a value outright, "give" adds to one. Kept as two
						// verbs rather than one flagged command because the difference is
						// destructive: set wipes committed points, give never does.
						.then(Commands.literal("set")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("essence")
										.then(Commands.argument("level", IntegerArgumentType.integer(0, MAX_SET_LEVEL))
												.then(Commands.argument("player", EntityArgument.player())
														.executes(context -> setEssenceLevel(context.getSource(),
																EntityArgument.getPlayer(context, "player"),
																IntegerArgumentType.getInteger(context, "level")))))))
						.then(Commands.literal("give")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("essence")
										.then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_GIVE_ESSENCE))
												.then(Commands.argument("player", EntityArgument.player())
														.executes(context -> giveEssence(context.getSource(),
																EntityArgument.getPlayer(context, "player"),
																IntegerArgumentType.getInteger(context, "amount"))))))
								// Skill-line milestones are counted in the hundreds, so without
								// this a gate at tier 3 costs an evening of real kills to reach
								// and cannot practically be tested at all.
								.then(Commands.literal("milestone")
										.then(Commands.argument("objective", StringArgumentType.word())
												.suggests(OBJECTIVES)
												.then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_GIVE_MILESTONE))
														.then(Commands.argument("player", EntityArgument.player())
																.executes(context -> giveMilestone(context.getSource(),
																		EntityArgument.getPlayer(context, "player"),
																		StringArgumentType.getString(context, "objective"),
																		IntegerArgumentType.getInteger(context, "amount"))))))))
						.then(Commands.literal("reclass")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(context -> {
											ServerPlayer target = EntityArgument.getPlayer(context, "player");
											target.setAttached(GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);

											// A reclass unmakes the character, so their progression goes
											// with it: level, banked Essence, unspent points and every
											// point already committed. Leaving the spent points behind
											// would hand a fresh peasant the stats of the character they
											// used to be, bought with a class they no longer have.
											EssenceAwards.reset(target);

											// And what they had done towards their skill-lines, for the
											// same reason: those counters were earned as somebody else,
											// and leaving them would hand a fresh peasant a head start
											// on a tree they cannot see yet.
											SkillMilestones.reset(target);

											// And what they had equipped. The stored paths name the old
											// class's nodes and would resolve to nothing anyway, so this
											// is about not leaving a record of a character who is gone.
											SkillLoadouts.reset(target);

											// Stats come from the class and the spent points, so this has
											// to run after both have been cleared — one pass, covering
											// both halves of what just changed.
											PlayerStats.applyBaselines(target);
											context.getSource().sendSuccess(() -> Component.translatable(
													"commands.grandcraft.reclass.success", target.getDisplayName()), true);
											return 1;
										})))
						// A category rather than a command, so later config screens are
						// siblings of "combat" instead of needing their own top-level verb.
						.then(Commands.literal("config")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								// "game" first: it holds the settings that apply mod-wide, of
								// which combat is one area among several to come.
								.then(Commands.literal("game")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendGameConfig(player);
											return 1;
										}))
								.then(Commands.literal("combat")
										.executes(context -> {
											// Needs a real player: the screen opens on their client, so a
											// console or command block invocation has nowhere to send it.
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendCombatConfig(player);
											return 1;
										}))
								.then(Commands.literal("stats")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendStatConfig(player);
											return 1;
										}))
								.then(Commands.literal("levels")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendLevelConfig(player);
											return 1;
										}))
								.then(Commands.literal("weapons")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendWeaponConfig(player);
											return 1;
										}))
								.then(Commands.literal("skills")
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											GrandCraftNetworking.sendSkillConfig(player);
											return 1;
										})))));
	}

	/**
	 * Spawns one of the mod's mobs where the command was run.
	 *
	 * <p>Deliberately narrower than vanilla's {@code /summon}: no position, no NBT.
	 * This exists so GrandCraft's own mobs can be reached by name while they have no
	 * spawn eggs and no natural spawning, not to reimplement a command that already
	 * works.
	 */
	/**
	 * Puts a character at an exact Essence Power level, with that level's worth of
	 * points unspent.
	 *
	 * <p>Rebuilds rather than adjusts — see {@link EssenceAwards#setLevel}. Anything
	 * already committed to a stat is cleared, which is why the attributes are rewritten
	 * straight afterwards: stats are derived from class plus spent points, so leaving
	 * the old values in place would describe a character who no longer exists. Exactly
	 * the pass {@code reclass} makes for the same reason.
	 */
	private static int setEssenceLevel(CommandSourceStack source, ServerPlayer target, int level) {
		EssenceAwards.setLevel(target, level);
		PlayerStats.applyBaselines(target);

		EssenceProgress progress = EssenceAwards.progressOf(target);

		source.sendSuccess(() -> Component.translatable("commands.grandcraft.set_essence.success",
				target.getDisplayName(), level, progress.statPoints(), progress.poolPoints()), true);
		return 1;
	}

	/**
	 * Awards Essence as though it had been picked up, levelling the character as far as
	 * it pays for.
	 *
	 * <p>The amount is in <strong>Essence, not orbs</strong>. Orbs are worth one to
	 * three each, so an orb count would be a random amount of progress — no use in a
	 * command whose whole point is reaching a known state. This runs through the same
	 * {@code award} path a pickup does, so levels, points and the announcement behave
	 * exactly as they would in play.
	 *
	 * <p>Unlike {@code set}, this is purely additive: banked Essence and committed
	 * points are left alone, so the attributes do not need rewriting.
	 */
	private static int giveEssence(CommandSourceStack source, ServerPlayer target, int amount) {
		EssenceAwards.award(target, amount);

		source.sendSuccess(() -> Component.translatable("commands.grandcraft.give_essence.success",
				amount, target.getDisplayName(),
				EssenceAwards.progressOf(target).level()), true);
		return 1;
	}

	/**
	 * Credits progress towards one objective, exactly as playing would.
	 *
	 * <p>Goes through {@link SkillMilestones#count} rather than writing the attachment,
	 * so the command cannot produce a state that play could not — the same contract
	 * {@code give essence} has with {@code EssenceAwards.award}.
	 *
	 * <p>Reports the new total rather than only the amount added, because the number
	 * that decides whether a node opened is the total.
	 */
	private static int giveMilestone(CommandSourceStack source, ServerPlayer target,
			String objectiveName, int amount) throws CommandSyntaxException {
		SkillObjective objective = SkillObjective.byId(objectiveName);

		if (objective == null) {
			// Hard failure rather than a silent no-op, as with an unknown mob: a typo
			// that quietly counts nothing is indistinguishable from a broken gate.
			throw UNKNOWN_OBJECTIVE.create(objectiveName);
		}

		SkillMilestones.count(target, objective, amount);

		int total = SkillMilestones.progressOf(target).get(objective);

		source.sendSuccess(() -> Component.translatable("commands.grandcraft.give_milestone.success",
				amount, objectiveName, target.getDisplayName(), total), true);
		return 1;
	}

	private static int summon(CommandSourceStack source, String name) throws CommandSyntaxException {
		EntityType<?> type = GrandCraftEntities.summonable(name);

		if (type == null) {
			// A hard failure rather than a silent no-op: a typo that quietly spawns
			// nothing is indistinguishable from a mob that failed to render.
			throw UNKNOWN_MOB.create(name);
		}

		ServerLevel level = source.getLevel();
		Vec3 position = source.getPosition();
		Entity spawned = type.spawn(level, BlockPos.containing(position), EntitySpawnReason.COMMAND);

		if (spawned == null) {
			throw SUMMON_FAILED.create(name);
		}

		source.sendSuccess(() -> Component.translatable(
				"commands.grandcraft.summon.success", spawned.getDisplayName()), true);
		return 1;
	}

	private static final DynamicCommandExceptionType UNKNOWN_MOB = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.grandcraft.summon.unknown", name));

	private static final DynamicCommandExceptionType SUMMON_FAILED = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.grandcraft.summon.failed", name));

	private static final DynamicCommandExceptionType UNKNOWN_OBJECTIVE = new DynamicCommandExceptionType(
			name -> Component.translatable("commands.grandcraft.give_milestone.unknown", name));
}
