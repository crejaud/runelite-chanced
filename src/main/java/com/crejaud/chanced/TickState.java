package com.crejaud.chanced;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * What the matcher needs to know about the end of one game tick, read from the client or from a
 * debug report: the hits on the player and everything that decides what they could have been.
 */
final class TickState
{
	int tick;
	int cycle;
	/** Hitpoints after the tick. */
	int hpAfter;
	WorldPoint local;
	Set<AttackStyle> prayers = EnumSet.noneOf(AttackStyle.class);
	boolean pvpActive;
	boolean antiDragonShield;
	boolean antifire;
	boolean superAntifire;
	/** Nightmare Zone absorption points left. */
	int absorption;
	/** Crystal armour points for the Hunllef's tables. */
	int hunllefArmour;
	/** NPCs attacking the player, and every actor with an attack still pending. */
	List<ActorState> actors = new ArrayList<>();
	/** PvP max hit estimates for nearby players. */
	Map<ActorRef, PlayerEstimate> players = new HashMap<>();
	/** Damage hitsplats on the player, in the order they arrived. */
	List<RawHit> hits = new ArrayList<>();

	/** An actor as it stands at the end of the tick. */
	static final class ActorState
	{
		ActorRef ref;
		/** Current NPC id, or -1 for players. */
		int npcId;
		String name;
		boolean interactingMe;
		boolean dead;
		/** Tiles from the player. */
		int distance;
		/** In melee distance of the player. */
		boolean meleeRange;
	}

	/** A player's estimated max hit per style from their visible gear. */
	static final class PlayerEstimate
	{
		/** Display name for the alert; never written to a debug report. */
		String name;
		AttackStyle weaponStyle;
		Map<AttackStyle, Integer> max = new EnumMap<>(AttackStyle.class);
	}

	/** One damage hitsplat on the player. */
	static final class RawHit
	{
		final int amount;
		final int type;

		RawHit(int amount, int type)
		{
			this.amount = amount;
			this.type = type;
		}
	}

	ActorState actor(ActorRef ref)
	{
		for (ActorState a : actors)
		{
			if (a.ref.equals(ref))
			{
				return a;
			}
		}
		return null;
	}

	boolean interactingMe(ActorRef ref)
	{
		ActorState a = actor(ref);
		return a != null && a.interactingMe;
	}
}
