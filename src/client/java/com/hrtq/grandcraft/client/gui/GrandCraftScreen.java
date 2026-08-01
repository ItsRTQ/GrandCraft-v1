package com.hrtq.grandcraft.client.gui;

import com.hrtq.grandcraft.client.ClientLevelSettings;
import com.hrtq.grandcraft.client.ClientMana;
import com.hrtq.grandcraft.client.ClientStamina;
import com.hrtq.grandcraft.client.ClientStatSettings;
import com.hrtq.grandcraft.network.SelectClassPayload;
import com.hrtq.grandcraft.network.SpendPoolPointPayload;
import com.hrtq.grandcraft.network.SpendStatPointPayload;
import com.hrtq.grandcraft.player.GrandCraftAttachments;
import com.hrtq.grandcraft.player.PlayerClass;
import com.hrtq.grandcraft.progression.EssenceProgress;
import com.hrtq.grandcraft.progression.LevelSettings;
import com.hrtq.grandcraft.stats.CharacterPool;
import com.hrtq.grandcraft.stats.CharacterStat;
import com.hrtq.grandcraft.stats.StatBlock;
import com.hrtq.grandcraft.stats.StatEffects;
import com.hrtq.grandcraft.stats.StatSettings;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * The character sheet, opened with the {@code =} key.
 *
 * <p>Two different screens behind one key. Without a class it is the class picker it
 * has always been. With one it is the sheet: the character on the left with their
 * class, Essence Power level and progress underneath, their stats and pools beside
 * them, and the right half held for whatever comes next.
 *
 * <h2>Why every widget is sized to its own text</h2>
 * 26.2's {@code StringWidget} draws at {@code getX()} with no centring term, so a
 * widget handed a wide box does <em>not</em> centre its text inside it — which is why
 * the old title, given {@code width = this.width}, drew hard against the left edge.
 * Rather than depend on that reading, every widget here is made exactly as wide as
 * its text and then positioned by hand. Left-aligning and centring inside a box that
 * tight are the same thing, so the layout comes out right either way.
 *
 * <h2>Widgets for anything hoverable, drawing for the rest</h2>
 * Stat and pool rows are widgets because a widget is what carries a tooltip — the
 * numbers are bare, and hovering is what explains them. The divider, the model and
 * the "coming soon" panel are drawn directly in {@link #extractRenderState}, since
 * none of them answer the mouse.
 */
public class GrandCraftScreen extends Screen {
	private static final int TITLE_TOP = 15;
	private static final int CONTENT_TOP = 40;
	private static final int BOTTOM_MARGIN = 20;
	private static final int EDGE_PADDING = 12;
	private static final int COLUMN_GAP = 12;

	/**
	 * Room under the model for the identity block: class name, Essence Power level and
	 * progress towards the next one.
	 *
	 * <p>One gap plus three rows, with a little left over so the last line is not
	 * pressed against the bottom margin. Raise this before adding a fourth line —
	 * the model is sized from whatever is left, so it gives way rather than the text.
	 */
	private static final int IDENTITY_RESERVE = 44;

	private static final int LINE_HEIGHT = 9;
	private static final int ROW_HEIGHT = 11;
	private static final int SECTION_GAP = 7;

	/** Wide enough for the longest pool reading, e.g. "88 / 100". */
	private static final int VALUE_WIDTH = 46;

	/**
	 * The side of a spend button, and the width of the strip reserved for the column
	 * they sit in.
	 *
	 * <p>Deliberately equal to {@link #ROW_HEIGHT}, so four of them stacked on
	 * consecutive rows meet exactly rather than overlapping. A vanilla button is
	 * normally 20 tall and its texture compresses to fit; if these read as too
	 * squashed, this is the single number to raise — the rows and the panel are laid
	 * out from it.
	 */
	private static final int SPEND_BUTTON_SIZE = ROW_HEIGHT;

	/**
	 * The column is reserved whenever the sheet is shown, not only when there are
	 * points to spend. Sizing the panel differently in the two cases would make the
	 * whole layout jump sideways the moment a level landed while the sheet was open.
	 */
	private static final int SPEND_COLUMN = SPEND_BUTTON_SIZE + COLUMN_GAP;

	/** ARGB, and the alpha byte is not optional — 0x6E6E6E would be invisible. */
	private static final int DIVIDER_COLOUR = 0xFF6E6E6E;
	private static final int PANEL_COLOUR = 0x22FFFFFF;
	private static final int COMING_SOON_COLOUR = 0xFFA0A0A0;

	/** Vanilla draws the player at size 30 inside a 49x70 box; keep that ratio. */
	private static final int MODEL_REFERENCE_SIZE = 30;
	private static final int MODEL_REFERENCE_WIDTH = 49;
	private static final int MODEL_REFERENCE_HEIGHT = 70;
	private static final float MODEL_Y_OFFSET = 0.0625F;

	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 24;

	/** Shown for a pool the server has not reported, or has switched off entirely. */
	private static final Component MISSING = Component.literal("—");

	/** Every label on the panel, so the widest of them can size the value column. */
	private static final String[] PANEL_KEYS = {
			"stats", "strength", "agility", "constitution", "arcane",
			"attributes", "health", "stamina", "mana" };

	private boolean classed;

	private int dividerX;
	private int contentTop;
	private int contentBottom;
	private int modelLeft;
	private int modelRight;
	private int modelBottom;
	private int modelSize;
	private int panelRight;

	/** Where the identity block under the model is centred. */
	private int identityCentreX;

	/** The three live rows, kept so {@link #tick} can rewrite them in place. */
	private StringWidget healthValue;
	private StringWidget staminaValue;
	private StringWidget manaValue;

	/** The two live progression lines, rewritten the same way and for the same reason. */
	private StringWidget levelValue;
	private StringWidget essenceValue;

	/**
	 * What the panel was built to show, so {@link #tick} can tell a change that needs
	 * a rebuild from one that only needs a number rewritten.
	 *
	 * <p>Essence and the pools move constantly and are updated in place, because
	 * rebuilding every tick would drop a tooltip out from under the mouse. These three
	 * change rarely and structurally — a spend adds a point to a stat and removes a
	 * button, which no amount of rewriting text can express — so they are worth a
	 * rebuild on the tick they actually move.
	 */
	private StatBlock shownStats;
	private int shownStatPoints;
	private int shownPoolPoints;

	public GrandCraftScreen() {
		super(Component.translatable("screen.grandcraft.character_sheet"));
	}

	@Override
	protected void init() {
		// Cleared before anything is rebuilt: init() runs again on resize, and a stale
		// reference to a widget no longer on screen would be updated forever.
		this.healthValue = null;
		this.staminaValue = null;
		this.manaValue = null;
		this.levelValue = null;
		this.essenceValue = null;
		this.shownStats = null;

		addCentred(this.title, this.width / 2, TITLE_TOP);

		PlayerClass playerClass = this.minecraft.player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);
		this.classed = playerClass != PlayerClass.PEASANT;

		if (this.classed) {
			initSheet(playerClass);
		} else {
			initClassSelection();
		}
	}

	// ------------------------------------------------------------------ the sheet

	private void initSheet(PlayerClass playerClass) {
		this.dividerX = this.width / 2;
		this.contentTop = CONTENT_TOP;
		this.contentBottom = this.height - BOTTOM_MARGIN;

		// The panel is sized to its content and anchored against the divider; the model
		// then takes whatever is left. In that order a narrow window loses picture
		// rather than text, which is the right thing to lose. The spend column is taken
		// off the right first, so the values stay right-anchored inside what remains.
		this.panelRight = this.dividerX - EDGE_PADDING - SPEND_COLUMN;

		int panelLeft = this.panelRight - (widestLabel() + COLUMN_GAP + VALUE_WIDTH);
		this.modelLeft = EDGE_PADDING;
		this.modelRight = Math.max(this.modelLeft + 1, panelLeft - COLUMN_GAP);
		this.modelBottom = this.contentBottom - IDENTITY_RESERVE;
		this.modelSize = modelSize();
		this.identityCentreX = (this.modelLeft + this.modelRight) / 2;

		buildIdentity(playerClass);
		buildPanel(panelLeft);
	}

	/**
	 * Who the character is, under the picture: class, then Essence Power level, then
	 * how far through the current level they are.
	 *
	 * <p>The two progression lines start empty and are filled by
	 * {@link #refreshProgress}, so there is one place that decides how they read
	 * rather than an initial version and an updating version that could disagree.
	 */
	private void buildIdentity(PlayerClass playerClass) {
		int y = this.modelBottom + SECTION_GAP;

		addCentred(playerClass.displayName().copy().withStyle(ChatFormatting.YELLOW),
				this.identityCentreX, y);

		this.levelValue = addCentred(Component.empty(), this.identityCentreX, y + ROW_HEIGHT);
		this.levelValue.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.sheet.level.tooltip")));

		this.essenceValue = addCentred(Component.empty(), this.identityCentreX, y + ROW_HEIGHT * 2);
		this.essenceValue.setTooltip(Tooltip.create(
				Component.translatable("screen.grandcraft.sheet.essence.tooltip")));

		refreshProgress();
	}

	private void buildPanel(int panelLeft) {
		EssenceProgress progress = progress();

		this.shownStats = currentStats();
		this.shownStatPoints = progress.statPoints();
		this.shownPoolPoints = progress.poolPoints();

		int y = this.contentTop;

		y = addHeading("stats", panelLeft, y, this.shownStatPoints, null);

		for (CharacterStat stat : CharacterStat.values()) {
			y = addStatRow(stat, panelLeft, y);
		}

		y += SECTION_GAP;

		y = addHeading("attributes", panelLeft, y, this.shownPoolPoints,
				Component.translatable("screen.grandcraft.sheet.pool_points.tooltip"));

		this.healthValue = addPoolRow(CharacterPool.HEALTH, panelLeft, y);
		this.staminaValue = addPoolRow(CharacterPool.STAMINA, panelLeft, y + ROW_HEIGHT);
		this.manaValue = addPoolRow(CharacterPool.MANA, panelLeft, y + ROW_HEIGHT * 2);

		refreshPools();
	}

	/**
	 * A section heading, carrying the unspent point count for that section when there
	 * is one.
	 */
	private int addHeading(String key, int panelLeft, int y, int points, Component tooltip) {
		MutableComponent text = points > 0
				? Component.translatable("screen.grandcraft.sheet.heading_points", label(key), points)
				: label(key);

		StringWidget heading = place(text.withStyle(ChatFormatting.GOLD), panelLeft, y);

		if (tooltip != null && points > 0) {
			heading.setTooltip(Tooltip.create(tooltip));
		}

		return y + ROW_HEIGHT;
	}

	/**
	 * A stat: the bare number, with what it currently does on the tooltip, and a spend
	 * button in the reserved column while there is a point to spend.
	 */
	private int addStatRow(CharacterStat stat, int panelLeft, int y) {
		String key = stat.translationKey();
		double value = StatEffects.statOf(this.minecraft.player, stat.attribute());

		StringWidget name = place(label(key), panelLeft, y);
		StringWidget number = placeRight(Component.literal(whole(value)), y);

		// On both halves of the row, so hovering anywhere along it works.
		Tooltip tooltip = Tooltip.create(statTooltip(key, value));
		name.setTooltip(tooltip);
		number.setTooltip(tooltip);

		if (this.shownStatPoints > 0) {
			addSpendButton(y,
					Component.translatable("screen.grandcraft.sheet.spend.tooltip", stat.displayName()),
					() -> ClientPlayNetworking.send(new SpendStatPointPayload(stat)));
		}

		return y + ROW_HEIGHT;
	}

	/**
	 * The button that commits a point.
	 *
	 * <p>Built only when there is a point to spend rather than built and disabled: a
	 * column of greyed buttons that do nothing is noise on a screen whose whole left
	 * column is meant to be readable at a glance. {@link #tick} rebuilds the panel when
	 * the count changes, so they appear and disappear on their own.
	 *
	 * <p>Shared by both kinds of point — the caller supplies what to send — because the
	 * button is the same affordance in the same column either way, and the only thing
	 * that differs is which message it produces.
	 *
	 * <p>Nothing is predicted. The request goes to the server and the change comes back
	 * through the attachment's own sync, which is what the rebuild is watching for — so
	 * a refused spend simply leaves the button where it was.
	 */
	private void addSpendButton(int y, Component tooltip, Runnable onPress) {
		Button button = Button.builder(Component.literal("+"), pressed -> onPress.run())
				.bounds(this.panelRight + COLUMN_GAP, y - 1, SPEND_BUTTON_SIZE, SPEND_BUTTON_SIZE)
				.build();

		button.setTooltip(Tooltip.create(tooltip));
		addRenderableWidget(button);
	}

	/**
	 * A pool: filled in now, rewritten every tick by {@link #refreshPools}, and given a
	 * spend button while there is an attribute point to put into it.
	 */
	private StringWidget addPoolRow(CharacterPool pool, int panelLeft, int y) {
		String key = pool.translationKey();

		StringWidget name = place(label(key), panelLeft, y);
		StringWidget value = placeRight(Component.empty(), y);

		Tooltip tooltip = Tooltip.create(
				Component.translatable("screen.grandcraft.sheet." + key + ".tooltip"));
		name.setTooltip(tooltip);
		value.setTooltip(tooltip);

		if (this.shownPoolPoints > 0) {
			addSpendButton(y,
					Component.translatable("screen.grandcraft.sheet.spend.tooltip", pool.displayName()),
					() -> ClientPlayNetworking.send(new SpendPoolPointPayload(pool)));
		}

		return value;
	}

	/**
	 * Restates the three pools.
	 *
	 * <p>The screen does not pause the game, so these move while it is open — health
	 * as you take a hit, stamina as it comes back. Only the three values are touched;
	 * rebuilding the screen every tick would drop a tooltip out from under the mouse.
	 */
	private void refreshPools() {
		LocalPlayer player = this.minecraft.player;

		if (player == null || this.healthValue == null) {
			return;
		}

		long now = Util.getMillis();

		// Health is written to the half-heart because half a heart is a real amount.
		// A third of a stamina point is not, so those two are whole numbers.
		setValue(this.healthValue, reading(exact(player.getHealth()), exact(player.getMaxHealth())));
		setValue(this.staminaValue, ClientStamina.hasData()
				? reading(whole(ClientStamina.current(now)), whole(ClientStamina.max()))
				: MISSING);
		setValue(this.manaValue, ClientMana.hasData()
				? reading(whole(ClientMana.current(now)), whole(ClientMana.max()))
				: MISSING);
	}

	private static Component reading(String current, String max) {
		return Component.translatable("screen.grandcraft.sheet.value", current, max);
	}

	/**
	 * Restates the Essence Power level and the progress towards the next one.
	 *
	 * <p>Live for the same reason the pools are: the sheet does not pause the game, and
	 * orbs keep flying to the player and being collected while it is open — so a frozen
	 * number here would look like collection had stopped working.
	 *
	 * <p>Read straight off the synced attachment rather than through a packet of its
	 * own, exactly as the class name above it is.
	 */
	private void refreshProgress() {
		if (this.minecraft.player == null || this.levelValue == null) {
			return;
		}

		EssenceProgress progress = progress();

		// The cost curve is the server's, and arrives with the level settings — which is
		// why those are pushed to every client rather than held server side.
		LevelSettings settings = ClientLevelSettings.current();

		setCentredValue(this.levelValue,
				Component.translatable("screen.grandcraft.sheet.level", progress.level()));
		setCentredValue(this.essenceValue,
				Component.translatable("screen.grandcraft.sheet.essence",
						progress.essence(), progress.currentLevelCost(settings)));
	}

	// ------------------------------------------------------------------- tooltips

	private Component statTooltip(String key, double value) {
		String path = "screen.grandcraft.sheet." + key + ".tooltip";
		StatSettings settings = ClientStatSettings.current();

		return switch (key) {
			case "agility" -> Component.translatable(path,
					percent(settings.staminaCostMultiplier(value)));
			case "constitution" -> Component.translatable(path,
					signed(settings.armourBonus(value)),
					signed(settings.healthBonus(value)),
					percent(settings.staminaRegenMultiplier(value)));
			// Strength and Arcane are registered and shown but drive nothing yet, and
			// their tooltips say so rather than implying an effect nobody could find.
			default -> Component.translatable(path);
		};
	}

	// -------------------------------------------------------------- number format

	/** Trims a pointless ".0" so a whole value does not read like a measurement. */
	private static String exact(double value) {
		return value == Math.rint(value)
				? Long.toString((long) value)
				: String.format(Locale.ROOT, "%.1f", value);
	}

	private static String whole(double value) {
		return Long.toString(Math.round(value));
	}

	/** A bonus, which has to carry its sign to mean anything. */
	private static String signed(double value) {
		return value > 0.0 ? "+" + exact(value) : exact(value);
	}

	private static String percent(double multiplier) {
		return Long.toString(Math.round(multiplier * 100.0));
	}

	// ------------------------------------------------------------------- plumbing

	/** Returns the mutable form so a heading can be styled without a second lookup. */
	private static MutableComponent label(String key) {
		return Component.translatable("screen.grandcraft.sheet." + key);
	}

	/** This player's progression, read off the attachment the server syncs to them. */
	private EssenceProgress progress() {
		return this.minecraft.player.getAttachedOrElse(
				GrandCraftAttachments.ESSENCE_PROGRESS, EssenceProgress.NONE);
	}

	/**
	 * The four stat values as currently displayed, rounded the same way the rows round
	 * them — so a difference here means a difference on screen and nothing else.
	 */
	private StatBlock currentStats() {
		LocalPlayer player = this.minecraft.player;

		return new StatBlock(
				statValue(player, CharacterStat.STRENGTH),
				statValue(player, CharacterStat.AGILITY),
				statValue(player, CharacterStat.CONSTITUTION),
				statValue(player, CharacterStat.ARCANE));
	}

	private static int statValue(LocalPlayer player, CharacterStat stat) {
		return (int) Math.round(StatEffects.statOf(player, stat.attribute()));
	}

	/**
	 * Whether the panel is showing something that is no longer true in a way a rewrite
	 * cannot fix — a stat that moved, or a point gained or spent.
	 *
	 * <p>Gear granting a stat later lands here too, which is why the stat values are
	 * compared rather than only the spend record.
	 */
	private boolean panelIsStale() {
		EssenceProgress progress = progress();

		return progress.statPoints() != this.shownStatPoints
				|| progress.poolPoints() != this.shownPoolPoints
				|| !currentStats().equals(this.shownStats);
	}

	private int widestLabel() {
		int widest = 0;

		for (String key : PANEL_KEYS) {
			widest = Math.max(widest, this.font.width(label(key)));
		}

		return widest;
	}

	/**
	 * The model scale that fits the box, taken from vanilla's own ratio rather than
	 * guessed. Never below 1: zero would make the character vanish on a small window
	 * instead of merely being small.
	 */
	private int modelSize() {
		int width = this.modelRight - this.modelLeft;
		int height = this.modelBottom - this.contentTop;

		return Math.max(1, Math.min(
				width * MODEL_REFERENCE_SIZE / MODEL_REFERENCE_WIDTH,
				height * MODEL_REFERENCE_SIZE / MODEL_REFERENCE_HEIGHT));
	}

	/** Adds a widget exactly as wide as its text, left edge at {@code x}. */
	private StringWidget place(Component text, int x, int y) {
		StringWidget widget = new StringWidget(
				this.font.width(text), LINE_HEIGHT, text, this.font);
		widget.setX(x);
		widget.setY(y);
		addRenderableWidget(widget);
		return widget;
	}

	/** The same, but ending at the panel's right edge. */
	private StringWidget placeRight(Component text, int y) {
		return place(text, this.panelRight - this.font.width(text), y);
	}

	private StringWidget addCentred(Component text, int centreX, int y) {
		return place(text, centreX - this.font.width(text) / 2, y);
	}

	/** Rewrites a value and re-anchors it, since its width moves with the number. */
	private void setValue(StringWidget widget, Component text) {
		int width = this.font.width(text);
		widget.setMessage(text);
		widget.setWidth(width);
		widget.setX(this.panelRight - width);
	}

	/**
	 * The same, for a line that stays centred under the model rather than anchored to
	 * the panel's right edge. Re-centring on every write is what stops "Level 9"
	 * growing into "Level 10" and drifting sideways.
	 */
	private void setCentredValue(StringWidget widget, Component text) {
		int width = this.font.width(text);
		widget.setMessage(text);
		widget.setWidth(width);
		widget.setX(this.identityCentreX - width / 2);
	}

	// -------------------------------------------------------------- class picking

	private void initClassSelection() {
		addCentred(Component.translatable("screen.grandcraft.choose_class"),
				this.width / 2, CONTENT_TOP);

		int count = PlayerClass.SELECTABLE.size();
		int totalHeight = count * BUTTON_SPACING - (BUTTON_SPACING - BUTTON_HEIGHT);
		int y = (this.height - totalHeight) / 2;

		for (PlayerClass choice : PlayerClass.SELECTABLE) {
			addRenderableWidget(Button.builder(choice.displayName(), button -> selectClass(choice))
					.bounds((this.width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build());
			y += BUTTON_SPACING;
		}
	}

	private void selectClass(PlayerClass choice) {
		ClientPlayNetworking.send(new SelectClassPayload(choice));
		onClose();
	}

	// --------------------------------------------------------------------- render

	@Override
	public void tick() {
		super.tick();

		if (!this.classed || this.minecraft.player == null) {
			return;
		}

		// Rebuilt only when something structural moved — a point spent, a level gained,
		// a stat changed. Doing this every tick instead would take a tooltip out from
		// under the mouse continuously; doing it never would leave a spent point showing
		// the old number and its button still there.
		if (this.shownStats != null && panelIsStale()) {
			rebuildWidgets();
			return;
		}

		refreshPools();
		refreshProgress();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
			float partialTick) {
		// Guarded on having a class: without one this is the picker, and a divider down
		// the middle of it would be furniture from a screen that is not being shown.
		if (this.classed && this.minecraft.player != null) {
			extractor.fill(this.dividerX - 1, this.contentTop,
					this.dividerX + 1, this.contentBottom, DIVIDER_COLOUR);

			extractor.fill(this.dividerX + EDGE_PADDING, this.contentTop,
					this.width - EDGE_PADDING, this.contentBottom, PANEL_COLOUR);
			extractor.centeredText(this.font,
					Component.translatable("screen.grandcraft.sheet.coming_soon"),
					(this.dividerX + this.width) / 2,
					(this.contentTop + this.contentBottom - LINE_HEIGHT) / 2,
					COMING_SOON_COLOUR);

			InventoryScreen.extractEntityInInventoryFollowsMouse(extractor,
					this.modelLeft, this.contentTop, this.modelRight, this.modelBottom,
					this.modelSize, MODEL_Y_OFFSET, mouseX, mouseY, this.minecraft.player);
		}

		// Last, so the widgets sit on top of the panel and the model.
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractTransparentBackground(GuiGraphicsExtractor extractor) {
		// Vanilla uses 0xC0101010 -> 0xD0101010; use a darker, more opaque black.
		// The engine allows only one blur pass per frame, so darkness is the
		// only per-screen way to strengthen the background dimming.
		extractor.fillGradient(0, 0, this.width, this.height, 0xD0000000, 0xE0000000);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
