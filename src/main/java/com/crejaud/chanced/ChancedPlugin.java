package com.crejaud.chanced;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Chanced",
	internalName = "chanced",
	description = "Alerts when a hit could have killed you and didn't",
	tags = {"combat", "max hit", "discord", "alert", "pvp", "luck"}
)
public class ChancedPlugin extends Plugin
{
	static final String REFRESH_COMMAND = "chancedrefresh";

	/** Ticks an attack animation stays a candidate for a hit (NPC melee lands within a tick). */
	private static final int NPC_ANIMATION_WINDOW = 2;
	/** Players' spells without projectiles (barrage) land a few ticks after the cast. */
	private static final int PLAYER_ANIMATION_WINDOW = 4;
	private static final int CYCLES_PER_TICK = 30;
	private static final int MAX_DISTANCE = 16;

	private static final Set<Integer> OVER_TIME = ImmutableSet.of(
		HitsplatID.POISON, HitsplatID.VENOM, HitsplatID.DISEASE, HitsplatID.BURN, HitsplatID.BLEED,
		HitsplatID.DOOM, HitsplatID.CORRUPTION);
	private static final Set<Integer> DAMAGE = ImmutableSet.of(
		HitsplatID.BLOCK_ME, HitsplatID.BLOCK_OTHER, HitsplatID.DAMAGE_ME, HitsplatID.DAMAGE_OTHER,
		HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_ME_ORANGE,
		HitsplatID.DAMAGE_OTHER_ORANGE, HitsplatID.DAMAGE_ME_YELLOW, HitsplatID.DAMAGE_OTHER_YELLOW,
		HitsplatID.DAMAGE_ME_WHITE, HitsplatID.DAMAGE_OTHER_WHITE, HitsplatID.DAMAGE_MAX_ME,
		HitsplatID.DAMAGE_MAX_ME_CYAN, HitsplatID.DAMAGE_MAX_ME_ORANGE, HitsplatID.DAMAGE_MAX_ME_YELLOW,
		HitsplatID.DAMAGE_MAX_ME_WHITE, HitsplatID.DAMAGE_ME_POISE, HitsplatID.DAMAGE_OTHER_POISE,
		HitsplatID.DAMAGE_MAX_ME_POISE);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChancedConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private MaxHitRepository maxHits;

	@Inject
	private PvpEstimator pvpEstimator;

	@Inject
	private DiscordNotifier discord;

	private Overrides overrides = Overrides.parse("", "", "");

	/** An attack seen starting: an animation, a projectile or a hazard. */
	static final class Pending
	{
		Actor actor;
		int startTick;
		/** Projectile end cycle, or 0 for animations and hazards. */
		int endCycle;
		boolean projectile;
		/** Style the animation or projectile name implies; null when unknown. */
		AttackStyle style;
		/** Animation with no style in its name. */
		boolean unknownStyle;
		/** Max hit of a configured hazard, or -1. */
		int hazardMax = -1;
		/** Protection prayers up when the attack started. */
		Set<AttackStyle> protectedAtStart;
		boolean consumed;
	}

	private final List<Pending> pending = new ArrayList<>();
	private final Map<Projectile, Pending> projectiles = new IdentityHashMap<>();
	private final List<int[]> tickHits = new ArrayList<>();

	@Override
	protected void startUp() throws Exception
	{
		reloadOverrides();
		loadMaxHits(false);
	}

	@Override
	protected void shutDown() throws Exception
	{
		pending.clear();
		projectiles.clear();
		tickHits.clear();
		maxHits.clear();
	}

