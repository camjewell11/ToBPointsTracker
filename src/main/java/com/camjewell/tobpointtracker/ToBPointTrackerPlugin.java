package com.camjewell.tobpointtracker;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "ToB Point Tracker",
	description = "Tracks individual and team Theatre of Blood points and your purple (unique) chance",
	tags = {"tob", "theatre", "blood", "raid", "points", "purple", "unique", "mvp"}
)
public class ToBPointTrackerPlugin extends Plugin
{
	// TOB_CLIENT_PARTYSTATUS: 0 = no party, 1 = in a party, 2 = inside the raid, 3 = dead and spectating
	private static final int STATUS_INSIDE = 2;
	private static final int STATUS_SPECTATING = 3;

	// TOB_CLIENT_P0-P4 party orb values: 0 = empty slot, 1-27 = scaled health, 30 = dead
	private static final int ORB_MAX_ALIVE = 27;
	private static final int ORB_DEAD = 30;

	private static final int[] NAME_VARCS = {
		VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME1, VarClientID.TOB_CLIENT_NAME2,
		VarClientID.TOB_CLIENT_NAME3, VarClientID.TOB_CLIENT_NAME4,
	};
	private static final int[] ORB_VARBITS = {
		VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2,
		VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4,
	};

	// e.g. "Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: 2:05.40"
	private static final Pattern WAVE_COMPLETE = Pattern.compile("Wave '(.+?)' \\((\\w+) Mode\\) complete!");

	// Give hitsplats landing on the killing tick a moment before sharing damage
	private static final int SHARE_DELAY_TICKS = 2;
	// Give teammates' final damage updates time to arrive before summarising
	private static final int SUMMARY_DELAY_TICKS = 5;
	// A relog this soon after leaving resumes the raid instead of starting a new one
	private static final Duration RESUME_WINDOW = Duration.ofMinutes(10);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ToBPointTrackerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ToBPointTrackerOverlay overlay;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private ChatMessageManager chatMessageManager;

	private RaidState raid;
	private PointCalculator.RaidScore score;
	private boolean inRaid;
	private boolean leftViaLogout;
	private Instant leftRaidAt;
	private final int[] previousOrbs = new int[ORB_VARBITS.length];
	private int shareTicks = -1;
	private int summaryTicks = -1;

