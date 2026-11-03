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
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectVolume;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.LinkBrowser;

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
	/** Projectiles still unmatched this many ticks after launch are dropped. */
	private static final int MAX_PROJECTILE_TICKS = 12;
	/** A projectile counts as landed this tick if it landed up to this many ticks ago. */
	private static final int LANDED_WINDOW_TICKS = 2;
	private static final int PERCENT = 100;
	/** Decibels per tenfold change in amplitude. */
	private static final int DECIBELS_PER_DECADE = 20;
	/** The test messages' Scurrius chance: max hit 8 (Magic) for 0 at 8 HP. */
	private static final int TEST_MAX_HIT = 8;
	private static final int TEST_DAMAGE = 0;
	private static final int TEST_HP = 8;

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
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private MaxHitRepository maxHits;

	@Inject
	private AttackStyleTable attackStyles;

	@Inject
	private PvpEstimator pvpEstimator;

	@Inject
	private DiscordNotifier discord;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ScheduledExecutorService executor;

	/** An attack seen starting: an animation or a projectile. */
	static final class Pending
	{
		Actor actor;
		int startTick;
		/** Projectile end cycle, or 0 for animations. */
		int endCycle;
		boolean projectile;
		/** Style the animation or projectile name implies; null when unknown. */
		AttackStyle style;
		/** Protection prayers up when the attack started. */
		Set<AttackStyle> protectedAtStart = EnumSet.noneOf(AttackStyle.class);
		boolean consumed;
	}

	private final List<Pending> pending = new ArrayList<>();
	private final Map<Projectile, Pending> projectiles = new IdentityHashMap<>();
	private final List<int[]> tickHits = new ArrayList<>();

	@Override
	protected void startUp() throws Exception
	{
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
		if (!ChancedConfig.GROUP.equals(event.getGroup()) || !"true".equals(event.getNewValue()))
		{
			return;
		}
		if (ChancedConfig.TEST_CHAT.equals(event.getKey()))
		{
			configManager.setConfiguration(ChancedConfig.GROUP, ChancedConfig.TEST_CHAT, false);
			clientThread.invoke(() -> chat(testEvent().format(config.chatTemplate())));
		}
		else if (ChancedConfig.TEST_DISCORD.equals(event.getKey()))
		{
			configManager.setConfiguration(ChancedConfig.GROUP, ChancedConfig.TEST_DISCORD, false);
			clientThread.invoke(this::sendTestDiscord);
		}
		else if (ChancedConfig.TEST_SOUND.equals(event.getKey()))
		{
			configManager.setConfiguration(ChancedConfig.GROUP, ChancedConfig.TEST_SOUND, false);
			clientThread.invoke(this::playSound);
		}
		else if (ChancedConfig.OPEN_SOUND_LIST.equals(event.getKey()))
		{
			configManager.setConfiguration(ChancedConfig.GROUP, ChancedConfig.OPEN_SOUND_LIST, false);
			LinkBrowser.browse(ChancedConfig.SOUND_LIST_URL);
		}
		else if (ChancedConfig.OPEN_SOUND_FOLDER.equals(event.getKey()))
		{
			configManager.setConfiguration(ChancedConfig.GROUP, ChancedConfig.OPEN_SOUND_FOLDER, false);
			openSoundFolder();
		}
	}

	private void sendTestDiscord()
	{
		String webhooks = config.webhookUrl();
		if (!config.discordEnabled())
		{
			chat("Chanced: turn on Discord first.");
			return;
		}
		if (webhooks == null || webhooks.trim().isEmpty())
		{
			chat("Chanced: set a Discord webhook URL first.");
			return;
		}
		discord.send(testEvent(), config.discordTemplate(), webhooks, config.sendScreenshot(),
			DiscordNotifier.Delivery.from(config));
		chat("Chanced: sent a test message to Discord.");
	}

	/** A made-up Scurrius chance for the test buttons. */
	private ChancedEvent testEvent()
	{
		Player local = client.getLocalPlayer();
		String name = local != null && local.getName() != null ? local.getName() : "Player";
		return new ChancedEvent(name, "Scurrius", "Magic", prayerName(AttackStyle.MELEE), TEST_MAX_HIT, TEST_DAMAGE,
			TEST_HP, TEST_HP - TEST_DAMAGE,
			client.getWorld(), false);
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
		Pending p = new Pending();
		p.actor = actor;
		p.startTick = client.getTickCount();
		p.protectedAtStart = protectedStyles();
		if (actor instanceof NPC)
		{
			AttackHint hint = attackStyles.animation(actor.getAnimation());
			if (hint == null)
			{
				return;
			}
			p.style = hint.toStyle();
		}
		else if (!(actor instanceof Player && actor.getInteracting() == local && pvpActive()))
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
		if (local == null || projectile.getTargetActor() != local || projectiles.containsKey(projectile))
		{
			return;
		}
		Pending p = new Pending();
		p.actor = projectile.getSourceActor();
		p.startTick = client.getTickCount();
		p.endCycle = projectile.getEndCycle();
		p.projectile = true;
		p.style = attackStyles.projectile(projectile.getId());
		p.protectedAtStart = protectedStyles();
		projectiles.put(projectile, p);
		pending.add(p);
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
		pending.removeIf(p -> p.consumed || tick - p.startTick > MAX_PROJECTILE_TICKS
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
		Iterator<Pending> next = candidates(tick, local).iterator();

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

		Verdict verdict = ChanceCalculator.evaluate(hits, hpAfter);
		if (verdict != null)
		{
			notify(ChancedEvent.of(verdict, local.getName() != null ? local.getName() : "Player", prayerName(),
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
			if (p.consumed || p.actor == null)
			{
				continue;
			}
			if (p.projectile)
			{
				if (p.endCycle <= cycle + CYCLES_PER_TICK && p.endCycle >= cycle - LANDED_WINDOW_TICKS * CYCLES_PER_TICK)
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
		for (Pending p : pending)
		{
			if (!p.consumed && p.actor == used.actor && (!p.projectile || used.projectile)
				&& p.startTick >= tick - PLAYER_ANIMATION_WINDOW && p.startTick <= used.startTick + 1)
			{
				p.consumed = true;
			}
		}
	}

	/** No seen attack explains the hit: blame the nearest NPC attacking us. */
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
		if (nearest == null)
		{
			return null;
		}
		Pending p = new Pending();
		p.actor = nearest;
		p.protectedAtStart = protectedStyles();
		return toAttack(p, true);
	}

	private Attack toAttack(Pending p, boolean unexplained)
	{
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
		Set<AttackStyle> prayers = protectedStyles();
		AttackStyle style = chooseStyle(info, p, unexplained, prayers);
		if (style == null)
		{
			return null;
		}
		boolean prayed = style.isNormal() && (p.protectedAtStart.contains(style) || prayers.contains(style));
		String name = npc.getName() != null ? npc.getName() : info.getName();
		if (HunllefRules.isHunllef(npc.getId()))
		{
			// Unexplained hits in the Hunllef fight are its tornadoes and floor tiles.
			return HunllefRules.attack(npc.getId(), name, unexplained ? AttackStyle.SPECIAL : style, prayed,
				HunllefRules.armourPoints(client.getLocalPlayer()));
		}
		int specialMax = info.max(AttackStyle.SPECIAL);
		if (specialMax > 0 && DragonfireRules.applies(info.getSpecialName()))
		{
			specialMax = DragonfireRules.max(specialMax, "King Black Dragon".equals(info.getName()),
				dragonfireProtection(prayers));
		}
		return Attack.builder()
			.source(name)
			.style(style)
			.max(style == AttackStyle.SPECIAL ? specialMax : info.max(style))
			.prayed(prayed)
			.specialMax(specialMax)
			.specialName(info.getSpecialName())
			.build();
	}

	private DragonfireRules.Protection dragonfireProtection(Set<AttackStyle> prayers)
	{
		DragonfireRules.Protection p = new DragonfireRules.Protection();
		Player local = client.getLocalPlayer();
		PlayerComposition comp = local != null ? local.getPlayerComposition() : null;
		p.shield = comp != null && DragonfireRules.SHIELDS.contains(comp.getEquipmentId(KitType.SHIELD));
		p.prayer = prayers.contains(AttackStyle.MAGIC);
		p.antifire = client.getVarbitValue(VarbitID.ANTIFIRE_POTION) > 0;
		p.superAntifire = client.getVarbitValue(VarbitID.SUPER_ANTIFIRE_POTION) > 0;
		return p;
	}

	/**
	 * The style an NPC attack most likely used. Specials only explain a hit no normal style
	 * does; when the style is unclear, take the worst case for the player.
	 */
	static AttackStyle chooseStyle(MonsterInfo info, Pending p, boolean unexplained, Set<AttackStyle> prayers)
	{
		Set<AttackStyle> options = EnumSet.noneOf(AttackStyle.class);
		if (unexplained || p.style == AttackStyle.SPECIAL)
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
		return Attack.builder()
			.source(player.getName() != null ? player.getName() : "A player")
			.style(style)
			.max(PvpEstimator.maxHit(gear, style))
			.prayed(p.protectedAtStart.contains(style) || protectedStyles().contains(style))
			.player(true)
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
		if (!config.leaguesEnabled() && client.getWorldType().contains(WorldType.SEASONAL))
		{
			return;
		}
		if (config.chatMessage())
		{
			chat(event.format(config.chatTemplate()));
		}
		if (config.soundEnabled())
		{
			playSound();
		}
		String webhooks = config.webhookUrl();
		if (config.discordEnabled() && webhooks != null && !webhooks.trim().isEmpty())
		{
			discord.send(event, config.discordTemplate(), webhooks, config.sendScreenshot(),
				DiscordNotifier.Delivery.from(config));
		}
	}

	private void playSound()
	{
		SoundType type = config.soundType();
		log.debug("Playing the {} sound at {}%", type, config.soundVolume());
		if (type == SoundType.GAME_SOUND)
		{
			playGameSound();
			return;
		}
		float gain = soundGainDb(config.soundVolume());
		executor.execute(() ->
		{
			try
			{
				if (type == SoundType.CUSTOM_FILE)
				{
					audioPlayer.play(getPluginDirectory().joinSegment(ChancedConfig.CUSTOM_SOUND_FILE), gain);
				}
				else
				{
					audioPlayer.play(ChancedPlugin.class, ChancedConfig.DEFAULT_SOUND_RESOURCE, gain);
				}
				log.debug("Started the {} sound at {} dB", type, gain);
			}
			catch (IOException | UnsupportedAudioFileException | LineUnavailableException | RuntimeException e)
			{
				log.debug("Could not play the {} sound", type, e);
				clientThread.invoke(() -> soundFailed(type, gain));
			}
		});
	}

	/** A custom file that cannot play falls back to the Chanced sound; that one falls back to the game sound. */
	private void soundFailed(SoundType type, float gain)
	{
		if (type == SoundType.CUSTOM_FILE)
		{
			chat("Chanced: could not play " + ChancedConfig.CUSTOM_SOUND_FILE
				+ " from the sound folder; playing the Chanced sound instead.");
			executor.execute(() ->
			{
				try
				{
					audioPlayer.play(ChancedPlugin.class, ChancedConfig.DEFAULT_SOUND_RESOURCE, gain);
				}
				catch (IOException | UnsupportedAudioFileException | LineUnavailableException | RuntimeException e)
				{
					log.debug("Could not play the Chanced sound", e);
					clientThread.invoke(this::playGameSound);
				}
			});
			return;
		}
		playGameSound();
	}

	private void playGameSound()
	{
		log.debug("Playing game sound {} at volume {}", config.soundId(), soundVolume(config.soundVolume()));
		client.playSoundEffect(config.soundId(), soundVolume(config.soundVolume()));
	}

	/** Config percent (1-100) to decibels of gain for a sound file: 100% plays it as recorded. */
	static float soundGainDb(int percent)
	{
		int p = Math.max(1, Math.min(PERCENT, percent));
		return (float) (DECIBELS_PER_DECADE * Math.log10((double) p / PERCENT));
	}

	private void openSoundFolder()
	{
		try
		{
			Filepath dir = getPluginDirectory();
			dir.createDirectories();
			LinkBrowser.open(dir.toString());
		}
		catch (IOException e)
		{
			log.debug("Could not open the sound folder", e);
		}
	}

	/** Config percent (1-100) to the client's sound effect volume scale (0-127). */
	static int soundVolume(int percent)
	{
		return Math.max(0, Math.min(PERCENT, percent)) * SoundEffectVolume.HIGH / PERCENT;
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
