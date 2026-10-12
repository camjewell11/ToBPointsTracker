package com.camjewell.tobpointtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
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

		StringBuilder sb = new StringBuilder()
			.append(String.format("%s Mode, %d-man", score.getMode().getDisplayName(), score.getTeamSize()))
			.append(String.format("</br>Team points: %d / %d", score.getTeamPoints(), score.getMaxPoints()));
		if (!score.isComplete() && score.getTotalDeaths() > 0)
		{
			sb.append(String.format("</br>If no more deaths: %d / %d", score.getProjectedTeamPoints(), score.getMaxPoints()));
		}

		for (PointCalculator.PlayerScore p : score.getPlayers())
		{
			sb.append("</br>").append(p.getName()).append(": ").append(formatPoints(p));
		}

		for (PointCalculator.PlayerScore p : score.getPlayers())
		{
			if (p.isLocal())
			{
				appendMvpOutcomes(sb, p);
			}
		}

		tooltipManager.add(new Tooltip(sb.toString()));
	}

	/**
	 * e.g. "18 room + 2-6 MVP - 4 (1 death) = 16-20 pts"
	 */
	private static String formatPoints(PointCalculator.PlayerScore p)
	{
		StringBuilder sb = new StringBuilder().append(p.getRoomPoints()).append(" room + ");
		if (p.getMvpUnknown() > 0)
		{
			sb.append(ColorUtil.wrapWithColorTag(p.getMvpWon() + "-" + (p.getMvpWon() + p.getMvpUnknown()), UNKNOWN_COLOR));
		}
		else
		{
			sb.append(p.getMvpWon());
		}
		sb.append(" MVP");

		if (p.getDeaths() > 0)
		{
			sb.append(ColorUtil.wrapWithColorTag(String.format(" - %d (%d death%s)",
				p.getDeaths() * Segment.DEATH_PENALTY, p.getDeaths(), p.getDeaths() == 1 ? "" : "s"), DEATH_COLOR));
		}

		sb.append(" = ").append(p.isExact() ? String.valueOf(p.getMinPoints()) : p.getMinPoints() + "-" + p.getMaxPoints())
			.append(" pts");
		return sb.toString();
	}

	private static void appendMvpOutcomes(StringBuilder sb, PointCalculator.PlayerScore p)
	{
		StringBuilder line = new StringBuilder();
		for (Segment segment : Segment.values())
		{
			MvpOutcome outcome = p.getOutcomes()[segment.ordinal()];
			if (outcome == null)
			{
				continue;
			}

			if (line.length() > 0)
			{
				line.append(", ");
			}
			switch (outcome)
			{
				case WON:
					line.append(ColorUtil.wrapWithColorTag(segment.getDisplayName(), WON_COLOR));
					break;
				case LOST:
					line.append(ColorUtil.wrapWithColorTag(segment.getDisplayName(), DEATH_COLOR));
					break;
				default:
					line.append(ColorUtil.wrapWithColorTag(segment.getDisplayName() + "?", UNKNOWN_COLOR));
			}
		}

		if (line.length() > 0)
		{
			sb.append("</br>Your MVPs: ").append(line);
		}
	}
}
