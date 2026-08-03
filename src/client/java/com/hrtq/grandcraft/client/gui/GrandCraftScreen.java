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
import net.minecraft.client.gui.components.MultiLineTextWidget;
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
 * <p>One screen, always the same shape: the character on the left with their class,
 * Essence Power level and progress underneath, their stats and pools beside them, and
 * a panel filling the right half.
 *
 * <h2>The right half does the class picking</h2>
 * There is no separate picker screen any more. An unclassed character gets a class
 * browser in that panel — one class at a time, arrows either side — and choosing turns
 * the panel into the placeholder for the skill-lines that will eventually live there.
 *
 * <p>The point of browsing in place is that the <em>left half previews the choice</em>:
 * the name under the model and every stat row read {@code current → what it would
 * become}, so the arrows compare characters rather than paging through four words. The
 * preview is a display concern only — nothing is sent until Choose is pressed, and
 * nothing is predicted after it either. See {@link #previewDelta}.
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
 * numbers are bare, and hovering is what explains them. The divider, the model and the
 * panel background are drawn directly in {@link #extractRenderState}, since none of
 * them answer the mouse.
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

	private static final int BUTTON_HEIGHT = 20;

	/** Inset of the class browser's contents from the panel's own edges. */
	private static final int PANEL_INSET = 10;

	private static final int ARROW_SIZE = 20;

	/**
	 * Distance from the panel's centre line to the inner edge of each arrow.
	 *
	 * <p>Fixed rather than derived from the name's width on purpose: an arrow that sits
	 * a set distance from the longest name would jump inwards on "Cleric" and outwards
	 * on "Sorcerer", so cycling would move the very buttons being cycled with. Wide
	 * enough to clear the longest class name at a comfortable margin.
	 */
	private static final int ARROW_OFFSET = 44;

	private static final int CHOOSE_BUTTON_WIDTH = 100;

	/** Shown for a pool the server has not reported, or has switched off entirely. */
	private static final Component MISSING = Component.literal("—");

	/** Every label on the panel, so the widest of them can size the value column. */
	private static final String[] PANEL_KEYS = {
			"stats", "strength", "agility", "constitution", "arcane",
			"attributes", "health", "stamina", "mana" };

	private boolean classed;

	/**
	 * Which class the browser is showing, as an index into
	 * {@link PlayerClass#SELECTABLE}.
	 *
	 * <p>Deliberately <em>not</em> cleared by {@link #init}: pressing an arrow rebuilds
	 * the whole screen, so resetting it there would snap the browser back to the first
	 * class on every press and make the arrows do nothing. It survives a resize for the
	 * same reason.
	 */
	private int previewIndex;

	private int dividerX;
	private int contentTop;
	private int contentBottom;
	private int modelLeft;
	private int modelRight;
	private int modelBottom;
	private int modelSize;
	private int panelRight;

	/** The right-hand panel: browser while unclassed, placeholder once classed. */
	private int rightPanelLeft;
	private int rightPanelRight;
	private int rightPanelCentre;

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

	/**
	 * The class the screen was built for.
	 *
	 * <p>Watched by {@link #tick} because choosing a class is not predicted: the button
	 * only sends, and the change arrives a tick or two later through the attachment's
	 * own sync. This is what turns the browser into the placeholder when it lands.
	 */
	private PlayerClass shownClass;

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

		this.shownClass = this.minecraft.player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);
		this.classed = this.shownClass != PlayerClass.PEASANT;

		initSheet(this.shownClass);
	}

	// ------------------------------------------------------------------ the sheet

	private void initSheet(PlayerClass playerClass) {
		this.dividerX = this.width / 2;
		this.contentTop = CONTENT_TOP;
		this.contentBottom = this.height - BOTTOM_MARGIN;

		this.rightPanelLeft = this.dividerX + EDGE_PADDING;
		this.rightPanelRight = this.width - EDGE_PADDING;
		this.rightPanelCentre = (this.rightPanelLeft + this.rightPanelRight) / 2;

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
		this.identityCentreX = identityCentreX();

		buildIdentity(playerClass);
		buildPanel(panelLeft);

		if (!this.classed) {
			buildClassBrowser();
		}
	}

	/**
	 * Who the character is, under the picture: class, then Essence Power level, then
	 * how far through the current level they are.
	 *
	 * <p>The two progression lines start empty and are filled by
	 * {@link #refreshProgress}, so there is one place that decides how they read
	 * rather than an initial version and an updating version that could disagree.
	 *
	 * <p>While browsing, the class line reads {@code Peasant → Warrior} in the same
	 * grey-to-green form the stat rows use, so the whole left column says "this is what
	 * you would become" in one voice rather than two.
	 */
	private void buildIdentity(PlayerClass playerClass) {
		int y = this.modelBottom + SECTION_GAP;

		addCentred(identityName(), this.identityCentreX, y);

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
	 *
	 * <p>While browsing, the number becomes {@code 5 → 14} and the tooltip describes the
	 * value on the <em>right</em> of that arrow — the point of hovering mid-browse is to
	 * find out what the class would buy you, not to be told again what you already have.
	 */
	private int addStatRow(CharacterStat stat, int panelLeft, int y) {
		String key = stat.translationKey();
		double value = StatEffects.statOf(this.minecraft.player, stat.attribute());
		double described = value;
		Component reading = Component.literal(whole(value));

		if (previewing()) {
			described = value + previewDelta(stat);
			reading = preview(Component.literal(whole(value)), Component.literal(whole(described)));
		}

		StringWidget name = place(label(key), panelLeft, y);
		StringWidget number = placeRight(reading, y);

		// On both halves of the row, so hovering anywhere along it works.
		Tooltip tooltip = Tooltip.create(statTooltip(key, described));
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

		setCentredValue(this.levelValue, levelText());
		setCentredValue(this.essenceValue, essenceText());
	}

	/**
	 * The class line: plain once chosen, {@code Peasant → Warrior} while browsing.
	 *
	 * <p>Built here rather than inline so {@link #identityCentreX} can measure the same
	 * line that will actually be drawn — the preview form is roughly twice the width of
	 * a bare class name, and that difference is exactly what has to be laid out for.
	 */
	private Component identityName() {
		return previewing()
				? preview(this.shownClass.displayName(), previewClass().displayName())
				: this.shownClass.displayName().copy().withStyle(ChatFormatting.YELLOW);
	}

	private Component levelText() {
		return Component.translatable("screen.grandcraft.sheet.level", progress().level());
	}

	private Component essenceText() {
		EssenceProgress progress = progress();

		// The cost curve is the server's, and arrives with the level settings — which is
		// why those are pushed to every client rather than held server side.
		LevelSettings settings = ClientLevelSettings.current();

		return Component.translatable("screen.grandcraft.sheet.essence",
				progress.essence(), progress.currentLevelCost(settings));
	}

	/**
	 * Where the block under the model is centred.
	 *
	 * <p>Under the model, but <strong>never starting before the screen edge</strong>.
	 * The model column is the part of the layout that gives way — the panel is sized
	 * from its own labels and anchored to the divider, and the model takes what is left
	 * — so at a small window or a large GUI scale that column collapses to a few dozen
	 * pixels while the lines written underneath it do not get any shorter. Centring
	 * blindly in it walked them off the left of the screen, which is what this clamps.
	 *
	 * <p>Measured from the widest of the three lines, so they stay centred on each other
	 * rather than each being pushed a different distance. The push is only ever
	 * rightwards, into the gap below the panel, which is empty at this height.
	 */
	private int identityCentreX() {
		int half = Math.max(this.font.width(identityName()),
				Math.max(this.font.width(levelText()), this.font.width(essenceText()))) / 2;

		int centred = (this.modelLeft + this.modelRight) / 2;
		int rightmost = this.dividerX - EDGE_PADDING - half;

		return Math.min(Math.max(centred, EDGE_PADDING + half), Math.max(rightmost, EDGE_PADDING + half));
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
			// Strength and Arcane describe their effects in prose rather than with live
			// figures, because what either is worth depends on the weapon in hand — a
			// claymore reads Strength alone, a sword only partly. The honest place for a
			// number is the item tooltip, which knows which weapon is being asked about.
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

		return actualClass() != this.shownClass
				|| progress.statPoints() != this.shownStatPoints
				|| progress.poolPoints() != this.shownPoolPoints
				|| !currentStats().equals(this.shownStats);
	}

	private PlayerClass actualClass() {
		return this.minecraft.player.getAttachedOrElse(
				GrandCraftAttachments.PLAYER_CLASS, PlayerClass.PEASANT);
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

	// -------------------------------------------------------------- class browsing

	/**
	 * The class browser that fills the right panel until a class is chosen: one class
	 * at a time, an arrow either side, and the button that commits.
	 *
	 * <p>Laid out from <em>both</em> ends. The heading, name and description hang from
	 * the top; Choose and the permanence line are anchored to the bottom. That is what
	 * keeps the button still while cycling — the description is the only part whose
	 * height varies, and growing it downwards would otherwise walk the button around
	 * under the mouse.
	 */
	private void buildClassBrowser() {
		PlayerClass choice = previewClass();
		int centre = this.rightPanelCentre;

		addCentred(Component.translatable("screen.grandcraft.choose_class")
				.withStyle(ChatFormatting.GOLD), centre, this.contentTop + PANEL_INSET);

		int arrowY = this.contentTop + PANEL_INSET + ROW_HEIGHT + SECTION_GAP;

		addRenderableWidget(Button.builder(
						Component.translatable("screen.grandcraft.class_previous"),
						pressed -> cycle(-1))
				.bounds(centre - ARROW_OFFSET - ARROW_SIZE, arrowY, ARROW_SIZE, ARROW_SIZE)
				.build());

		addRenderableWidget(Button.builder(
						Component.translatable("screen.grandcraft.class_next"),
						pressed -> cycle(1))
				.bounds(centre + ARROW_OFFSET, arrowY, ARROW_SIZE, ARROW_SIZE)
				.build());

		// Centred against the arrows rather than sitting on their top edge.
		addCentred(choice.displayName().copy().withStyle(ChatFormatting.YELLOW),
				centre, arrowY + (ARROW_SIZE - LINE_HEIGHT) / 2);

		int permanentY = this.contentBottom - PANEL_INSET - LINE_HEIGHT;
		int chooseY = permanentY - SECTION_GAP - BUTTON_HEIGHT;

		addCentred(Component.translatable("screen.grandcraft.class_permanent")
				.withStyle(ChatFormatting.DARK_GRAY), centre, permanentY);

		addRenderableWidget(Button.builder(
						Component.translatable("screen.grandcraft.class_choose"),
						pressed -> selectClass(choice))
				.bounds(centre - CHOOSE_BUTTON_WIDTH / 2, chooseY,
						CHOOSE_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());

		// Built last because it is the one part sized by its content, so it is the one
		// that has to be told how much room the fixed furniture left it.
		int descriptionTop = arrowY + ARROW_SIZE + SECTION_GAP * 2;
		addDescription(choice, centre, descriptionTop, chooseY - SECTION_GAP - descriptionTop);
	}

	/**
	 * The class blurb, wrapped to the panel.
	 *
	 * <p>{@code setCentered} centres each line around {@code getX() + getWidth() / 2},
	 * where the width is the <em>text block's</em> and not {@code maxWidth} — so the
	 * block still has to be positioned by hand, exactly as every other widget on this
	 * screen is. Sizing it first and placing it second is the whole trick.
	 *
	 * <p>Capped to the rows that actually fit. On a small window the blurb would
	 * otherwise run down through the Choose button; losing the tail of a sentence reads
	 * as a window too small for it, where text printed over a button reads as broken.
	 */
	private void addDescription(PlayerClass choice, int centre, int y, int available) {
		MultiLineTextWidget description = new MultiLineTextWidget(
				choice.description().copy().withStyle(ChatFormatting.GRAY), this.font);

		description.setMaxWidth(this.rightPanelRight - this.rightPanelLeft - PANEL_INSET * 2);
		description.setMaxRows(Math.max(1, available / LINE_HEIGHT));
		description.setCentered(true);
		description.setY(y);
		description.setX(centre - description.getWidth() / 2);

		addRenderableWidget(description);
	}

	/**
	 * Moves the browser one class along, wrapping in both directions — a cycle with ends
	 * would need its arrows disabling at them, and there is no reason for four choices
	 * to have a first and a last.
	 */
	private void cycle(int step) {
		this.previewIndex = Math.floorMod(this.previewIndex + step, PlayerClass.SELECTABLE.size());
		rebuildWidgets();
	}

	/**
	 * Commits the choice.
	 *
	 * <p>The screen stays open and nothing changes on it yet: the request goes to the
	 * server, and the class comes back through the attachment's own sync — which
	 * {@link #tick} is watching for. Same contract as a spend button, and it means a
	 * refused choice simply leaves the browser where it was.
	 */
	private void selectClass(PlayerClass choice) {
		ClientPlayNetworking.send(new SelectClassPayload(choice));
	}

	/** The class the browser is currently showing. */
	private PlayerClass previewClass() {
		return PlayerClass.SELECTABLE.get(
				Math.floorMod(this.previewIndex, PlayerClass.SELECTABLE.size()));
	}

	private boolean previewing() {
		return !this.classed;
	}

	/**
	 * How far a stat would move if the browsed class were chosen.
	 *
	 * <p>Taken as the difference between the two classes' baselines rather than by
	 * building the target value outright, because the server's own sum is
	 * {@code baseline + points spent} — and later, gear on top. A delta rides along with
	 * everything else already in the number, so the preview cannot drift from what
	 * {@code PlayerStats.applyBaselines} would actually write.
	 */
	private int previewDelta(CharacterStat stat) {
		return previewClass().baseStats().get(stat) - this.shownClass.baseStats().get(stat);
	}

	/** A value and what it would become: current in grey, target in green. */
	private static Component preview(Component current, Component target) {
		return Component.translatable("screen.grandcraft.sheet.preview",
				current.copy().withStyle(ChatFormatting.GRAY),
				target.copy().withStyle(ChatFormatting.GREEN));
	}

	// --------------------------------------------------------------------- render

	@Override
	public void tick() {
		super.tick();

		if (this.minecraft.player == null) {
			return;
		}

		// Rebuilt only when something structural moved — a point spent, a level gained,
		// a stat changed, a class chosen. Doing this every tick instead would take a
		// tooltip out from under the mouse continuously; doing it never would leave a
		// spent point showing the old number and its button still there.
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
		if (this.minecraft.player != null) {
			extractor.fill(this.dividerX - 1, this.contentTop,
					this.dividerX + 1, this.contentBottom, DIVIDER_COLOUR);

			extractor.fill(this.rightPanelLeft, this.contentTop,
					this.rightPanelRight, this.contentBottom, PANEL_COLOUR);

			// The panel holds the class browser until a class is chosen; the placeholder
			// is what it reverts to afterwards, naming what will eventually fill it.
			if (this.classed) {
				extractor.centeredText(this.font,
						Component.translatable("screen.grandcraft.sheet.coming_soon"),
						this.rightPanelCentre,
						(this.contentTop + this.contentBottom - LINE_HEIGHT) / 2,
						COMING_SOON_COLOUR);
			}

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
