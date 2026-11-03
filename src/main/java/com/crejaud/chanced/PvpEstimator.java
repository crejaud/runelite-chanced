package com.crejaud.chanced;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Worst-case max hit of another player from their visible gear, assuming 99s, the best
 * boosting potion and prayer, and an assumed ring and ammo (neither is visible).
 */
@Singleton
class PvpEstimator
{
	/** 99 + super combat / ranging potion boost. */
	private static final int BOOSTED_STRENGTH = 118;
	private static final int BOOSTED_RANGED = 112;
	/** Piety / Rigour multiplier, as a percent. */
	private static final int PRAYER_BONUS = 123;
	/** Ice Barrage, the biggest common PvP spell base. */
	private static final int MAGIC_BASE = 30;
	private static final int PERCENT = 100;
	/** Max hit formula: +8 to the effective level, +3 more for the aggressive stance. */
	private static final int EFFECTIVE_LEVEL_BONUS = 8;
	private static final int AGGRESSIVE_STANCE = 3;
	private static final int STRENGTH_OFFSET = 64;
	private static final int MAX_HIT_DIVISOR = 640;
	/** Adds half the divisor so the division rounds to nearest. */
	private static final int ROUND_HALF = MAX_HIT_DIVISOR / 2;
	/** Rings and ammo are not visible on other players: assume a berserker ring and dragon bolts. */
	static final int RING_STRENGTH = 8;
	static final int AMMO_STRENGTH = 122;
	/** Room for special attacks above the normal max. */
	static final int SPEC_MARGIN_PERCENT = 25;

	private final ItemManager itemManager;

	@Inject
	PvpEstimator(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/** Totals of a player's visible equipment. */
	static final class Gear
	{
		int str;
		int rstr;
		float mdmg;
		int weaponMelee;
		int weaponRanged;
		int weaponMagic;
		int weaponRstr;
	}

	Gear gear(Player player)
	{
		Gear gear = new Gear();
		PlayerComposition comp = player.getPlayerComposition();
		if (comp == null)
		{
			return gear;
		}
		for (KitType slot : KitType.values())
		{
			int itemId = comp.getEquipmentId(slot);
			if (itemId <= 0)
			{
				continue;
			}
			ItemStats stats = itemManager.getItemStats(itemId);
			if (stats == null || stats.getEquipment() == null)
			{
				continue;
			}
			ItemEquipmentStats e = stats.getEquipment();
			gear.str += e.getStr();
			gear.rstr += e.getRstr();
			gear.mdmg += e.getMdmg();
			if (slot == KitType.WEAPON)
			{
				gear.weaponMelee = Math.max(e.getAstab(), Math.max(e.getAslash(), e.getAcrush()));
				gear.weaponRanged = e.getArange();
				gear.weaponMagic = e.getAmagic();
				gear.weaponRstr = e.getRstr();
			}
		}
		return gear;
	}

	/** Style the weapon is built for. */
	static AttackStyle weaponStyle(Gear gear)
	{
		if (gear.weaponMagic > gear.weaponMelee && gear.weaponMagic > gear.weaponRanged)
		{
			return AttackStyle.MAGIC;
		}
		if (gear.weaponRanged > gear.weaponMelee || gear.weaponRstr > 0)
		{
			return AttackStyle.RANGED;
		}
		return AttackStyle.MELEE;
	}

	/** Estimated max hit for a style, with a margin for special attacks. */
	static int maxHit(Gear gear, AttackStyle style)
	{
		return maxHit(gear, style, RING_STRENGTH, AMMO_STRENGTH, SPEC_MARGIN_PERCENT);
	}

	static int maxHit(Gear gear, AttackStyle style, int ringStr, int ammoRstr, int specMarginPercent)
	{
		int max;
		switch (style)
		{
			case RANGED:
			{
				int effective = BOOSTED_RANGED * PRAYER_BONUS / PERCENT + EFFECTIVE_LEVEL_BONUS;
				// Thrown weapons and blowpipes carry their own ranged strength.
				int rstr = gear.rstr + (gear.weaponRstr > 0 ? 0 : ammoRstr);
				max = maxFromEffective(effective, rstr);
				break;
			}
			case MAGIC:
				max = (int) (MAGIC_BASE * (1 + gear.mdmg / PERCENT));
				break;
			default:
			{
				int effective = BOOSTED_STRENGTH * PRAYER_BONUS / PERCENT + AGGRESSIVE_STANCE + EFFECTIVE_LEVEL_BONUS;
				max = maxFromEffective(effective, gear.str + ringStr);
				break;
			}
		}
		return max * (PERCENT + specMarginPercent) / PERCENT;
	}

	/** The game's max hit formula: effective level times (strength bonus + 64) over 640, rounded. */
	private static int maxFromEffective(int effectiveLevel, int strengthBonus)
	{
		return (effectiveLevel * (strengthBonus + STRENGTH_OFFSET) + ROUND_HALF) / MAX_HIT_DIVISOR;
	}
}
