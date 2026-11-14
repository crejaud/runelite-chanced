package com.crejaud.chanced;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HitsplatID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpotanimID;

/**
 * Matches the damage hitsplats on the player to the attacks seen starting near them. It reads
 * plain data only, so the plugin feeds it from the client and tests replay it from a debug report.
 */
@Slf4j
final class HitMatcher
{
	/** Ticks an attack animation stays a candidate for a hit (NPC melee lands within a tick). */
	static final int NPC_ANIMATION_WINDOW = 2;
	/** Players' spells without projectiles (barrage) land a few ticks after the cast. */
	static final int PLAYER_ANIMATION_WINDOW = 4;
	static final int CYCLES_PER_TICK = 30;
	/** Projectiles still unmatched this many ticks after launch are dropped. */
	static final int MAX_PROJECTILE_TICKS = 12;
	/** A projectile counts as landed this tick if it landed up to this many ticks ago. */
	private static final int LANDED_WINDOW_TICKS = 2;
	/** An area special hits the player within this many tiles of where it lands. */
	static final int AREA_RADIUS = 1;
	/** An area special next to the player deals half its damage. */
	private static final int ADJACENT_DIVISOR = 2;
	private static final String KBD = "King Black Dragon";

	/** Projectiles aimed at a tile that are an NPC's special: Vorkath's dragonfire bomb. */
	static final Set<Integer> AREA_SPECIALS = ImmutableSet.of(SpotanimID.VORKATH_AREA_TRAVEL);

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

	/** An attack seen starting: an animation or a projectile. */
	static final class Pending
	{
		ActorRef actor;
		int startTick;
		/** Projectile end cycle, or 0 for animations. */
		int endCycle;
		boolean projectile;
		/** Style the animation or projectile name implies; null when unknown. */
		AttackStyle style;
		/** Where an area special lands, or null for attacks aimed at the player. */
		WorldPoint areaTarget;
		/** Protection prayers up when the attack started. */
		Set<AttackStyle> protectedAtStart = EnumSet.noneOf(AttackStyle.class);
		boolean consumed;
	}

	/** The style of a projectile seen leaving an NPC, and the tick it was seen. */
	static final class SeenProjectile
	{
		final AttackStyle style;
		final int tick;

		SeenProjectile(AttackStyle style, int tick)
		{
			this.style = style;
			this.tick = tick;
		}
	}

	/** Self-damage the player just triggered, waiting for its hits. */
	static final class Expectation
	{
		SelfDamage source;
		int startTick;
		int min;
		int max;
		/** Hits still to come. */
		int left;
	}

	/** How one hit was put on an attack, for the debug report. */
	static final class Decision
	{
		int damage;
		/** attack, same, self, melee, unseen, hazard, none or overtime. */
		String how;
		/** The self-damage source, for "self". */
		SelfDamage self;
		ActorRef source;
		AttackStyle style;
		int max;
		boolean prayed;
	}

	/** A tick's outcome: the chance, if any, and how each hit was matched. */
	static final class Result
	{
		Verdict verdict;
		final List<Decision> decisions = new ArrayList<>();
	}

	/** Who an unseen hit is put on, for an NPC attacking the player. */
	enum Blame
	{
		/** Not this NPC: the hit was the environment or the player's own gear. */
		NONE,
		/** Its melee, whose animation we do not know. */
		MELEE,
		/** The worst of its normal styles: an attack whose animation we do not know. */
		WORST_NORMAL,
		/** The Hunllef's tornadoes and floor tiles. */
		HUNLLEF_HAZARD
	}

	private final IntFunction<MonsterInfo> maxHits;
	private final List<Pending> pending = new ArrayList<>();
	/** Each NPC's latest projectile with a known style, whoever it targets. */
	private final Map<ActorRef, SeenProjectile> lastProjectiles = new HashMap<>();
	/** NPC ids seen attacking by an animation or projectile we know. */
	private final Set<Integer> seenAttackers = new HashSet<>();
	private final List<Expectation> expectations = new ArrayList<>();

	HitMatcher(IntFunction<MonsterInfo> maxHits)
	{
		this.maxHits = maxHits;
	}

	void clear()
	{
		pending.clear();
		lastProjectiles.clear();
		seenAttackers.clear();
		expectations.clear();
	}

	static boolean isOverTime(int hitsplatType)
	{
		return OVER_TIME.contains(hitsplatType);
	}

	/** A hitsplat type the matcher counts: damage, blocks and damage over time. */
	static boolean counts(int hitsplatType)
	{
		return OVER_TIME.contains(hitsplatType) || DAMAGE.contains(hitsplatType);
	}

	Set<Integer> seenAttackers()
	{
		return Collections.unmodifiableSet(seenAttackers);
	}