	@Provides
	ChancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChancedConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ChancedConfig.GROUP.equals(event.getGroup()))
		{
			reloadOverrides();
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (REFRESH_COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			chat("Refreshing max hits from the OSRS Wiki...");
			loadMaxHits(true);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (actor == null || local == null || actor == local || actor.getAnimation() == -1 || !near(actor, local))
		{
			return;
		}
		int animation = actor.getAnimation();
		Pending p = new Pending();
		p.actor = actor;
		p.startTick = client.getTickCount();
		p.protectedAtStart = protectedStyles();
		if (actor instanceof NPC)
		{
			AttackStyle override = overrides.style(((NPC) actor).getId(), animation);
			AttackHint hint = AttackStyleTable.animation(animation);
			if (override == null && hint == null)
			{
				return;
			}
			p.style = override != null ? override : hint.toStyle();
			p.unknownStyle = p.style == null;
		}
		else if (actor instanceof Player && actor.getInteracting() == local && pvpActive())
		{
			p.unknownStyle = true;
		}
		else
		{
			return;
		}
		pending.add(p);
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		Player local = client.getLocalPlayer();
		if (local == null || projectiles.containsKey(projectile))
		{
			return;
		}
		Integer hazardMax = overrides.getProjectileHazards().get(projectile.getId());
		boolean atMe = projectile.getTargetActor() == local;
		if (!atMe && hazardMax != null)
		{
			WorldPoint target = projectile.getTargetPoint();
			atMe = target != null && target.distanceTo(local.getWorldLocation()) <= 1;
		}
		if (!atMe)
		{
			return;
		}
		Pending p = new Pending();
		p.actor = projectile.getSourceActor();
		p.startTick = client.getTickCount();
		p.endCycle = projectile.getEndCycle();
		p.projectile = true;
		p.style = AttackStyleTable.projectile(projectile.getId());
		p.hazardMax = hazardMax != null ? hazardMax : -1;
		p.protectedAtStart = protectedStyles();
		projectiles.put(projectile, p);
		pending.add(p);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Player local = client.getLocalPlayer();
		if (event.getActor() != local || local == null || overrides.getGraphicHazards().isEmpty())
		{
			return;
		}
		for (ActorSpotAnim spotAnim : local.getSpotAnims())
		{
			Integer max = overrides.getGraphicHazards().get(spotAnim.getId());
			if (max != null)
			{
				Pending p = new Pending();
				p.startTick = client.getTickCount();
				p.hazardMax = max;
				p.protectedAtStart = protectedStyles();
				pending.add(p);
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		int type = event.getHitsplat().getHitsplatType();
		boolean overTime = OVER_TIME.contains(type);
		if (overTime || DAMAGE.contains(type))
		{
			tickHits.add(new int[]{event.getHitsplat().getAmount(), overTime ? 1 : 0});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		if (!tickHits.isEmpty())
		{
			evaluateTick(tick);
			tickHits.clear();
		}
		pending.removeIf(p -> p.consumed || tick - p.startTick > 12
			|| (!p.projectile && tick - p.startTick > PLAYER_ANIMATION_WINDOW));
		projectiles.values().removeIf(p -> !pending.contains(p));
	}

	private void evaluateTick(int tick)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		int hpAfter = client.getBoostedSkillLevel(Skill.HITPOINTS);
		List<Pending> candidates = candidates(tick, local);
		Iterator<Pending> next = candidates.iterator();

		List<Hit> hits = new ArrayList<>();
		Attack last = null;
		for (int[] raw : tickHits)
		{
			int damage = raw[0];
			if (raw[1] == 1)
			{
				hits.add(new Hit(damage, true, null));
				continue;
			}
			Attack attack = null;
			while (attack == null && next.hasNext())
			{
				Pending p = next.next();
				if (p.consumed)
				{
					continue;
				}
				consume(p, tick);
				attack = toAttack(p, false);
			}
			if (attack == null)
			{
				// Out of seen attacks: a second hitsplat of the same attack, or an unseen one.
				attack = last != null ? last : fallback(local);
			}
			if (attack != null)
			{
				last = attack;
			}
			hits.add(new Hit(damage, false, attack));
		}

		Verdict verdict = ChanceCalculator.evaluate(hits, hpAfter, config.unknownHpThreshold());
		if (verdict != null)
		{
			notify(ChancedEvent.of(verdict, local.getName() != null ? local.getName() : "You", prayerName(),
				client.getWorld()));
		}
	}

	/** Attacks that could have landed this tick, most certain first. */
	private List<Pending> candidates(int tick, Player local)
	{
		int cycle = client.getGameCycle();
		List<Pending> landed = new ArrayList<>();
		List<Pending> atMe = new ArrayList<>();
		List<Pending> others = new ArrayList<>();
		for (Pending p : pending)
		{
			if (p.consumed)
			{
				continue;
			}
			if (p.projectile)
			{
				if (p.endCycle <= cycle + CYCLES_PER_TICK && p.endCycle >= cycle - 2 * CYCLES_PER_TICK)
				{
					landed.add(p);
				}
				continue;
			}
			if (p.actor == null)
			{
				if (p.startTick >= tick - 1)
				{
					landed.add(p);
				}
				continue;
			}
			int window = p.actor instanceof Player ? PLAYER_ANIMATION_WINDOW : NPC_ANIMATION_WINDOW;
			if (p.startTick < tick - window || inFlightFrom(p.actor))
			{
				continue;
			}
			(p.actor.getInteracting() == local ? atMe : others).add(p);
		}
		landed.addAll(atMe);
		landed.addAll(others);
		return landed;
	}

	/** The actor has a projectile on its way to us, so its animation's hit has not landed yet. */
	private boolean inFlightFrom(Actor actor)
	{
		int cycle = client.getGameCycle();
		for (Pending p : pending)
		{
			if (p.projectile && !p.consumed && p.actor == actor && p.endCycle > cycle + CYCLES_PER_TICK)
			{
				return true;
			}
		}
		return false;
	}

	/** Marks an attack used, along with the same actor's other recent pieces of it. */
	private void consume(Pending used, int tick)
	{
		used.consumed = true;
		if (used.actor == null)
		{
			return;
		}
		for (Pending p : pending)
		{
			if (!p.consumed && p.actor == used.actor && (!p.projectile || used.projectile)
				&& p.startTick >= tick - PLAYER_ANIMATION_WINDOW && p.startTick <= used.startTick + 1)
			{
				p.consumed = true;
			}
		}
	}

	/** No seen attack explains the hit: blame the nearest NPC attacking us, or a region hazard. */
	private Attack fallback(Player local)
	{
		NPC nearest = null;
		int best = Integer.MAX_VALUE;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getInteracting() != local || npc.isDead())
			{
				continue;
			}
			int d = npc.getWorldLocation().distanceTo(local.getWorldLocation());
			if (d < best)
			{
				best = d;
				nearest = npc;
			}
		}
		if (nearest != null)
		{
			Pending p = new Pending();
			p.actor = nearest;
			p.protectedAtStart = protectedStyles();
			Attack attack = toAttack(p, true);
			if (attack != null)
			{
				return attack;
			}
		}
		Integer regionMax = overrides.getRegionHazards().get(local.getWorldLocation().getRegionID());
		if (regionMax != null)
		{
			return hazard(regionMax);
		}
		return null;
	}

	private Attack toAttack(Pending p, boolean unexplained)
	{
		if (p.hazardMax >= 0)
		{
			return hazard(p.hazardMax);
		}
		if (p.actor instanceof NPC)
		{
			return npcAttack((NPC) p.actor, p, unexplained);
		}
		if (p.actor instanceof Player && pvpActive())
		{
			return playerAttack((Player) p.actor, p);
		}
		return null;
	}

	private Attack npcAttack(NPC npc, Pending p, boolean unexplained)
	{
		MonsterInfo info = maxHits.get(npc.getId());
		if (info == null)
		{
			return null;
		}
		AttackStyle style = chooseStyle(info, p, unexplained, protectedStyles());
		if (style == null)
		{
			return null;
		}
		boolean prayed = style.isNormal()
			&& (p.protectedAtStart.contains(style) || protectedStyles().contains(style));
		return Attack.builder()
			.source(npc.getName() != null ? npc.getName() : info.getName())
			.style(style)
			.max(info.max(style))
			.prayed(prayed)
			.passThroughPercent(overrides.passThrough(npc.getId(), style))
			.specialMax(info.max(AttackStyle.SPECIAL))
			.specialName(info.getSpecialName())
			.build();
	}

	/**
	 * The style an NPC attack most likely used. Specials only explain a hit no normal style
	 * does; when the style is unclear, take the worst case for the player.
	 */
	static AttackStyle chooseStyle(MonsterInfo info, Pending p, boolean unexplained, Set<AttackStyle> prayers)
	{
		Set<AttackStyle> options = EnumSet.noneOf(AttackStyle.class);
		if (unexplained)
		{
			if (info.has(AttackStyle.SPECIAL))
			{
				return AttackStyle.SPECIAL;
			}
		}
		else if (p.style == AttackStyle.SPECIAL)
		{
			if (info.has(AttackStyle.SPECIAL))
			{
				return AttackStyle.SPECIAL;
			}
		}
		else if (p.style != null)
		{
			if (info.has(p.style))
			{
				return p.style;
			}
		}
		else if (p.projectile)
		{
			if (info.has(AttackStyle.RANGED))
			{
				options.add(AttackStyle.RANGED);
			}
			if (info.has(AttackStyle.MAGIC))
			{
				options.add(AttackStyle.MAGIC);
			}
		}
		if (options.isEmpty())
		{
			for (AttackStyle style : info.getMaxHits().keySet())
			{
				if (style.isNormal())
				{
					options.add(style);
				}
			}
		}
		if (options.isEmpty())
		{
			return info.has(AttackStyle.SPECIAL) ? AttackStyle.SPECIAL : null;
		}
		AttackStyle worst = null;
		int worstMax = -1;
		for (AttackStyle style : options)
		{
			int max = prayers.contains(style) ? 0 : info.max(style);
			if (max > worstMax)
			{
				worst = style;
				worstMax = max;
			}
		}
		return worst;
	}

	private Attack playerAttack(Player player, Pending p)
	{
		PvpEstimator.Gear gear = pvpEstimator.gear(player);
		AttackStyle style = p.style != null && p.style.isNormal() ? p.style : PvpEstimator.weaponStyle(gear);
		int max = PvpEstimator.maxHit(gear, style, config.pvpAssumedRing(), config.pvpAssumedAmmo(),
			config.pvpSpecMargin());
		return Attack.builder()
			.source(player.getName() != null ? player.getName() : "A player")
			.style(style)
			.max(max)
			.prayed(p.protectedAtStart.contains(style) || protectedStyles().contains(style))
			.player(true)
			.build();
	}

	private static Attack hazard(int max)
	{
		return Attack.builder()
			.source("Hazard")
			.style(AttackStyle.SPECIAL)
			.max(max)
			.specialName("Hazard")
			.build();
	}

	private boolean near(Actor actor, Player local)
	{
		WorldPoint a = actor.getWorldLocation();
		return a != null && a.distanceTo(local.getWorldLocation()) <= MAX_DISTANCE;
	}

	private boolean pvpActive()
	{
		if (!config.pvpEnabled())
		{
			return false;
		}
		if (config.pvpAnywhere())
		{
			return true;
		}
		EnumSet<WorldType> types = client.getWorldType();
		return client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1
			|| client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1
			|| types.contains(WorldType.PVP) || types.contains(WorldType.DEADMAN)
			|| types.contains(WorldType.HIGH_RISK);
	}

	private Set<AttackStyle> protectedStyles()
	{
		Set<AttackStyle> out = EnumSet.noneOf(AttackStyle.class);
		for (AttackStyle style : AttackStyle.values())
		{
			Prayer prayer = style.getProtection();
			if (prayer != null && client.isPrayerActive(prayer))
			{
				out.add(style);
			}
		}
		return out;
	}

	private String prayerName()
	{
		Set<AttackStyle> styles = protectedStyles();
		return styles.isEmpty() ? "None" : prayerName(styles.iterator().next());
	}

	static String prayerName(AttackStyle style)
	{
		return style == AttackStyle.RANGED ? "Protect from Missiles" : "Protect from " + style.getDisplayName();
	}

	private void notify(ChancedEvent event)
	{
		log.debug("Chanced: {}", event);
		if (config.chatMessage())
		{
			chat(event.format(config.chatTemplate()));
		}
		String webhooks = config.webhookUrl();
		if (webhooks != null && !webhooks.trim().isEmpty())
		{
			discord.send(event, config.discordTemplate(), webhooks, config.sendScreenshot());
		}
	}

	private void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(config.chatColor(), message)
				.build())
			.build());
	}

	private void reloadOverrides()
	{
		overrides = Overrides.parse(config.styleOverrides(), config.prayerPassThrough(), config.hazards());
	}

	private void loadMaxHits(boolean force)
	{
		try
		{
			maxHits.load(getPluginDirectory(), force, status ->
			{
				log.info("Chanced: {}", status);
				if (force)
				{
					clientThread.invoke(() -> chat(status));
				}
			});
		}
		catch (IOException e)
		{
			log.warn("Chanced: no plugin data directory", e);
		}
	}
}
