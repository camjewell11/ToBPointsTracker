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
 * Laid out like the Raid Points Overlay plugin's CoX overlay: Total, your points, team size and unique chance, with a
 * breakdown on hover. Teammates only get their own line when they share damage through the party, since without it their
 * MVP points can't be known; they're left grouped in Total instead. Values that rely on an MVP estimate are prefixed with
 * {@link #ESTIMATE_PREFIX}.
 */
class ToBPointTrackerOverlay extends OverlayPanel
{
	private static final DecimalFormat POINTS_FORMAT = new DecimalFormat("#,###");
	private static final DecimalFormat POINTS_PERCENT_FORMAT = new DecimalFormat(" (##0.00%)");
	private static final DecimalFormat UNIQUE_FORMAT = new DecimalFormat("#0.00%");

	private static final String ESTIMATE_PREFIX = "~";

	private static final Color DEATH_COLOR = Color.RED;
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

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Total:")
			.right(POINTS_FORMAT.format(score.getTeamPoints()))
			.build());

		PointCalculator.PlayerScore local = null;
		for (PointCalculator.PlayerScore p : score.getPlayers())
		{
			if (p.isLocal())
			{
				local = p;
				addPlayerLine(score, p);
			}
		}
		if (config.showPlayers())
		{
			for (PointCalculator.PlayerScore p : score.getPlayers())
			{
				if (!p.isLocal() && p.isDamageKnown())
				{
					addPlayerLine(score, p);
				}
			}
		}

		if (score.getTeamSize() > 1 && config.showTeamSize())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Party size:")
				.right(String.valueOf(score.getTeamSize()))
				.build());
		}

		String unique = uniqueText(score, local);
		if (unique != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Unique:")
				.right(unique)
				.build());
		}

		Dimension dimension = super.render(graphics);
		addTooltip(score);
		return dimension;
	}

	private void addPlayerLine(PointCalculator.RaidScore score, PointCalculator.PlayerScore p)
	{
		String right = (p.isEstimated() ? ESTIMATE_PREFIX : "") + POINTS_FORMAT.format(p.getPoints());
		if (config.pointsPercent() && score.getTeamSize() > 1)
		{
			right += POINTS_PERCENT_FORMAT.format(p.getShare());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left(p.getName() + ":")
			.right(right)
			.rightColor(p.getDeaths() > 0 ? DEATH_COLOR : Color.WHITE)
			.build());
	}

	/**
	 * @return the Unique line's text, or null to hide it
	 */
	private String uniqueText(PointCalculator.RaidScore score, PointCalculator.PlayerScore local)
	{
		UniqueChanceDisplay display = config.uniqueChance();
		if (display == UniqueChanceDisplay.OFF)
		{
			return null;
		}
		if (!score.getMode().canRollUniques())
		{
			return "N/A (Entry)";
		}

		String team = UNIQUE_FORMAT.format(score.getTeamUniqueChance());
		if (local == null)
		{
			// Spectating, so there's no personal chance
			return team;
		}

		String personal = (local.isEstimated() ? ESTIMATE_PREFIX : "") + UNIQUE_FORMAT.format(local.getUniqueChance());
		switch (display)
		{
			case PERSONAL_CHANCE:
				return personal;
			case TEAM_CHANCE:
				return team;
			default:
				return score.getTeamSize() > 1 ? personal + " (" + team + ")" : personal;
		}
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
			.append(String.format("</br>Team: %d / %d max", score.getTeamPoints(), score.getMaxPoints()));
		if (!score.isComplete())
		{
			sb.append(String.format("</br>If no more deaths: %d / %d", score.getProjectedTeamPoints(), score.getMaxPoints()));
		}

		for (PointCalculator.PlayerScore p : score.getPlayers())
		{
			sb.append("</br>").append(p.getName());
			if (!p.isDamageKnown())
			{
				// We can't know their MVP points, only what they've lost to deaths
				sb.append(String.format(": %d room", p.getRoomPoints()));
				if (p.getDeaths() > 0)
				{
					sb.append(ColorUtil.wrapWithColorTag(String.format(" - %d (%d death%s)",
						p.getDeaths() * Segment.DEATH_PENALTY, p.getDeaths(), p.getDeaths() == 1 ? "" : "s"), DEATH_COLOR));
				}
				sb.append(ColorUtil.wrapWithColorTag(" + MVP? (no party data)", UNKNOWN_COLOR));
				continue;
			}

			sb.append(String.format(": %d room + %s%d MVP", p.getRoomPoints(),
				p.isEstimated() ? ESTIMATE_PREFIX : "", p.getMvpPoints()));
			if (p.getDeaths() > 0)
			{
				sb.append(ColorUtil.wrapWithColorTag(
					String.format(" - %d (%d death%s)", p.getDeaths() * Segment.DEATH_PENALTY, p.getDeaths(),
						p.getDeaths() == 1 ? "" : "s"),
					DEATH_COLOR));
			}
			sb.append(String.format(" = %s%d", p.isEstimated() ? ESTIMATE_PREFIX : "", p.getPoints()));
		}

		if (score.getEstimatedMvpPoints() > 0)
		{
			sb.append(ColorUtil.wrapWithColorTag(String.format(
				"</br>~ %d MVP points estimated: teammates without the plugin are assumed to split their damage evenly",
				score.getEstimatedMvpPoints()), UNKNOWN_COLOR));
		}

		tooltipManager.add(new Tooltip(sb.toString()));
	}
}