	/** NPC ids seen attacking before a replay starts. */
	void seedSeenAttackers(Collection<Integer> npcIds)
	{
		seenAttackers.addAll(npcIds);
	}

	/**
	 * An attack animation: an NPC's with a known attack hint, or a player's attacking us in PvP.
	 *
	 * @param style the style the animation implies, null when unknown
	 */
	void animation(ActorRef actor, int tick, AttackStyle style, Set<AttackStyle> prayers)
	{
		Pending p = new Pending();
		p.actor = actor;
		p.startTick = tick;
		p.style = style;
		p.protectedAtStart = EnumSet.copyOf(prayersOrNone(prayers));
		if (!actor.isPlayer())
		{
			seenAttackers.add(actor.getNpcId());
		}
		pending.add(p);
	}

	/**
	 * A projectile seen at its launch.
	 *
	 * @param source the actor it came from, null when unknown
	 * @param atMe   it targets the local player
	 * @param target the tile it is aimed at
	 * @param local  the player's tile at launch
	 * @param style  the style its name implies, null when unknown
	 */
	void projectile(int id, ActorRef source, boolean atMe, WorldPoint target, WorldPoint local, int tick, int endCycle,
		AttackStyle style, Set<AttackStyle> prayers)
	{
		boolean npc = source != null && !source.isPlayer();
		if (style != null && npc)
		{
			lastProjectiles.put(source, new SeenProjectile(style, tick));
			seenAttackers.add(source.getNpcId());
		}
		WorldPoint areaTarget = null;
		AttackStyle pendingStyle = style;
		if (!atMe)
		{
			// A special aimed at the player's tile, not at the player.
			if (!npc || !AREA_SPECIALS.contains(id) || target == null || !inArea(target, local))
			{
				return;
			}
			areaTarget = target;
			pendingStyle = AttackStyle.SPECIAL;
		}
		Pending p = new Pending();
		p.actor = source;
		p.startTick = tick;
		p.endCycle = endCycle;
		p.projectile = true;
		p.style = pendingStyle;
		p.areaTarget = areaTarget;
		p.protectedAtStart = EnumSet.copyOf(prayersOrNone(prayers));
		pending.add(p);
	}

	/**
	 * The only NPC that started an attack animation in the last {@value #NPC_ANIMATION_WINDOW}
	 * ticks, or null when none or several did: the source of a projectile that names none.
	 */
	ActorRef onlyAnimatingNpc(int tick)
	{
		ActorRef animating = null;
		for (Pending p : pending)
		{
			if (!p.projectile && p.actor != null && !p.actor.isPlayer() && p.startTick >= tick - NPC_ANIMATION_WINDOW)
			{
				if (animating != null && !animating.equals(p.actor))
				{
					return null;
				}
				animating = p.actor;
			}
		}
		return animating;
	}

	/**
	 * The player triggered self-damage: drank a divine potion, proc'd a ruby bolt. Hits of its
	 * size in the next few ticks are the player's own, before any attack can claim them.
	 *
	 * @param hp hitpoints when it was triggered
	 */
	void selfDamage(SelfDamage source, int tick, int hp)
	{
		Expectation e = new Expectation();
		e.source = source;
		e.startTick = tick;
		e.min = source.min(hp);
		e.max = source.max(hp);
		e.left = source.getHits();
		expectations.add(e);
	}

	/** Matches the tick's hits, then drops attacks too old to land. */
	Result endTick(TickState s)
	{
		Result result = new Result();
		if (!s.hits.isEmpty())
		{
			evaluate(s, result);
		}
		int tick = s.tick;
		pending.removeIf(p -> p.consumed || tick - p.startTick > MAX_PROJECTILE_TICKS
			|| (!p.projectile && tick - p.startTick > PLAYER_ANIMATION_WINDOW));
		lastProjectiles.values().removeIf(seen -> tick - seen.tick > MAX_PROJECTILE_TICKS);
		expectations.removeIf(e -> e.left <= 0 || tick >= e.startTick + e.source.getTicks());
		return result;
	}

