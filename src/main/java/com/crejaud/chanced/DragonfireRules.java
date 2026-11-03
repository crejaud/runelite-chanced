package com.crejaud.chanced;

import com.google.common.collect.ImmutableSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Dragonfire max hits under the player's protection, from the OSRS Wiki's dragonfire damage
 * table. The wiki's infobox max is unprotected; an anti-dragon shield, Protect from Magic and
 * (super) antifire each cut it, and most combinations of two make regular dragonfire harmless.
 */
final class DragonfireRules
{
	static final Set<Integer> SHIELDS = ImmutableSet.of(
		ItemID.ANTIDRAGONBREATHSHIELD, ItemID.NZONE_ANTIDRAGONSHIELD,
		ItemID.DRAGONFIRE_SHIELD, ItemID.DRAGONFIRE_SHIELD_UNCHARGED,
		ItemID.DRAGONFIRE_WARD, ItemID.DRAGONFIRE_WARD_UNCHARGED,
		ItemID.WYVERN_SHIELD, ItemID.WYVERN_SHIELD_UNCHARGED);

	/** Protection the player has up. */
	static final class Protection
	{
		boolean shield;
		boolean prayer;
		boolean antifire;
		boolean superAntifire;
	}

	private DragonfireRules()
	{
	}

	/** A special the rules apply to: regular dragonfire, not Vorkath's fireball. */
	static boolean applies(String specialName)
	{
		String s = specialName.toLowerCase(Locale.ROOT);
		return s.contains("dragonfire") && !s.contains("bomb");
	}

	/**
	 * Max dragonfire hit under protection.
	 *
	 * @param unprotected the wiki's (unprotected) max
	 * @param kbd         the King Black Dragon, which the wiki tables separately
	 */
	static int max(int unprotected, boolean kbd, Protection p)
	{
		// Numerators over the unprotected max: chromatic 50 (also brutal, lava, frost,
		// reanimated) and King Black Dragon 65.
		int base = kbd ? 65 : 50;
		int left;
		boolean anyAntifire = p.antifire || p.superAntifire;
		if (p.superAntifire && !kbd)
		{
			left = 0;
		}
		else if (p.shield && anyAntifire)
		{
			left = 0;
		}
		else if (p.shield)
		{
			left = kbd ? 15 : 5;
		}
		else if (p.prayer && anyAntifire)
		{
			left = kbd ? (p.superAntifire ? 0 : 5) : 0;
		}
		else if (p.prayer)
		{
			left = kbd ? 20 : 10;
		}
		else if (p.superAntifire)
		{
			left = 0;
		}
		else if (p.antifire)
		{
			left = kbd ? 50 : 35;
		}
		else
		{
			return unprotected;
		}
		return (unprotected * left + base - 1) / base;
	}
}
