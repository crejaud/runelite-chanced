package com.crejaud.chanced;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Replays a debug report's matcher inputs through a fresh {@link HitMatcher}. */
final class ReportReplay
{
	private static final int X = 0;
	private static final int Y = 1;
	private static final int PLANE = 2;
	/** The bundled attack style table, as the plugin loads it. */
	static final AttackStyleTable STYLES = new AttackStyleTable();

	private ReportReplay()
	{
	}

	/** The outcome of every tick with hits on the player, by tick. */
	static Map<Integer, HitMatcher.Result> replay(JsonObject report)
	{
		Map<Integer, MonsterInfo> monsters = monsters(report.getAsJsonObject("monsters"));
		if (report.get("format").getAsInt() != DebugRecorder.FORMAT_VERSION)
		{
			throw new IllegalArgumentException("Report format " + report.get("format") + " is not "
				+ DebugRecorder.FORMAT_VERSION + ": convert it, or replay it with the plugin version that wrote it");
		}
		HitMatcher matcher = new HitMatcher(monsters::get, STYLES);
		for (JsonElement id : report.getAsJsonArray("seenBefore"))
		{
			matcher.seedSeenAttackers(Collections.singleton(id.getAsInt()));
		}
		Map<Integer, HitMatcher.Result> results = new LinkedHashMap<>();
		for (JsonElement element : report.getAsJsonArray("events"))
		{
			JsonObject e = element.getAsJsonObject();
			if (!e.has("m"))
			{
				continue;
			}
			int tick = e.get("t").getAsInt();
			switch (e.get("k").getAsString())
			{
				case DebugRecorder.ANIMATION:
					ActorRef actor = ref(e.get("a"));
					Set<AttackStyle> prayers = styles(e.getAsJsonArray("pray"));
					if (actor.isPlayer())
					{
						matcher.playerAnimation(actor, tick, prayers);
					}
					else
					{
						matcher.npcAnimation(actor, tick, e.get("id").getAsInt(), prayers);
					}
					break;
				case DebugRecorder.PROJECTILE:
					List<ActorRef> near = new ArrayList<>();
					e.getAsJsonArray("near").forEach(n -> near.add(ref(n)));
					matcher.projectile(e.get("id").getAsInt(), ref(e.get("a")), near, e.get("me").getAsBoolean(),
						point(e.get("to")), point(e.get("at")), tick, e.get("end").getAsInt(),
						styles(e.getAsJsonArray("pray")));
					break;
				case DebugRecorder.INVENTORY:
					if (e.get("delta").getAsInt() < 0)
					{
						matcher.itemUsed(tick, e.get("name").getAsString(), e.get("hp").getAsInt());
					}
					break;
				case DebugRecorder.CHAT:
					matcher.gameMessage(tick, e.get("msg").getAsString(), e.get("hp").getAsInt());
					break;
				case DebugRecorder.SPOTANIM:
					for (JsonElement id : e.getAsJsonArray("ids"))
					{
						matcher.targetSpotAnim(tick, id.getAsInt(), e.get("hp").getAsInt());
					}
					break;
				case DebugRecorder.TICK:
					TickState s = tickState(e);
					HitMatcher.Result result = matcher.endTick(s);
					if (!s.hits.isEmpty())
					{
						results.put(tick, result);
					}
					break;
				default:
					throw new IllegalArgumentException("Unknown matcher input " + e);
			}
		}
		return results;
	}

	private static TickState tickState(JsonObject e)
	{
		TickState s = new TickState();
		s.tick = e.get("t").getAsInt();
		s.cycle = e.get("c").getAsInt();
		if (!e.has("hits"))
		{
			return s;
		}
		s.hpAfter = e.get("hp").getAsInt();
		s.local = point(e.get("at"));
		s.prayers = styles(e.getAsJsonArray("pray"));
		s.pvpActive = e.get("pvp").getAsBoolean();
		s.antiDragonShield = e.get("shield").getAsBoolean();
		s.antifire = e.get("antifire").getAsBoolean();
		s.superAntifire = e.get("superAntifire").getAsBoolean();
		s.hunllefArmour = e.get("hunllefArmour").getAsInt();
		s.absorption = e.get("absorption").getAsInt();
		for (JsonElement element : e.getAsJsonArray("actors"))
		{
			JsonObject o = element.getAsJsonObject();
			TickState.ActorState a = new TickState.ActorState();
			a.ref = ref(o.get("a"));
			a.npcId = o.get("npc").getAsInt();
			a.name = o.has("name") ? o.get("name").getAsString() : null;
			a.interactingMe = o.get("me").getAsBoolean();
			a.dead = o.get("dead").getAsBoolean();
			a.distance = o.get("d").getAsInt();
			a.meleeRange = o.get("melee").getAsBoolean();
			s.actors.add(a);
		}
		for (JsonElement element : e.getAsJsonArray("hits"))
		{
			JsonArray hit = element.getAsJsonArray();
			s.hits.add(new TickState.RawHit(hit.get(0).getAsInt(), hit.get(1).getAsInt()));
		}
		return s;
	}

	private static Map<Integer, MonsterInfo> monsters(JsonObject json)
	{
		Map<Integer, MonsterInfo> monsters = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : json.entrySet())
		{
			JsonObject o = entry.getValue().getAsJsonObject();
			MonsterInfo info = new MonsterInfo(o.get("name").getAsString());
			for (Map.Entry<String, JsonElement> max : o.getAsJsonObject("max").entrySet())
			{
				AttackStyle style = AttackStyle.valueOf(max.getKey());
				if (style == AttackStyle.SPECIAL)
				{
					info.putSpecial(max.getValue().getAsInt(), o.get("special").getAsString());
				}
				else
				{
					info.put(style, max.getValue().getAsInt());
				}
			}
			monsters.put(Integer.parseInt(entry.getKey()), info);
		}
		return monsters;
	}

	private static ActorRef ref(JsonElement json)
	{
		if (json == null || json.isJsonNull())
		{
			return null;
		}
		JsonObject o = json.getAsJsonObject();
		if (o.get("p").getAsBoolean())
		{
			return ActorRef.player(o.get("i").getAsInt());
		}
		return ActorRef.npc(o.get("i").getAsInt(), o.get("n").getAsInt(),
			o.has("name") ? o.get("name").getAsString() : null);
	}

	private static WorldPoint point(JsonElement json)
	{
		if (json == null || json.isJsonNull())
		{
			return null;
		}
		JsonArray a = json.getAsJsonArray();
		return new WorldPoint(a.get(X).getAsInt(), a.get(Y).getAsInt(), a.get(PLANE).getAsInt());
	}

	private static Set<AttackStyle> styles(JsonArray json)
	{
		Set<AttackStyle> styles = EnumSet.noneOf(AttackStyle.class);
		for (JsonElement e : json)
		{
			styles.add(AttackStyle.valueOf(e.getAsString()));
		}
		return styles;
	}
}
