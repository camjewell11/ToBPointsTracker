package com.camjewell.tobpointtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;

/**
 * Laid out like the Raid Points Overlay plugin's CoX overlay, but showing what actually changes a ToB unique chance:
 * deaths, your purple chance, and the team's. Point breakdowns are on hover.
 * <p>
 * Your chance is a range when MVP points can't be proven without teammates' damage, and an exact value otherwise.
 * Teammates only get their own line when they share damage through the party.
 */
class ToBPointTrackerOverlay extends OverlayPanel
{
	private static final DecimalFormat CHANCE_FORMAT = new DecimalFormat("#0.00%");
	private static final DecimalFormat CHANCE_RANGE_LOW_FORMAT = new DecimalFormat("#0.00");

	private static final Color DEATH_COLOR = Color.RED;
	private static final Color WON_COLOR = Color.GREEN;
	private static final Color UNKNOWN_COLOR = Color.LIGHT_GRAY;
	private static final Color HEADER_COLOR = ColorScheme.BRAND_ORANGE;

	private static final String BR = "</br>";
	private static final String INDENT = "    ";

	private final Client client;
	private final ToBPointTrackerPlugin plugin;
	private final ToBPointTrackerConfig config;
	private final TooltipManager tooltipManager;

	@Inject
	private ToBPointTrackerOverlay(Client client, ToBPointTrackerPlugin plugin, ToBPointTrackerConfig config,
		TooltipManager tooltipManager)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(Overlay.PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || !plugin.isInRaid())
		{
			return null;
		}

		PointCalculator.RaidScore score = plugin.getScore();
		if (score == null)
		{
			return null;
		}

