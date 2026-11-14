package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.HitsplatID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import org.junit.Test;

/** Whole fights through the matcher, recorded and replayed as the plugin's debug report. */
public class HitMatcherTest
{
	/** Vorkath's wiki max hits. */
	private static final int VORKATH_MELEE = 32;
	private static final int VORKATH_RANGED = 32;
	private static final int VORKATH_MAGIC = 30;
	private static final int VORKATH_BOMB = 121;
	private static final String BOMB = "Dragonfire Bomb";
	private static final int VORKATH_INDEX = 7;

	private static final int PLAYER_X = 2272;
	private static final int PLAYER_Y = 4065;
	private static final int TWO_TILES = 2;
	private static final int START_TICK = 100;
	private static final int FLIGHT_TICKS = 3;
	private static final int LATER = 10;
	private static final int ACID = 8;
	private static final int LOW_HP = 2;
	private static final int BOMB_HIT = 40;
	private static final int HP_AFTER_BOMB = 30;
	/** 10 + 40 = 50 HP before: under half the bomb's 121. */
	private static final int HP_AFTER_HALF_BOMB = 10;
	private static final int RUBY_SELF_DAMAGE = 1;
	private static final int ONE_HP_AFTER = 1;
	private static final int DISTANCE = 6;
	private static final int HP_AT_DRINK = 30;
	private static final int DIVINE_DAMAGE = 10;
	private static final int BLOCKED = 0;
	private static final int HP_AFTER_DIVINE = 20;
	private static final int OVERLOAD_HIT = 10;
	private static final int OVERLOAD_HITS = 5;
	private static final int HP_AT_OVERLOAD = 60;
	private static final int ABSORPTION_POINTS = 200;

	private static final ActorRef VORKATH = ActorRef.npc(VORKATH_INDEX, NpcID.VORKATH, "Vorkath");
	private static final WorldPoint PLAYER = new WorldPoint(PLAYER_X, PLAYER_Y, 0);
	private static final Set<AttackStyle> NONE = EnumSet.noneOf(AttackStyle.class);

	/** Drives the matcher and recorder the way the plugin does, one game tick at a time. */
	private static final class Fight
	{
		final HitMatcher matcher;
		final DebugRecorder recorder = new DebugRecorder();
		final Map<Integer, MonsterInfo> monsters = new HashMap<>();
		int tick = START_TICK;
		WorldPoint at = PLAYER;
		Set<AttackStyle> prayers = NONE;
		int absorption;

		Fight()
		{
			MonsterInfo vorkath = new MonsterInfo("Vorkath");
			vorkath.put(AttackStyle.MELEE, VORKATH_MELEE);
			vorkath.put(AttackStyle.RANGED, VORKATH_RANGED);
			vorkath.put(AttackStyle.MAGIC, VORKATH_MAGIC);
			vorkath.putSpecial(VORKATH_BOMB, BOMB);
			monsters.put(NpcID.VORKATH, vorkath);
			matcher = new HitMatcher(monsters::get);
		}

		static int cycle(int tick)
		{
			return tick * HitMatcher.CYCLES_PER_TICK;
		}

		void projectile(int id, boolean atMe, WorldPoint target, AttackStyle style)
		{
			int end = cycle(tick + FLIGHT_TICKS);
			recorder.projectile(tick, id, VORKATH, atMe, target, at, end, style, prayers);
			matcher.projectile(id, VORKATH, atMe, target, at, tick, end, style, prayers);
		}

		void selfDamage(SelfDamage source, int hp)
		{
			recorder.selfDamage(tick, source, hp);
			matcher.selfDamage(source, tick, hp);
		}

		/** Ends the tick with these hits on the player, then moves to the next tick. */
		HitMatcher.Result endTick(int hpAfter, int... hits)
		{
			TickState s = new TickState();
			s.tick = tick;
			s.cycle = cycle(tick);
			if (hits.length > 0)
			{
				s.hpAfter = hpAfter;
				s.local = at;
				s.prayers = prayers;
				s.absorption = absorption;
				TickState.ActorState vorkath = new TickState.ActorState();
				vorkath.ref = VORKATH;
				vorkath.npcId = NpcID.VORKATH;
				vorkath.name = "Vorkath";
				vorkath.interactingMe = true;
				vorkath.distance = DISTANCE;
				s.actors.add(vorkath);
				for (int hit : hits)
				{
					s.hits.add(new TickState.RawHit(hit, HitsplatID.DAMAGE_ME));
				}
			}
			HitMatcher.Result result = matcher.endTick(s);
			recorder.tick(s, result);
			recorder.prune(tick);
			tick++;
			return result;
		}

