package com.crejaud.chanced;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps the last {@value #KEEP_TICKS} ticks of what the plugin saw, for the debug report attached
 * to a Discord message. Events marked {@code "m": true} are the matcher's raw inputs, what the
 * client showed before any decision, so a test replays a report through {@link HitMatcher} and
 * re-runs every decision; the rest are clues for a person reading it.
 * It holds no other player's name, no estimate of their gear and no public chat, so a replayed
 * PvP hit has no max hit to count.
 */
final class DebugRecorder
{
	static final int FORMAT_VERSION = 2;
	/** About a minute of game ticks. */
	static final int KEEP_TICKS = 100;

	static final String ANIMATION = "anim";
	static final String PROJECTILE = "proj";
	static final String TICK = "tick";
	static final String TARGET_HIT = "targetHit";
	static final String SPOTANIM = "spotanim";
	static final String CHAT = "chat";
	static final String INVENTORY = "inv";
	static final String OBJECT = "object";
	static final String GRAPHIC = "graphic";
	static final String TARGET = "target";

	private final Deque<JsonObject> events = new ArrayDeque<>();
	/** NPC ids the matcher had seen attacking in events already dropped from the window. */
	private final Set<Integer> seenBefore = new HashSet<>();

	void clear()
	{
		events.clear();
		seenBefore.clear();
	}

	/**
	 * An NPC's animation near the player, or a player's attacking them in PvP.
	 *
	 * @param seen the NPC id it showed attacking, or -1
	 */
	void animation(int tick, ActorRef actor, int animation, int seen, Set<AttackStyle> prayers)
	{
		JsonObject e = event(tick, ANIMATION, true);
		e.add("a", ref(actor));
		e.addProperty("id", animation);
		e.addProperty("seen", seen);
		e.add("pray", styles(prayers));
	}

	/**
	 * A projectile at its launch: the source the client gave, or, when it gave none, the NPCs on or
	 * next to its launch tile.
	 *
	 * @param seen the NPC id it showed attacking, or -1
	 */
	void projectile(int tick, int id, ActorRef clientSource, List<ActorRef> nearSource, boolean atMe,
		WorldPoint target, WorldPoint local, int endCycle, int seen, Set<AttackStyle> prayers)
	{
		JsonObject e = event(tick, PROJECTILE, true);
		e.addProperty("id", id);
		e.add("a", ref(clientSource));
		JsonArray near = new JsonArray();
		if (nearSource != null)
		{
			nearSource.forEach(r -> near.add(ref(r)));
		}
		e.add("near", near);
		e.addProperty("me", atMe);
		e.add("to", point(target));
		e.add("at", point(local));
		e.addProperty("end", endCycle);
		e.addProperty("seen", seen);
		e.add("pray", styles(prayers));
	}

	/** The end of a tick: the matcher's input and, on ticks with hits, what it decided. */
	void tick(TickState s, HitMatcher.Result result)
	{
		JsonObject e = event(s.tick, TICK, true);
		e.addProperty("c", s.cycle);
		if (s.hits.isEmpty())
		{
			return;
		}
		e.addProperty("hp", s.hpAfter);
		e.add("at", point(s.local));
		e.add("pray", styles(s.prayers));
		e.addProperty("pvp", s.pvpActive);
		e.addProperty("shield", s.antiDragonShield);
		e.addProperty("antifire", s.antifire);
		e.addProperty("superAntifire", s.superAntifire);
		e.addProperty("absorption", s.absorption);
		e.addProperty("hunllefArmour", s.hunllefArmour);
		JsonArray actors = new JsonArray();
		for (TickState.ActorState a : s.actors)
		{
			JsonObject o = new JsonObject();
			o.add("a", ref(a.ref));
			o.addProperty("npc", a.npcId);
			if (a.name != null && !a.ref.isPlayer())
			{
				o.addProperty("name", a.name);
			}
			o.addProperty("me", a.interactingMe);
			o.addProperty("dead", a.dead);
			o.addProperty("d", a.distance);
			o.addProperty("melee", a.meleeRange);
			actors.add(o);
		}
		e.add("actors", actors);
		JsonArray hits = new JsonArray();
		for (TickState.RawHit h : s.hits)
		{
			JsonArray hit = new JsonArray();
			hit.add(h.amount);
			hit.add(h.type);
			hits.add(hit);
		}
		e.add("hits", hits);
		e.add("out", outcome(result));
	}

	void targetHit(int tick, int amount, int type)
	{
		JsonObject e = event(tick, TARGET_HIT, false);
		e.addProperty("amount", amount);
		e.addProperty("type", type);
	}

	/** Spotanims now on the player ({@code on} "me") or their target ("target"), at {@code hp}. */
	void spotAnims(int tick, String on, JsonArray ids, int hp)
	{
		JsonObject e = event(tick, SPOTANIM, TARGET.equals(on));
		e.addProperty("on", on);
		e.add("ids", ids);
		e.addProperty("hp", hp);
	}

	/** A game message: these are about the player, never other players' chat. */
	void chat(int tick, String type, String message, int hp)
	{
		JsonObject e = event(tick, CHAT, true);
		e.addProperty("type", type);
		e.addProperty("msg", message);
		e.addProperty("hp", hp);
	}

	/** An inventory item's count changed by {@code delta}, at {@code hp} hitpoints. */
	void inventory(int tick, int itemId, String name, int delta, int hp)
	{
		JsonObject e = event(tick, INVENTORY, true);
		e.addProperty("id", itemId);
		e.addProperty("name", name);
		e.addProperty("delta", delta);
		e.addProperty("hp", hp);
	}

	/** A game object spawned on or next to the player's tile: acid pools and the like. */
	void object(int tick, int id, WorldPoint at)
	{
		JsonObject e = event(tick, OBJECT, false);
		e.addProperty("id", id);
		e.add("to", point(at));
	}

	/** A graphics object on or next to the player's tile: falling rocks, lightning and the like. */
	void graphic(int tick, int id, WorldPoint at)
	{
		JsonObject e = event(tick, GRAPHIC, false);
		e.addProperty("id", id);
		e.add("to", point(at));
	}

	/** Drops events older than the window, remembering which NPCs they showed attacking. */
	void prune(int tick)
	{
		while (!events.isEmpty() && events.peekFirst().get("t").getAsInt() < tick - KEEP_TICKS)
		{
			JsonObject e = events.pollFirst();
			Integer seen = seenAttacker(e);
			if (seen != null)
			{
				seenBefore.add(seen);
			}
		}
	}

	/** Every NPC id the window mentions, for the report's max hit table. */
	Set<Integer> npcIds()
	{
		Set<Integer> ids = new HashSet<>();
		for (JsonObject e : events)
		{
			addNpc(ids, e.get("a"));
			if (e.has("actors"))
			{
				for (JsonElement a : e.getAsJsonArray("actors"))
				{
					ids.add(a.getAsJsonObject().get("npc").getAsInt());
				}
			}
		}
		ids.remove(-1);
		return ids;
	}

	/**
	 * The report: {@code header} plus the window's events, the NPC ids seen attacking before it
	 * and the max hits used.
	 */
	JsonObject report(JsonObject header, JsonObject monsters)
	{
		JsonObject report = header.deepCopy();
		report.addProperty("format", FORMAT_VERSION);
		JsonArray seen = new JsonArray();
		seenBefore.forEach(seen::add);
		report.add("seenBefore", seen);
		report.add("monsters", monsters);
		JsonArray list = new JsonArray();
		events.forEach(e -> list.add(e.deepCopy()));
		report.add("events", list);
		return report;
	}

	static JsonObject monster(MonsterInfo info)
	{
		JsonObject o = new JsonObject();
		o.addProperty("name", info.getName());
		o.addProperty("special", info.getSpecialName());
		JsonObject max = new JsonObject();
		info.getMaxHits().forEach((style, value) -> max.addProperty(style.name(), value));
		o.add("max", max);
		return o;
	}

	/** The NPC id an event showed attacking, as {@link HitMatcher} counts it, or null. */
	static Integer seenAttacker(JsonObject e)
	{
		return e.has("seen") && e.get("seen").getAsInt() >= 0 ? e.get("seen").getAsInt() : null;
	}

	private JsonObject event(int tick, String kind, boolean matcherInput)
	{
		JsonObject e = new JsonObject();
		e.addProperty("t", tick);
		e.addProperty("k", kind);
		if (matcherInput)
		{
			e.addProperty("m", true);
		}
		events.addLast(e);
		return e;
	}

	private static void addNpc(Set<Integer> ids, JsonElement ref)
	{
		if (ref != null && ref.isJsonObject() && !ref.getAsJsonObject().get("p").getAsBoolean())
		{
			ids.add(ref.getAsJsonObject().get("n").getAsInt());
		}
	}

	private static JsonObject outcome(HitMatcher.Result result)
	{
		JsonObject out = new JsonObject();
		JsonArray decisions = new JsonArray();
		for (HitMatcher.Decision d : result.decisions)
		{
			JsonObject o = new JsonObject();
			o.addProperty("damage", d.damage);
			o.addProperty("how", d.how);
			if (d.self != null)
			{
				o.addProperty("self", d.self.name());
			}
			o.add("a", ref(d.source));
			if (d.style != null)
			{
				o.addProperty("style", d.style.name());
				o.addProperty("max", d.max);
				o.addProperty("prayed", d.prayed);
			}
			decisions.add(o);
		}
		out.add("decisions", decisions);
		Verdict v = result.verdict;
		if (v != null)
		{
			JsonObject verdict = new JsonObject();
			verdict.addProperty("source", v.getSource());
			verdict.addProperty("style", v.getStyle());
			verdict.addProperty("maxHit", v.getMaxHit());
			verdict.addProperty("damage", v.getDamage());
			verdict.addProperty("hpBefore", v.getHpBefore());
			out.add("chance", verdict);
		}
		return out;
	}

	static JsonElement ref(ActorRef ref)
	{
		if (ref == null)
		{
			return JsonNull.INSTANCE;
		}
		JsonObject o = new JsonObject();
		o.addProperty("p", ref.isPlayer());
		o.addProperty("i", ref.getIndex());
		if (!ref.isPlayer())
		{
			o.addProperty("n", ref.getNpcId());
			if (ref.getName() != null)
			{
				o.addProperty("name", ref.getName());
			}
		}
		return o;
	}

	static JsonElement point(WorldPoint p)
	{
		if (p == null)
		{
			return JsonNull.INSTANCE;
		}
		JsonArray a = new JsonArray();
		a.add(p.getX());
		a.add(p.getY());
		a.add(p.getPlane());
		return a;
	}

	static JsonArray styles(Set<AttackStyle> styles)
	{
		JsonArray a = new JsonArray();
		if (styles != null)
		{
			styles.forEach(s -> a.add(s.name()));
		}
		return a;
	}
}
