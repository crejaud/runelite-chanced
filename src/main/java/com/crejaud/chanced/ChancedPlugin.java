package com.crejaud.chanced;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectVolume;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpotanimID;
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
import net.runelite.client.util.Text;
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

	private static final int MAX_DISTANCE = 16;
	private static final int PERCENT = 100;
	/** Decibels per tenfold change in amplitude. */
	private static final int DECIBELS_PER_DECADE = 20;
	/** The test messages' Scurrius chance: max hit 8 (Magic) for 0 at 8 HP. */
	private static final int TEST_MAX_HIT = 8;
	private static final int TEST_DAMAGE = 0;
	private static final int TEST_HP = 8;
	/** Ruby bolts (e)'s Blood Forfeit, which also costs the player 10% of their hitpoints. */
	private static final int RUBY_BOLT_PROC = SpotanimID.XBOWS_BLOOD_SACRIFICE_SPOT_ANIM;
	/** Hazards spawning this close to the player go in the debug report. */
	private static final int HAZARD_DISTANCE = 1;

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

	@Inject
	private Gson gson;

	private final HitMatcher matcher = new HitMatcher(id -> maxHits.get(id));
	private final DebugRecorder recorder = new DebugRecorder();
	/** Projectiles in flight already read, so each is read once. */
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new IdentityHashMap<>());
	private final List<TickState.RawHit> tickHits = new ArrayList<>();
	/** Inventory item counts, to see what the player drinks and eats. */
	private final Map<Integer, Integer> inventory = new HashMap<>();
	/** The debug report is on: Discord and its attachment are both enabled. */
	private boolean recording;

	@Override
	protected void startUp() throws Exception
	{
		updateRecording();
		loadMaxHits(false);
	}

	@Override
	protected void shutDown() throws Exception
	{
		discord.shutDown();
		matcher.clear();
		recorder.clear();
		seenProjectiles.clear();
		tickHits.clear();
		inventory.clear();
		maxHits.clear();
	}

	@Provides
	ChancedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChancedConfig.class);
	}

	private void updateRecording()
	{
		boolean on = config.discordEnabled() && config.attachDebugReport();
		if (!on)
		{
			recorder.clear();
		}
		recording = on;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ChancedConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		updateRecording();
		if (!"true".equals(event.getNewValue()))
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
			showSoundFolder();
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
		ChancedEvent event = testEvent();
		discord.send(event, config.discordTemplate(), webhooks, config.sendScreenshot(), debugReport(event),
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
		int tick = client.getTickCount();
		Set<AttackStyle> prayers = protectedStyles();
		if (actor instanceof NPC)
		{
			ActorRef ref = ref(actor);
			AttackHint hint = attackStyles.animation(actor.getAnimation());
			if (recording)
			{
				recorder.animation(tick, ref, actor.getAnimation(), hint, hint != null, prayers);
			}
			if (hint != null)
			{
				matcher.animation(ref, tick, hint.toStyle(), prayers);
			}
		}
		else if (actor instanceof Player && actor.getInteracting() == local && pvpActive())
		{
			ActorRef ref = ref(actor);
			if (recording)
			{
				recorder.animation(tick, ref, actor.getAnimation(), null, true, prayers);
			}
			matcher.animation(ref, tick, null, prayers);
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		Player local = client.getLocalPlayer();
		// Moved fires every frame of a projectile's flight: read each one once, at its launch.
		if (local == null || !seenProjectiles.add(projectile))
		{
			return;
		}
		int tick = client.getTickCount();
		AttackStyle style = attackStyles.projectile(projectile.getId());
		ActorRef source = projectileSource(projectile, tick);
		boolean atMe = projectile.getTargetActor() == local;
		WorldPoint target = projectile.getTargetPoint();
		WorldPoint at = local.getWorldLocation();
		Set<AttackStyle> prayers = protectedStyles();
		if (atMe)
		{
			log.debug("Chanced: tick {} projectile {} style {} from {}", tick, projectile.getId(), style, source);
		}
		if (recording)
		{
			recorder.projectile(tick, projectile.getId(), source, atMe, target, at, projectile.getEndCycle(), style,
				prayers);
		}
		matcher.projectile(projectile.getId(), source, atMe, target, at, tick, projectile.getEndCycle(), style,
			prayers);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Player local = client.getLocalPlayer();
		Actor actor = event.getActor();
		if (local == null || actor == null)
		{
			return;
		}
		boolean target = actor == local.getInteracting();
		if (!target && actor != local)
		{
			return;
		}
		int tick = client.getTickCount();
		if (recording)
		{
			JsonArray ids = new JsonArray();
			for (ActorSpotAnim spot : actor.getSpotAnims())
			{
				ids.add(spot.getId());
			}
			recorder.spotAnims(tick, target ? "target" : "me", ids);
		}
		if (target && actor.hasSpotAnim(RUBY_BOLT_PROC))
		{
			selfDamage(SelfDamage.RUBY_BOLT, tick);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		int type = event.getHitsplat().getHitsplatType();
		int amount = event.getHitsplat().getAmount();
		if (event.getActor() == local)
		{
			if (HitMatcher.counts(type))
			{
				tickHits.add(new TickState.RawHit(amount, type));
			}
		}
		else if (recording && event.getActor() == local.getInteracting())
		{
			recorder.targetHit(client.getTickCount(), amount, type);
		}
	}

	/** Self-damage the player just triggered, for the matcher and the report. */
	private void selfDamage(SelfDamage source, int tick)
	{
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		matcher.selfDamage(source, tick, hp);
		if (recording)
		{
			recorder.selfDamage(tick, source, hp);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Game messages are about the player; other players' chat is never read.
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		int tick = client.getTickCount();
		if (recording)
		{
			recorder.chat(tick, event.getType().name(), event.getMessage());
		}
		SelfDamage source = SelfDamage.fromMessage(Text.removeTags(event.getMessage()));
		if (source != null)
		{
			selfDamage(source, tick);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		Map<Integer, Integer> now = counts(event.getItemContainer());
		if (!inventory.isEmpty())
		{
			int tick = client.getTickCount();
			for (Map.Entry<Integer, Integer> e : now.entrySet())
			{
				int delta = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
				if (delta != 0)
				{
					inventoryChanged(tick, e.getKey(), delta);
				}
			}
			for (Map.Entry<Integer, Integer> e : inventory.entrySet())
			{
				if (!now.containsKey(e.getKey()))
				{
					inventoryChanged(tick, e.getKey(), -e.getValue());
				}
			}
		}
		inventory.clear();
		inventory.putAll(now);
	}

	/** A dose drunk shows as one fewer of the item: some potions cost the player hitpoints. */
	private void inventoryChanged(int tick, int itemId, int delta)
	{
		if (recording)
		{
			recorder.inventory(tick, itemId, delta);
		}
		if (delta < 0)
		{
			SelfDamage source = SelfDamage.fromItem(client.getItemDefinition(itemId).getName());
			if (source != null)
			{
				selfDamage(source, tick);
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		Player local = client.getLocalPlayer();
		GameObject object = event.getGameObject();
		if (!recording || local == null || object == null)
		{
			return;
		}
		WorldPoint at = object.getWorldLocation();
		if (closeTo(at, local))
		{
			recorder.object(client.getTickCount(), object.getId(), at);
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		Player local = client.getLocalPlayer();
		GraphicsObject graphic = event.getGraphicsObject();
		if (!recording || local == null || graphic == null || graphic.getLocation() == null)
		{
			return;
		}
		WorldPoint at = WorldPoint.fromLocal(client, graphic.getLocation());
		if (closeTo(at, local))
		{
			recorder.graphic(client.getTickCount(), graphic.getId(), at);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player local = client.getLocalPlayer();
		TickState state = new TickState();
		state.tick = client.getTickCount();
		state.cycle = client.getGameCycle();
		if (local != null && !tickHits.isEmpty())
		{
			fill(state, local);
		}
		tickHits.clear();
		HitMatcher.Result result = matcher.endTick(state);
		if (recording)
		{
			recorder.tick(state, result);
			recorder.prune(state.tick);
		}
		if (result.verdict != null && local != null)
		{
			notify(ChancedEvent.of(result.verdict, local.getName() != null ? local.getName() : "Player", prayerName(),
				client.getWorld()));
		}
		int cycle = client.getGameCycle();
		seenProjectiles.removeIf(p -> p.getEndCycle() < cycle);
	}

	/** Reads everything the matcher needs to match this tick's hits. */
	private void fill(TickState s, Player local)
	{
		s.hits.addAll(tickHits);
		s.hpAfter = client.getBoostedSkillLevel(Skill.HITPOINTS);
		s.local = local.getWorldLocation();
		s.prayers = protectedStyles();
		s.pvpActive = pvpActive();
		PlayerComposition comp = local.getPlayerComposition();
		s.antiDragonShield = comp != null && DragonfireRules.SHIELDS.contains(comp.getEquipmentId(KitType.SHIELD));
		s.antifire = client.getVarbitValue(VarbitID.ANTIFIRE_POTION) > 0;
		s.superAntifire = client.getVarbitValue(VarbitID.SUPER_ANTIFIRE_POTION) > 0;
		s.hunllefArmour = HunllefRules.armourPoints(local);
		s.absorption = client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);
		WorldArea localArea = local.getWorldArea();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (!near(npc, local))
			{
				continue;
			}
			TickState.ActorState a = actorState(npc, local, localArea);
			a.npcId = npc.getId();
			a.name = npc.getName();
			a.dead = npc.isDead();
			s.actors.add(a);
		}
		if (!s.pvpActive)
		{
			return;
		}
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player == local || !near(player, local))
			{
				continue;
			}
			TickState.ActorState a = actorState(player, local, localArea);
			a.npcId = -1;
			s.actors.add(a);
			PvpEstimator.Gear gear = pvpEstimator.gear(player);
			TickState.PlayerEstimate estimate = new TickState.PlayerEstimate();
			estimate.name = player.getName();
			estimate.weaponStyle = PvpEstimator.weaponStyle(gear);
			for (AttackStyle style : AttackStyle.values())
			{
				if (style.isNormal())
				{
					estimate.max.put(style, PvpEstimator.maxHit(gear, style));
				}
			}
			s.players.put(a.ref, estimate);
		}
	}

	private TickState.ActorState actorState(Actor actor, Player local, WorldArea localArea)
	{
		TickState.ActorState a = new TickState.ActorState();
		a.ref = ref(actor);
		a.interactingMe = actor.getInteracting() == local;
		a.distance = actor.getWorldLocation().distanceTo(local.getWorldLocation());
		WorldArea area = actor.getWorldArea();
		a.meleeRange = area != null && localArea != null && area.isInMeleeDistance(localArea);
		return a;
	}

	private static ActorRef ref(Actor actor)
	{
		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			return ActorRef.npc(npc.getIndex(), npc.getId(), npc.getName());
		}
		if (actor instanceof Player)
		{
			return ActorRef.player(((Player) actor).getId());
		}
		return null;
	}

	/**
	 * The actor a projectile came from. Some NPCs' projectiles (the Hunllef's) carry no source
	 * actor: take the NPC standing on the projectile's source tile, else the only NPC that just
	 * started an attack animation.
	 */
	private ActorRef projectileSource(Projectile projectile, int tick)
	{
		Actor source = projectile.getSourceActor();
		if (source != null)
		{
			return ref(source);
		}
		WorldPoint from = projectile.getSourcePoint();
		if (from != null)
		{
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				WorldArea area = npc.getWorldArea();
				if (area != null && area.distanceTo(from) <= 1)
				{
					return ref(npc);
				}
			}
		}
		return matcher.onlyAnimatingNpc(tick);
	}

	private static Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() >= 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	private static boolean closeTo(WorldPoint at, Player local)
	{
		WorldPoint me = local.getWorldLocation();
		return at != null && me != null && at.getPlane() == me.getPlane() && at.distanceTo2D(me) <= HAZARD_DISTANCE;
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

	/** The debug report as JSON, or null when the setting is off. */
	private String debugReport(ChancedEvent event)
	{
		if (!recording)
		{
			return null;
		}
		JsonObject header = new JsonObject();
		header.addProperty("plugin", "chanced");
		header.addProperty("tick", client.getTickCount());
		header.addProperty("world", client.getWorld());
		JsonArray types = new JsonArray();
		client.getWorldType().forEach(t -> types.add(t.name()));
		header.add("worldTypes", types);
		Player local = client.getLocalPlayer();
		if (local != null && local.getWorldLocation() != null)
		{
			header.addProperty("region", local.getWorldLocation().getRegionID());
		}
		header.addProperty("hpLevel", client.getRealSkillLevel(Skill.HITPOINTS));
		header.addProperty("absorption", client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS));
		JsonArray worn = new JsonArray();
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				worn.add(item.getId());
			}
		}
		header.add("equipment", worn);
		JsonObject chance = new JsonObject();
		chance.addProperty("source", event.getSource());
		chance.addProperty("style", event.getStyle());
		chance.addProperty("maxHit", event.getMaxHit());
		chance.addProperty("damage", event.getDamage());
		chance.addProperty("hpBefore", event.getHpBefore());
		chance.addProperty("hpAfter", event.getHpAfter());
		chance.addProperty("prayer", event.getPrayer());
		header.add("alert", chance);
		JsonObject monsters = new JsonObject();
		for (int id : recorder.npcIds())
		{
			MonsterInfo info = maxHits.get(id);
			if (info != null)
			{
				monsters.add(String.valueOf(id), DebugRecorder.monster(info));
			}
		}
		return gson.toJson(recorder.report(header, monsters));
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
			discord.send(event, config.discordTemplate(), webhooks, config.sendScreenshot(), debugReport(event),
				DiscordNotifier.Delivery.from(config));
		}
	}

	private void playSound()
	{
		SoundType type = config.soundType();
		log.debug("Playing {} at {}%", type, config.soundVolume());
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
				log.debug("Started {} at {} dB", type, gain);
			}
			catch (IOException | UnsupportedAudioFileException | LineUnavailableException | RuntimeException e)
			{
				log.debug("Could not play {}", type, e);
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

	/** Posts the sound folder's path in chat: plugins may not open it, as that starts a process. */
	private void showSoundFolder()
	{
		try
		{
			Filepath dir = getPluginDirectory();
			dir.createDirectories();
			String message = "Chanced: put " + ChancedConfig.CUSTOM_SOUND_FILE + " in " + dir;
			clientThread.invoke(() -> chat(message));
		}
		catch (IOException e)
		{
			log.debug("Could not create the sound folder", e);
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