	@Provides
	ToBPointTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ToBPointTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		wsClient.registerMessage(ToBDamageUpdate.class);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		wsClient.unregisterMessage(ToBDamageUpdate.class);
		raid = null;
		score = null;
		inRaid = false;
		shareTicks = -1;
		summaryTicks = -1;
	}

	boolean isInRaid()
	{
		return inRaid && raid != null;
	}

	/**
	 * The latest score for the current raid, or null outside of one. Called from the overlay on the client thread.
	 */
	PointCalculator.RaidScore getScore()
	{
		if (raid == null)
		{
			return null;
		}
		if (score == null)
		{
			score = PointCalculator.calculate(raid, config.chanceBasis());
		}
		return score;
	}

	private void invalidate()
	{
		score = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ToBPointTrackerConfig.GROUP.equals(event.getGroup()))
		{
			clientThread.invokeLater(this::invalidate);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				if (inRaid)
				{
					leaveRaid(true);
				}
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int status = client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS);
		boolean nowInRaid = status == STATUS_INSIDE || status == STATUS_SPECTATING;
		if (nowInRaid && !inRaid)
		{
			enterRaid();
		}
		else if (!nowInRaid && inRaid)
		{
			leaveRaid(false);
		}

		if (!isInRaid())
		{
			return;
		}

		updateTeam();
		checkOrbs();

		if (shareTicks >= 0 && shareTicks-- == 0)
		{
			shareDamage();
		}
		if (summaryTicks >= 0 && summaryTicks-- == 0)
		{
			postSummary();
		}
	}

	private void enterRaid()
	{
		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		String localKey = Text.standardize(local.getName());
		boolean resume = raid != null
			&& !raid.isComplete()
			&& leftViaLogout
			&& leftRaidAt != null
			&& Duration.between(leftRaidAt, Instant.now()).compareTo(RESUME_WINDOW) < 0
			&& raid.getLocalName().equals(localKey);

		if (!resume)
		{
			log.debug("Starting new ToB raid tracking");
			raid = new RaidState(localKey);
			invalidate();
		}

		inRaid = true;
		// Don't treat orbs that are already dead when we (re)join as fresh deaths
		for (int i = 0; i < ORB_VARBITS.length; i++)
		{
			previousOrbs[i] = client.getVarbitValue(ORB_VARBITS[i]);
		}
	}

	private void leaveRaid(boolean viaLogout)
	{
		inRaid = false;
		leftViaLogout = viaLogout;
		leftRaidAt = Instant.now();
		shareTicks = -1;
		summaryTicks = -1;
	}

	private void updateTeam()
	{
		int before = raid.getTeam().size();
		for (int varc : NAME_VARCS)
		{
			String name = cleanName(client.getVarcStrValue(varc));
			if (!name.isEmpty())
			{
				raid.addTeamMember(Text.standardize(name), name);
			}
		}
		if (raid.getTeam().size() != before)
		{
			invalidate();
		}
	}

	/**
	 * Backup death detection for players we can't see die, using the party health orbs.
	 */
	private void checkOrbs()
	{
		for (int i = 0; i < ORB_VARBITS.length; i++)
		{
			int orb = client.getVarbitValue(ORB_VARBITS[i]);
			int previous = previousOrbs[i];
			previousOrbs[i] = orb;

			boolean alive = orb > 0 && orb <= ORB_MAX_ALIVE;
			boolean wasAlive = previous > 0 && previous <= ORB_MAX_ALIVE;
			if (orb == ORB_DEAD && wasAlive || alive && previous == ORB_DEAD)
			{
				String name = cleanName(client.getVarcStrValue(NAME_VARCS[i]));
				if (name.isEmpty())
				{
					continue;
				}

				if (alive)
				{
					// Respawned for the next room
					raid.markAlive(Text.standardize(name));
				}
				else
				{
					recordDeath(name);
				}
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!isInRaid())
		{
			return;
		}

		Actor actor = event.getActor();
		if (actor instanceof Player && actor.getName() != null)
		{
			recordDeath(actor.getName());
		}
		else if (actor instanceof NPC)
		{
			Segment segment = TobNpcs.bossSegment(((NPC) actor).getId());
			if (segment != null)
			{
				completeSegment(segment);
			}
		}
	}

	private void recordDeath(String name)
	{
		if (raid.recordDeath(Text.standardize(name)))
		{
			log.debug("{} died in room {}", name, raid.getCurrentRoom());
			invalidate();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		onNpc(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		onNpc(event.getNpc());
	}

	private void onNpc(NPC npc)
	{
		if (!isInRaid())
		{
			return;
		}

		int id = npc.getId();
		RaidMode mode = TobNpcs.modeOf(id);
		if (mode != null && !raid.isModeKnown())
		{
			raid.setMode(mode);
			raid.setModeKnown(true);
			invalidate();
		}

		Segment ended = TobNpcs.endedSegment(id);
		if (ended != null)
		{
			completeSegment(ended);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isInRaid() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		Segment segment = TobNpcs.bossSegment(((NPC) event.getActor()).getId());
		if (segment == null || raid.getCompleted().contains(segment))
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		boolean mine = hitsplat.isMine();
		if (mine || hitsplat.isOthers())
		{
			raid.recordHit(segment, hitsplat.getAmount(), mine);
			invalidate();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isInRaid() || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		Matcher m = WAVE_COMPLETE.matcher(Text.removeTags(event.getMessage()));
		if (!m.find())
		{
			return;
		}

		RaidMode mode = RaidMode.fromChatName(m.group(2));
		if (!raid.isModeKnown() || raid.getMode() != mode)
		{
			raid.setMode(mode);
			raid.setModeKnown(true);
			invalidate();
		}

		Segment segment = segmentForWave(m.group(1));
		if (segment != null)
		{
			completeSegment(segment);
		}
	}

	private static Segment segmentForWave(String wave)
	{
		String w = wave.toLowerCase(Locale.ROOT);
		if (w.contains("maiden"))
		{
			return Segment.MAIDEN;
		}
		if (w.contains("bloat"))
		{
			return Segment.BLOAT;
		}
		if (w.contains("nylocas"))
		{
			return Segment.NYLOCAS;
		}
		if (w.contains("sotetseg"))
		{
			return Segment.SOTETSEG;
		}
		if (w.contains("xarpus"))
		{
			return Segment.XARPUS;
		}
		if (w.contains("verzik") || w.contains("final"))
		{
			return Segment.VERZIK_P3;
		}
		return null;
	}

	private void completeSegment(Segment segment)
	{
		if (!raid.completeThrough(segment))
		{
			return;
		}

		log.debug("Completed {}", segment);
		invalidate();
		shareTicks = SHARE_DELAY_TICKS;
		if (raid.isComplete())
		{
			summaryTicks = SUMMARY_DELAY_TICKS;
		}
	}

	private void shareDamage()
	{
		if (!config.shareDamage() || !partyService.isInParty() || !raid.getTeam().containsKey(raid.getLocalName()))
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || local.getName() == null)
		{
			return;
		}

		partyService.send(new ToBDamageUpdate(local.getName(), raid.getLocalDamage().clone()));
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		// Teammates in the new party will resend their full damage after the next segment
		clientThread.invokeLater(() ->
		{
			if (raid != null)
			{
				raid.clearPartyDamage();
				invalidate();
			}
		});
	}

	@Subscribe
	public void onToBDamageUpdate(ToBDamageUpdate update)
	{
		// Party messages arrive on the websocket thread
		clientThread.invokeLater(() ->
		{
			if (raid == null || update.getName() == null)
			{
				return;
			}

			raid.setPartyDamage(Text.standardize(update.getName()), update.getDamage());
			invalidate();
		});
	}

	private void postSummary()
	{
		if (!config.chatSummary())
		{
			return;
		}

		PointCalculator.RaidScore s = getScore();
		ChatMessageBuilder message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("ToB points")
			.append(ChatColorType.NORMAL)
			.append(String.format(" (%s, %d-man): team %d/%d", s.getMode().getDisplayName(), s.getTeamSize(),
				s.getTeamPoints(), s.getMaxPoints()));

		for (PointCalculator.PlayerScore p : s.getPlayers())
		{
			if (p.isLocal())
			{
				message.append(String.format(", you %s%d/%d", p.isEstimated() ? "~" : "", p.getPoints(),
					Segment.MAX_PLAYER_POINTS));
				if (s.getMode().canRollUniques())
				{
					message.append(" (").append(formatPercent(p.getShare())).append(" of loot weight)");
				}
			}
		}

		if (s.getMode().canRollUniques())
		{
			message.append(". Purple chance: ")
				.append(ChatColorType.HIGHLIGHT)
				.append(formatPercent(s.getTeamUniqueChance()))
				.append(ChatColorType.NORMAL)
				.append(" team");
			for (PointCalculator.PlayerScore p : s.getPlayers())
			{
				if (p.isLocal())
				{
					message.append(", ").append(p.isEstimated() ? "~" : "").append(formatPercent(p.getUniqueChance()))
						.append(" you");
				}
			}
		}
		else
		{
			message.append(". Entry Mode can't roll purples");
		}

		if (s.getEstimatedMvpPoints() > 0)
		{
			message.append(String.format(" (%d MVP points estimated)", s.getEstimatedMvpPoints()));
		}

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message.append(".").build())
			.build());
	}

	static String formatPercent(double fraction)
	{
		return String.format("%.2f%%", fraction * 100);
	}

	private static String cleanName(String name)
	{
		if (name == null)
		{
			return "";
		}
		return Text.removeTags(name).replace(' ', ' ').trim();
	}
}
