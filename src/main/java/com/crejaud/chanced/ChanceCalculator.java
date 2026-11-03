package com.crejaud.chanced;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a tick's hits could have killed the player. HP at the hit is HP after the
 * tick plus the damage taken, so food eaten on the tick counts.
 */
final class ChanceCalculator
{
	static final int PVP_PRAYER_PERCENT = 60;

	private ChanceCalculator()
	{
	}

	/**
	 * @param hits                every damage hitsplat on the player this tick
	 * @param hpAfter             hitpoints after the tick
	 * @param unknownHpThreshold  alert on unexplained hits at or below this HP; 0 turns it off
	 * @return the verdict, or null when the tick was not a chance
	 */
	static Verdict evaluate(List<Hit> hits, int hpAfter, int unknownHpThreshold)
	{
		if (hits.isEmpty() || hpAfter <= 0)
		{
			return null;
		}

		int damage = 0;
		int potential = 0;
		int unexplained = 0;
		Map<Attack, Integer> byAttack = new IdentityHashMap<>();
		for (Hit hit : hits)
		{
			damage += hit.getDamage();
			if (hit.getAttack() != null)
			{
				byAttack.merge(hit.getAttack(), hit.getDamage(), Integer::sum);
			}
			else
			{
				potential += hit.getDamage();
				if (!hit.isOverTime())
				{
					unexplained++;
				}
			}
		}
		int hpBefore = hpAfter + damage;

		Attack worst = null;
		int worstMax = -1;
		String worstStyle = null;
		boolean estimate = false;
		for (Map.Entry<Attack, Integer> e : byAttack.entrySet())
		{
			Attack attack = e.getKey();
			int dealt = e.getValue();
			int max = effectiveMax(attack, dealt);
			potential += max;
			estimate |= attack.isPlayer();
			if (max > worstMax)
			{
				worst = attack;
				worstMax = max;
				worstStyle = styleShown(attack, dealt);
			}
		}

		if (worst != null)
		{
			return potential >= hpBefore
				? new Verdict(worst.getSource(), worstStyle, potential, damage, hpBefore, hpAfter, estimate)
				: null;
		}
		if (unexplained > 0 && unknownHpThreshold > 0 && hpBefore <= unknownHpThreshold)
		{
			return new Verdict("Unknown", "Unknown", -1, damage, hpBefore, hpAfter, false);
		}
		return null;
	}

	/** What one attack could have hit, given it actually dealt {@code dealt}. */
	static int effectiveMax(Attack attack, int dealt)
	{
		int max = attack.getMax();
		if (attack.isPlayer())
		{
			if (attack.isPrayed())
			{
				max = max * PVP_PRAYER_PERCENT / 100;
			}
		}
		else if (attack.getStyle().isNormal() && attack.isPrayed())
		{
			max = max * attack.getPassThroughPercent() / 100;
			if (dealt > max)
			{
				// It went through the prayer: a special (prayer smash), or the prayer came too late.
				max = attack.getSpecialMax() > 0 ? attack.getSpecialMax() : attack.getMax();
			}
		}
		return Math.max(max, dealt);
	}

	private static String styleShown(Attack attack, int dealt)
	{
		if (!attack.isPlayer() && attack.getStyle().isNormal() && attack.isPrayed() && attack.getSpecialMax() > 0
			&& dealt > attack.getMax() * attack.getPassThroughPercent() / 100)
		{
			return attack.getSpecialName();
		}
		if (attack.getStyle() == AttackStyle.SPECIAL)
		{
			return attack.getSpecialName();
		}
		return attack.getStyle().getDisplayName();
	}
}
