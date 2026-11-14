package com.crejaud.chanced;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Damage the player deals themselves, from the OSRS Wiki's list of self-damaging items. Each
 * source has a trigger the client sees (a dose drunk, a game message, a ruby bolt proc) and the
 * hits it causes: how many, for how much, and for how many ticks after the trigger. Ranges carry
 * a little slack, as the hitpoints read at the trigger may already include the hit.
 */
@Getter
@RequiredArgsConstructor
enum SelfDamage
{
	/** Divine potions: 10 on every dose. */
	DIVINE_POTION("divine potion", 1, 2),
	/** Zamorak brew and mix: 12% of current hitpoints, rounded down. */
	ZAMORAK_BREW("zamorak brew", 1, 2),
	/** Overloads (Nightmare Zone, Chambers of Xeric): 50 damage as five hits of 10. */
	OVERLOAD("overload", Overload.HITS, Overload.TICKS),
	/** Rock cakes: 1 to eat, 10% of current hitpoints plus 1 to guzzle. */
	ROCK_CAKE("rock cake", 1, 2),
	/** Nightmare Zone's locator orb: 10, never below 1 hitpoint. */
	LOCATOR_ORB("locator orb", 1, 2),
	/** Ruby bolts (e)'s Blood Forfeit: 10% of current hitpoints, landing with the bolt. */
	RUBY_BOLT("ruby bolt proc", 1, 1);

	/** The game message the rock cake sends when it hurts. */
	static final String ROCK_CAKE_MESSAGE = "Ow! I nearly broke a tooth!";
	/** The game message the locator orb sends. */
	static final String LOCATOR_ORB_MESSAGE = "The locator orb is unstable and hurts you as you use it.";

	private static final int FLAT_DAMAGE = 10;
	private static final int PERCENT = 100;
	private static final int TEN_PERCENT = 10;
	private static final int BREW_PERCENT = 12;
	/** Rounding and the HP read a tick late: a few hitpoints either way. */
	private static final int SLACK = 2;

	/** An overload's hits, apart so the enum constant can name them. */
	private static final class Overload
	{
		static final int HITS = 5;
		/** Five hits, about one every other tick, with room to spare. */
		static final int TICKS = 15;
	}

	private final String label;
	/** Hits one trigger causes. */
	private final int hits;
	/** Ticks after the trigger the hits can land in. */
	private final int ticks;

	/** A self-damage source a drunk or eaten item's name names, or null. */
	static SelfDamage fromItem(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.toLowerCase(Locale.ROOT);
		if (n.startsWith("divine ") && n.contains("potion("))
		{
			return DIVINE_POTION;
		}
		if (n.startsWith("zamorak brew(") || n.startsWith("zamorak mix("))
		{
			return ZAMORAK_BREW;
		}
		if (n.startsWith("overload"))
		{
			return OVERLOAD;
		}
		return null;
	}

	/** A self-damage source a game message announces, or null. */
	static SelfDamage fromMessage(String message)
	{
		if (message == null)
		{
			return null;
		}
		if (message.contains(ROCK_CAKE_MESSAGE))
		{
			return ROCK_CAKE;
		}
		if (message.contains(LOCATOR_ORB_MESSAGE))
		{
			return LOCATOR_ORB;
		}
		return null;
	}

	/** Smallest hit this source deals at {@code hp} hitpoints. */
	int min(int hp)
	{
		switch (this)
		{
			case ROCK_CAKE:
			case RUBY_BOLT:
				return 0;
			default:
				return 1;
		}
	}

	/** Largest hit this source deals at {@code hp} hitpoints. */
	int max(int hp)
	{
		switch (this)
		{
			case ZAMORAK_BREW:
				return hp * BREW_PERCENT / PERCENT + SLACK;
			case ROCK_CAKE:
			case RUBY_BOLT:
				return hp / TEN_PERCENT + SLACK;
			default:
				return FLAT_DAMAGE;
		}
	}
}