		void wait(int ticks)
		{
			for (int i = 0; i < ticks; i++)
			{
				endTick(0);
			}
		}

		JsonObject report()
		{
			JsonObject monstersJson = new JsonObject();
			monsters.forEach((id, info) -> monstersJson.add(String.valueOf(id), DebugRecorder.monster(info)));
			return recorder.report(new JsonObject(), monstersJson);
		}
	}

	@Test
	public void acidIsNotVorkathsBomb()
	{
		// GIVEN Vorkath seen attacking with its magic projectile
		Fight fight = new Fight();
		fight.projectile(SpotanimID.VORKATH_MAGIC_TRAVEL, true, PLAYER, AttackStyle.MAGIC);
		fight.wait(LATER);

		// WHEN an acid pool hits the player at low HP with no attack in flight
		HitMatcher.Result result = fight.endTick(LOW_HP, ACID);

		// THEN it is no chance: the hit counts at the damage it dealt, not Vorkath's bomb
		assertNull(result.verdict);
		assertEquals("none", result.decisions.get(0).how);
	}

	@Test
	public void rubyBoltSelfDamageIsNotVorkath()
	{
		// GIVEN Vorkath, not seen attacking yet, and a ruby bolt proc on it
		Fight fight = new Fight();
		fight.selfDamage(SelfDamage.RUBY_BOLT, LOW_HP + RUBY_SELF_DAMAGE);

		// WHEN the proc's self-damage lands at low HP
		HitMatcher.Result result = fight.endTick(ONE_HP_AFTER, RUBY_SELF_DAMAGE);

		// THEN it is the player's own damage, not a chance
		assertNull(result.verdict);
		assertEquals("self", result.decisions.get(0).how);
	}

	@Test
	public void bombOnThePlayersTileIsAChance()
	{
		// GIVEN Vorkath's bomb aimed at the player's tile
		Fight fight = new Fight();
		fight.projectile(SpotanimID.VORKATH_AREA_TRAVEL, false, PLAYER, null);
		fight.wait(FLIGHT_TICKS);

		// WHEN it lands on the player
		HitMatcher.Result result = fight.endTick(HP_AFTER_BOMB, BOMB_HIT);

		// THEN it is a chance at the bomb's max
		assertNotNull(result.verdict);
		assertEquals(VORKATH_BOMB, result.verdict.getMaxHit());
		assertEquals(BOMB, result.verdict.getStyle());
	}

	@Test
	public void bombNextToThePlayerIsHalf()
	{
		// GIVEN Vorkath's bomb aimed at the player's tile, and the player one tile away when it lands
		Fight fight = new Fight();
		fight.projectile(SpotanimID.VORKATH_AREA_TRAVEL, false, PLAYER, null);
		fight.wait(FLIGHT_TICKS);
		fight.at = PLAYER.dx(1);

		// WHEN it lands
		HitMatcher.Result result = fight.endTick(HP_AFTER_HALF_BOMB, BOMB_HIT);

		// THEN it is a chance at half the bomb's max
		assertNotNull(result.verdict);
		assertEquals((VORKATH_BOMB + 1) / TWO_TILES, result.verdict.getMaxHit());
	}