	private void evaluate(TickState s, Result result)
	{
		Iterator<Pending> next = candidates(s).iterator();
		List<Hit> hits = new ArrayList<>();
		Attack last = null;
		Decision lastDecision = null;
		for (TickState.RawHit raw : s.hits)
		{
			Decision d = new Decision();
			d.damage = raw.amount;
			result.decisions.add(d);
			if (isOverTime(raw.type))
			{
				d.how = "overtime";
				hits.add(new Hit(raw.amount, true, null));
				continue;
			}
			Expectation self = expected(raw.amount, s.tick);
			if (self != null)
			{
				self.left--;
				d.how = "self";
				d.self = self.source;
				hits.add(new Hit(raw.amount, false, null));
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
				consume(p, s.tick);
				attack = toAttack(p, false, s);
				d.how = "attack";
				d.source = p.actor;
			}
			if (attack == null && last != null)
			{
				// Out of seen attacks: a second hitsplat of the same attack.
				attack = last;
				d.how = "same";
				d.source = lastDecision.source;
			}
			else if (attack == null)
			{
				attack = fallback(s, d);
			}
			if (attack != null)
			{
				last = attack;
				lastDecision = d;
				d.style = attack.getStyle();
				d.max = attack.getMax();
				d.prayed = attack.isPrayed();
			}
			else
			{
				d.how = "none";
				d.source = null;
			}
			log.debug("Chanced: tick {} hit {} {} from {} style {} max {} prayed {}", s.tick, raw.amount, d.how,
				d.source, d.style, d.max, d.prayed);
			hits.add(new Hit(raw.amount, false, attack));
		}
		// Nightmare Zone's absorption takes the damage, so the hitsplats are not hitpoints lost.
		result.verdict = s.absorption > 0 ? null : ChanceCalculator.evaluate(hits, s.hpAfter);
	}

	/** Self-damage the player triggered that a hit of this size, this tick, can be. */
	private Expectation expected(int amount, int tick)
	{
		for (Expectation e : expectations)
		{
			if (e.left > 0 && tick >= e.startTick && tick < e.startTick + e.source.getTicks()
				&& amount >= e.min && amount <= e.max)
			{
				return e;
			}
		}
		return null;
	}

