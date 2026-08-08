package com.hrtq.grandcraft.block;

import com.hrtq.grandcraft.GrandCraft;
import com.hrtq.grandcraft.item.GrandCraftItems;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The mod's own blocks.
 *
 * <h2>Why blocks register here and not beside the items</h2>
 * A block is really two registrations — the block and the {@link BlockItem} that
 * places it — and the second one has to be linked back into {@code Item.BY_BLOCK}
 * by hand. That link is the whole reason this class exists rather than a couple of
 * extra lines in {@code GrandCraftItems}: forget it and everything looks fine until
 * someone middle-clicks the block or a piston moves it.
 *
 * <h2>What a block's numbers mean here</h2>
 * Hardness and blast resistance are properties of <em>this block</em>, so they are
 * baked at registration exactly like a weapon's damage — the same item/config line
 * {@code GrandCraftItems} draws. Nothing about mining belongs in a config screen.
 *
 * <p>Which tool is <em>efficient</em> is deliberately <strong>not</strong> stated in
 * code: it is the vanilla block tag {@code #minecraft:mineable/pickaxe}, appended
 * from {@code data/minecraft/tags/block/mineable/pickaxe.json}. Tags merge across
 * mods, so that is also what lets another mod's pickaxe work on this block on day
 * one. {@link BlockBehaviour.Properties#requiresCorrectToolForDrops()} is the other
 * half of the pair — without it the tag only changes mining <em>speed</em> and the
 * block drops to a bare hand.
 */
public final class GrandCraftBlocks {
	/**
	 * Deepslate's own blast resistance in 26.2, read off {@code Blocks.<clinit>}:
	 * {@code strength(3.0F, 6.0F)}.
	 *
	 * <p>Verified rather than remembered, and worth doing — deepslate was 3.5 in the
	 * versions most references describe, and its destroy time is 3.0 here.
	 */
	private static final float DEEPSLATE_EXPLOSION_RESISTANCE = 6.0F;

	/**
	 * A third longer to mine than deepslate's 3.0 (user, 2026-08-08: "slightly stronger").
	 *
	 * <p>Destroy time is a linear divisor, so 4.0 against 3.0 is exactly +33% on every
	 * tool: a diamond pickaxe goes 0.56s → 0.75s, a stone one 1.13s → 1.50s. That is
	 * about the smallest step that is felt rather than measured — a 3.5 would land
	 * inside the noise of when the player started holding the button.
	 *
	 * <p><strong>Only the mining time moved.</strong> Blast resistance stays at
	 * deepslate's 6.0, because that describes how the block answers a creeper and
	 * nothing about it was asked to change. The two are independent numbers and it is
	 * worth keeping them that way, so a future "make it tougher" can mean one or the
	 * other rather than both by accident.
	 */
	private static final float MANA_CRYSTAL_DESTROY_TIME = 4.0F;

	/**
	 * A cluster of raw mana, grown out of stone.
	 *
	 * <p>Stone's rules, a shade past deepslate's numbers: mined with a pickaxe, dropped
	 * only with one, and slow enough that it reads as something worth the trip.
	 *
	 * <p><strong>It does not drop itself any more.</strong> Breaking it yields 4–6
	 * Arcane Shards, Fortune-scaled, and only Silk Touch returns the block — the whole
	 * of that is the loot table, not code. Which is the point: the block states what it
	 * <em>is</em> (how hard, what tool, what shape) and the loot table states what it
	 * <em>gives</em>, so retuning a drop never means recompiling.
	 *
	 * <p><strong>It takes the default full-cube shape, and that is measured rather than
	 * assumed.</strong> The 2026-08-07 model is 72 cuboids, and once their rotations are
	 * applied the cluster spans roughly -5..21 in x, -2.5..22.7 in y and -1.8..17.2 in z
	 * — it overflows the block on every axis, standing about half a block proud of the
	 * one above. A full cube is therefore the closest legal interaction shape there is;
	 * a {@code getShape} override could only make the hitbox smaller than the art, which is
	 * the wrong error. That is also why this needs no {@code Block} subclass: an earlier
	 * model was a thin spire and had one purely to carry a {@code VoxelShape}.
	 *
	 * <p>{@code noOcclusion} is not cosmetic and matters more than ever. The cluster
	 * fills its cube but is full of gaps, so without it the game treats the block as
	 * solid for culling and light and paints black holes where its neighbours should be.
	 *
	 * <p>The map colour is measured too: the artist's texture means RGB (80, 168, 212),
	 * which {@code COLOR_LIGHT_BLUE} sits 27 away from and every other candidate further.
	 *
	 * <p>Nothing places it in the world yet: no ore feature and no recipe. It exists in
	 * the creative tab and on the end of a pickaxe, which is what was asked for.
	 */
	public static final Block MANA_CRYSTAL = register("mana_crystal", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.strength(MANA_CRYSTAL_DESTROY_TIME, DEEPSLATE_EXPLOSION_RESISTANCE)
					.requiresCorrectToolForDrops()
					.sound(SoundType.AMETHYST)
					.noOcclusion());

	private GrandCraftBlocks() {
	}

	/**
	 * Registers one block, the item that places it, and the link between them.
	 *
	 * <p>{@code setId} is mandatory on both halves in 26.2, the same as it is for a
	 * plain item: the description id and the model path are both derived from it.
	 *
	 * <p>{@code registerBlocks} is the call that is easy to miss. {@code Block.asItem}
	 * asks {@code Item.byBlock} once and then <em>caches the answer on the block</em>,
	 * so a block whose item never reached that map answers "air" forever — pick-block,
	 * {@code getCloneItemStack} and anything that turns a placed block back into a
	 * stack all break, and none of it shows up at registration time. Vanilla does the
	 * same call from {@code Items.registerBlock}.
	 */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		Identifier id = GrandCraft.id(name);
		Block block = Registry.register(BuiltInRegistries.BLOCK, id,
				factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));

		BlockItem item = Registry.register(BuiltInRegistries.ITEM, id,
				new BlockItem(block, new Item.Properties()
						.useBlockDescriptionPrefix()
						.setId(ResourceKey.create(Registries.ITEM, id))));
		item.registerBlocks(Item.BY_BLOCK, item);

		GrandCraftItems.addToCreativeTab(item);
		return block;
	}

	/** No-op: exists to force this class to initialise from {@code onInitialize}. */
	public static void register() {
	}
}