	@Test
	public void divinePotionHitDoesNotTakeABlockedAttacksPlace()
	{
		// GIVEN a magic attack landing through Protect from Magic as the player drinks a divine potion
		Fight fight = new Fight();
		fight.prayers = EnumSet.of(AttackStyle.MAGIC);
		fight.projectile(SpotanimID.VORKATH_MAGIC_TRAVEL, true, PLAYER, AttackStyle.MAGIC);
		fight.wait(FLIGHT_TICKS);
		fight.selfDamage(SelfDamage.DIVINE_POTION, HP_AT_DRINK);

		// WHEN the potion's 10 and the blocked 0 land on the same tick, the potion's first
		HitMatcher.Result result = fight.endTick(HP_AFTER_DIVINE, DIVINE_DAMAGE, BLOCKED);

		// THEN the 10 is the potion, not magic through prayer (Vorkath's bomb), and no chance
		assertNull(result.verdict);
		assertEquals("self", result.decisions.get(0).how);
		assertEquals("attack", result.decisions.get(1).how);
	}

	@Test
	public void overloadHitsAreAllThePlayers()
	{
		// GIVEN Vorkath seen attacking, and the player drinking an overload
		Fight fight = new Fight();
		fight.projectile(SpotanimID.VORKATH_MAGIC_TRAVEL, true, PLAYER, AttackStyle.MAGIC);
		fight.wait(LATER);
		fight.selfDamage(SelfDamage.OVERLOAD, HP_AT_OVERLOAD);

		// WHEN its five hits of 10 land, one every other tick, the last at 1 HP
		int self = 0;
		for (int i = 0; i < OVERLOAD_HITS; i++)
		{
			HitMatcher.Result result = fight.endTick(ONE_HP_AFTER, OVERLOAD_HIT);
			fight.wait(1);
			self += "self".equals(result.decisions.get(0).how) ? 1 : 0;
		}

		// THEN every hit is the player's own
		assertEquals(OVERLOAD_HITS, self);
	}

	@Test
	public void absorptionTakesTheHit()
	{
		// GIVEN Nightmare Zone absorption points left
		Fight fight = new Fight();
		fight.absorption = ABSORPTION_POINTS;
		fight.projectile(SpotanimID.VORKATH_AREA_TRAVEL, false, PLAYER, null);
		fight.wait(FLIGHT_TICKS);

		// WHEN a hit that could have killed lands at 1 HP
		HitMatcher.Result result = fight.endTick(ONE_HP_AFTER, BOMB_HIT);

		// THEN it is no chance: absorption took it
		assertNull(result.verdict);
	}

	@Test
	public void dodgedBombDoesNotExplainAnotherHit()
	{
		// GIVEN Vorkath's bomb aimed two tiles from where the player now stands
		Fight fight = new Fight();
		WorldPoint away = PLAYER.dx(TWO_TILES);
		fight.projectile(SpotanimID.VORKATH_AREA_TRAVEL, false, away, null);
		fight.wait(FLIGHT_TICKS);

		// WHEN acid hits the player at low HP as it lands
		HitMatcher.Result result = fight.endTick(LOW_HP, ACID);

		// THEN the bomb does not take the blame
		assertNull(result.verdict);
	}

	@Test
	public void reportReplaysToTheSameOutcome()
	{
		// GIVEN a recorded fight: Vorkath seen, then acid, then a bomb on the player
		Fight fight = new Fight();
		fight.projectile(SpotanimID.VORKATH_MAGIC_TRAVEL, true, PLAYER, AttackStyle.MAGIC);
		fight.wait(LATER);
		HitMatcher.Result acid = fight.endTick(LOW_HP, ACID);
		fight.projectile(SpotanimID.VORKATH_AREA_TRAVEL, false, PLAYER, null);
		fight.wait(FLIGHT_TICKS);
		int bombTick = fight.tick;
		HitMatcher.Result bomb = fight.endTick(HP_AFTER_BOMB, BOMB_HIT);

		// WHEN its debug report is replayed through a fresh matcher
		Map<Integer, HitMatcher.Result> replayed = ReportReplay.replay(fight.report());

		// THEN every tick with hits comes out the same
		assertNull(acid.verdict);
		assertEquals(Collections.singleton(bombTick), chanceTicks(replayed));
		assertEquals(bomb.verdict, replayed.get(bombTick).verdict);
	}

	static Set<Integer> chanceTicks(Map<Integer, HitMatcher.Result> results)
	{
		Set<Integer> ticks = new TreeSet<>();
		results.forEach((tick, result) ->
		{
			if (result.verdict != null)
			{
				ticks.add(tick);
			}
		});
		return ticks;
	}
}