	/** Attacks that could have landed this tick, most certain first. */
	private List<Pending> candidates(TickState s)
	{
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
				if (p.endCycle <= s.cycle + CYCLES_PER_TICK
					&& p.endCycle >= s.cycle - LANDED_WINDOW_TICKS * CYCLES_PER_TICK
					&& (p.areaTarget == null || inArea(p.areaTarget, s.local)))
				{
					landed.add(p);
				}
				continue;
			}
			int window = p.actor.isPlayer() ? PLAYER_ANIMATION_WINDOW : NPC_ANIMATION_WINDOW;
			if (p.startTick < s.tick - window || inFlightFrom(p.actor, s.cycle))
			{
				continue;
			}
			(s.interactingMe(p.actor) ? atMe : others).add(p);
		}
		landed.addAll(atMe);
		landed.addAll(others);
		return landed;
	}

	/** The actor has a projectile on its way to us, so its animation's hit has not landed yet. */
	private boolean inFlightFrom(ActorRef actor, int cycle)
	{
		for (Pending p : pending)
		{
			if (p.projectile && !p.consumed && p.areaTarget == null && actor.equals(p.actor)
				&& p.endCycle > cycle + CYCLES_PER_TICK)
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
			if (!p.consumed && used.actor.equals(p.actor) && (!p.projectile || used.projectile)
				&& p.startTick >= tick - PLAYER_ANIMATION_WINDOW && p.startTick <= used.startTick + 1)
			{
				p.consumed = true;
			}
		}
	}

	/**
	 * Whether an unseen hit can be this NPC's. An unseen hit is never put on a special: the wiki's
	 * specials are mostly floor hazards (Vorkath's bomb, Scurrius's rockfall) that only their own
	 * projectile shows, and the hit may be the floor (Vorkath's acid) or the player's own gear
	 * (ruby bolts). An NPC seen attacking by a known animation or projectile shows its ranged and
	 * magic attacks, so an unseen hit from it is only its melee, from next to the player. An NPC
	 * never seen attacking may use an animation we do not know: its normal styles count, unless it
	 * has a special, as bosses with floor hazards do. The Hunllef's tornadoes and floor tiles are
	 * the exception: they are its unseen hits.
	 */
	static Blame blame(MonsterInfo info, boolean seenAttacking, boolean hunllef, boolean inMeleeRange)
	{
		if (info == null)
		{
			return Blame.NONE;
		}
		if (hunllef)
		{
			return Blame.HUNLLEF_HAZARD;
		}
		if (seenAttacking)
		{
			return inMeleeRange && info.has(AttackStyle.MELEE) ? Blame.MELEE : Blame.NONE;
		}
		return info.has(AttackStyle.SPECIAL) || !info.hasNormalStyle() ? Blame.NONE : Blame.WORST_NORMAL;
	}

	/** No seen attack explains the hit: blame the nearest NPC attacking us that could have dealt it. */
	private Attack fallback(TickState s, Decision d)
	{
		TickState.ActorState nearest = null;
		Blame nearestBlame = Blame.NONE;
		for (TickState.ActorState a : s.actors)
		{
			if (a.ref.isPlayer() || !a.interactingMe || a.dead)
			{
				continue;
			}
			Blame blame = blame(maxHits.apply(a.npcId), seenAttackers.contains(a.npcId),
				HunllefRules.isHunllef(a.npcId), a.meleeRange);
			if (blame != Blame.NONE && (nearest == null || a.distance < nearest.distance))
			{
				nearest = a;
				nearestBlame = blame;
			}
		}
		if (nearest == null)
		{
			return null;
		}
		Pending p = new Pending();
		p.actor = nearest.ref;
		p.protectedAtStart = EnumSet.copyOf(prayersOrNone(s.prayers));
		d.source = nearest.ref;
		if (nearestBlame == Blame.HUNLLEF_HAZARD)
		{
			d.how = "hazard";
			return toAttack(p, true, s);
		}
		d.how = nearestBlame == Blame.MELEE ? "melee" : "unseen";
		p.style = nearestBlame == Blame.MELEE ? AttackStyle.MELEE : null;
		p.startTick = s.tick;
		return toAttack(p, false, s);
	}

	private Attack toAttack(Pending p, boolean unexplained, TickState s)
	{
		if (p.actor.isPlayer())
		{
			return s.pvpActive ? playerAttack(p, s) : null;
		}
		return npcAttack(p, unexplained, s);
	}

	private Attack npcAttack(Pending p, boolean unexplained, TickState s)
	{
		TickState.ActorState state = s.actor(p.actor);
		int npcId = state != null ? state.npcId : p.actor.getNpcId();
		MonsterInfo info = maxHits.apply(npcId);
		if (info == null)
		{
			return null;
		}
		Set<AttackStyle> prayers = s.prayers;
		if (!p.projectile && !unexplained)
		{
			p.style = withProjectile(p.style, p.startTick, lastProjectiles.get(p.actor));
		}
		AttackStyle style = chooseStyle(info, p, unexplained, prayers);
		if (style == null)
		{
			return null;
		}
		boolean prayed = style.isNormal() && (p.protectedAtStart.contains(style) || prayers.contains(style));
		String name = state != null && state.name != null ? state.name
			: p.actor.getName() != null ? p.actor.getName() : info.getName();
		if (HunllefRules.isHunllef(npcId))
		{
			// Unexplained hits in the Hunllef fight are its tornadoes and floor tiles.
			return HunllefRules.attack(npcId, name, unexplained ? AttackStyle.SPECIAL : style, prayed, s.hunllefArmour);
		}
		int specialMax = info.max(AttackStyle.SPECIAL);
		if (specialMax > 0 && DragonfireRules.applies(info.getSpecialName()))
		{
			specialMax = DragonfireRules.max(specialMax, KBD.equals(info.getName()), dragonfireProtection(s));
		}
		if (specialMax > 0 && p.areaTarget != null && s.local != null && p.areaTarget.distanceTo2D(s.local) > 0)
		{
			// Next to where an area special lands: half its damage (Vorkath's bomb).
			specialMax = (specialMax + 1) / ADJACENT_DIVISOR;
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

	private Attack playerAttack(Pending p, TickState s)
	{
		TickState.PlayerEstimate estimate = s.players.get(p.actor);
		if (estimate == null)
		{
			return null;
		}
		AttackStyle style = p.style != null && p.style.isNormal() ? p.style : estimate.weaponStyle;
		Integer max = estimate.max.get(style);
		return Attack.builder()
			.source(estimate.name != null ? estimate.name : "A player")
			.style(style)
			.max(max != null ? max : 0)
			.prayed(p.protectedAtStart.contains(style) || s.prayers.contains(style))
			.player(true)
			.build();
	}

	private static DragonfireRules.Protection dragonfireProtection(TickState s)
	{
		DragonfireRules.Protection p = new DragonfireRules.Protection();
		p.shield = s.antiDragonShield;
		p.prayer = s.prayers.contains(AttackStyle.MAGIC);
		p.antifire = s.antifire;
		p.superAntifire = s.superAntifire;
		return p;
	}

	/**
	 * Style of an NPC attack matched by its animation. A projectile the NPC fired with that attack
	 * wins for ranged and magic: animation names are labels, and some bosses (the Hunllef) play one
	 * animation for both styles, while each style has its own projectile.
	 */
	static AttackStyle withProjectile(AttackStyle animation, int animationTick, SeenProjectile projectile)
	{
		if (projectile == null || animation == AttackStyle.MELEE || animation == AttackStyle.SPECIAL)
		{
			return animation;
		}
		int delay = projectile.tick - animationTick;
		return delay >= 0 && delay <= NPC_ANIMATION_WINDOW ? projectile.style : animation;
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

	static boolean inArea(WorldPoint target, WorldPoint at)
	{
		return at != null && target.getPlane() == at.getPlane() && target.distanceTo2D(at) <= AREA_RADIUS;
	}

	private static Set<AttackStyle> prayersOrNone(Set<AttackStyle> prayers)
	{
		return prayers == null || prayers.isEmpty() ? EnumSet.noneOf(AttackStyle.class) : prayers;
	}
}