		// Deaths are the only thing that lowers the team's final points
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Deaths:")
			.right(String.valueOf(score.getTotalDeaths()))
			.rightColor(score.getTotalDeaths() > 0 ? DEATH_COLOR : Color.WHITE)
			.build());

		UniqueChanceDisplay display = config.uniqueChance();
		boolean canRoll = score.getMode().canRollUniques();

		if (canRoll && (display == UniqueChanceDisplay.PERSONAL_CHANCE || display == UniqueChanceDisplay.BOTH))
		{
			for (PointCalculator.PlayerScore p : score.getPlayers())
			{
				if (p.isLocal())
				{
					addPlayerLine(p);
				}
			}
			if (config.showPlayers())
			{
				for (PointCalculator.PlayerScore p : score.getPlayers())
				{
					if (!p.isLocal() && p.isDamageKnown())
					{
						addPlayerLine(p);
					}
				}
			}
		}

		if (display == UniqueChanceDisplay.TEAM_CHANCE || display == UniqueChanceDisplay.BOTH)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Team:")
				.right(canRoll ? CHANCE_FORMAT.format(score.getTeamUniqueChance()) : "N/A (Entry)")
				.build());
		}

		Dimension dimension = super.render(graphics);
		addTooltip(score);
		return dimension;
	}

	private void addPlayerLine(PointCalculator.PlayerScore p)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(p.getName() + ":")
			.right(formatChance(p))
			.rightColor(p.getDeaths() > 0 ? DEATH_COLOR : Color.WHITE)
			.build());
	}

	/**
	 * A player's chance as an exact value, or "low-high%" while some of their MVP points can't be proven.
	 */
	static String formatChance(PointCalculator.PlayerScore p)
	{
		if (p.isExact())
		{
			return CHANCE_FORMAT.format(p.getMinUniqueChance());
		}
		return CHANCE_RANGE_LOW_FORMAT.format(p.getMinUniqueChance() * 100) + "-"
			+ CHANCE_FORMAT.format(p.getMaxUniqueChance());
	}

	private void addTooltip(PointCalculator.RaidScore score)
	{
		Rectangle bounds = getBounds();
		Point mouse = client.getMouseCanvasPosition();
		if (bounds == null || mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}

		StringBuilder sb = new StringBuilder();
		header(sb, String.format("%s Mode, %d players", score.getMode().getDisplayName(), score.getTeamSize()));
		sb.append(BR).append(String.format("Team points: %d / %d", score.getTeamPoints(), score.getMaxPoints()));
		if (!score.isComplete() && score.getTotalDeaths() > 0)
		{
			sb.append(BR).append(detail(String.format("%d / %d if no one else dies",
				score.getProjectedTeamPoints(), score.getMaxPoints())));
		}

		sb.append(BR).append(BR);
		header(sb, "Player points");
		for (PointCalculator.PlayerScore p : score.getPlayers())
		{
			appendPlayerPoints(sb, p);
		}

		for (PointCalculator.PlayerScore p : score.getPlayers())
		{
			if (p.isLocal() && score.getMode().canRollUniques())
			{
				appendMvpOutcomes(sb, p);
			}
		}

		tooltipManager.add(new Tooltip(sb.toString()));
	}

	private static void header(StringBuilder sb, String text)
	{
		sb.append(ColorUtil.wrapWithColorTag(text, HEADER_COLOR));
	}

	private static String detail(String text)
	{
		return ColorUtil.wrapWithColorTag(INDENT + text, UNKNOWN_COLOR);
	}

	/**
	 * e.g.
	 * <pre>
	 * ThePrestiGIM: 16-20
	 *     18 rooms + 2-6 MVP - 4 for 1 death
	 * </pre>
	 */
	private static void appendPlayerPoints(StringBuilder sb, PointCalculator.PlayerScore p)
	{
		String points = p.isExact() ? String.valueOf(p.getMinPoints()) : p.getMinPoints() + "-" + p.getMaxPoints();
		sb.append(BR).append(p.getName()).append(": ")
			.append(ColorUtil.wrapWithColorTag(points, p.getDeaths() > 0 ? DEATH_COLOR : Color.WHITE));
		if (!p.isDamageKnown())
		{
			sb.append(ColorUtil.wrapWithColorTag(" (no plugin)", UNKNOWN_COLOR));
		}

		StringBuilder breakdown = new StringBuilder()
			.append(p.getRoomPoints()).append(" rooms + ");
		if (p.getMvpUnknown() > 0)
		{
			breakdown.append(p.getMvpWon()).append("-").append(p.getMvpWon() + p.getMvpUnknown());
		}
		else
		{
			breakdown.append(p.getMvpWon());
		}
		breakdown.append(" MVP");
		if (p.getDeaths() > 0)
		{
			breakdown.append(String.format(" - %d for %d death%s",
				p.getDeaths() * Segment.DEATH_PENALTY, p.getDeaths(), p.getDeaths() == 1 ? "" : "s"));
		}
		sb.append(BR).append(detail(breakdown.toString()));
	}

	/**
	 * Lists completed segments grouped by whether you provably won, provably lost, or can't tell their MVP points.
	 */
	private static void appendMvpOutcomes(StringBuilder sb, PointCalculator.PlayerScore p)
	{
		StringBuilder won = new StringBuilder();
		StringBuilder lost = new StringBuilder();
		StringBuilder unknown = new StringBuilder();
		for (Segment segment : Segment.values())
		{
			MvpOutcome outcome = p.getOutcomes()[segment.ordinal()];
			if (outcome == null)
			{
				continue;
			}

			StringBuilder list = outcome == MvpOutcome.WON ? won : outcome == MvpOutcome.LOST ? lost : unknown;
			if (list.length() > 0)
			{
				list.append(", ");
			}
			list.append(segment.getDisplayName()).append(" (").append(segment.getMvpPoints()).append(")");
		}

		if (won.length() + lost.length() + unknown.length() == 0)
		{
			return;
		}

		sb.append(BR).append(BR);
		header(sb, "Your MVPs");
		appendOutcomeLine(sb, "Won", won, WON_COLOR);
		appendOutcomeLine(sb, "Lost", lost, DEATH_COLOR);
		appendOutcomeLine(sb, "Unknown", unknown, UNKNOWN_COLOR);
	}

	private static void appendOutcomeLine(StringBuilder sb, String label, StringBuilder segments, Color color)
	{
		if (segments.length() > 0)
		{
			sb.append(BR).append(ColorUtil.wrapWithColorTag(label + ": ", color)).append(segments);
		}
	}
}
