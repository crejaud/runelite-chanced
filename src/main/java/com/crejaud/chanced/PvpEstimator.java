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
				int effective = BOOSTED_RANGED * PRAYER_BONUS / 100 + 8;
				// Thrown weapons and blowpipes carry their own ranged strength.
				int rstr = gear.rstr + (gear.weaponRstr > 0 ? 0 : ammoRstr);
				max = (effective * (rstr + 64) + 320) / 640;
				break;
			}
			case MAGIC:
				max = (int) (MAGIC_BASE * (1 + gear.mdmg / 100f));
				break;
			default:
			{
				int effective = BOOSTED_STRENGTH * PRAYER_BONUS / 100 + 3 + 8;
				max = (effective * (gear.str + ringStr + 64) + 320) / 640;
				break;
			}
		}
		return max * (100 + specMarginPercent) / 100;
	}
}
