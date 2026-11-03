package com.crejaud.chanced;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/** The advanced config text fields, parsed. Bad lines are skipped. */
@Getter
final class Overrides
{
	/** (npcId, animationId) to style. */
	private final Map<Long, AttackStyle> styles = new HashMap<>();
	/** (npcId, style) to pass-through percent. */
	private final Map<Long, Integer> passThrough = new HashMap<>();
	private final Map<Integer, Integer> graphicHazards = new HashMap<>();
	private final Map<Integer, Integer> projectileHazards = new HashMap<>();
	private final Map<Integer, Integer> regionHazards = new HashMap<>();

	static Overrides parse(String styleOverrides, String prayerPassThrough, String hazards)
	{
		Overrides o = new Overrides();
		for (String[] f : lines(styleOverrides))
		{
			AttackStyle style = style(f[2]);
			Integer npc = number(f[0]);
			Integer anim = number(f[1]);
			if (style != null && npc != null && anim != null)
			{
				o.styles.put(key(npc, anim), style);
			}
		}
		for (String[] f : lines(prayerPassThrough))
		{
			AttackStyle style = style(f[1]);
			Integer npc = number(f[0]);
			Integer percent = number(f[2]);
			if (style != null && npc != null && percent != null)
			{
				o.passThrough.put(key(npc, style.ordinal()), Math.max(0, Math.min(100, percent)));
			}
		}
		for (String[] f : lines(hazards))
		{
			Integer id = number(f[1]);
			Integer max = number(f[2]);
			if (id == null || max == null)
			{
				continue;
			}
			switch (f[0].toLowerCase(Locale.ROOT))
			{
				case "graphic":
					o.graphicHazards.put(id, max);
					break;
				case "projectile":
					o.projectileHazards.put(id, max);
					break;
				case "region":
					o.regionHazards.put(id, max);
					break;
				default:
					break;
			}
		}
		return o;
	}

	AttackStyle style(int npcId, int animationId)
	{
		return styles.get(key(npcId, animationId));
	}

	int passThrough(int npcId, AttackStyle style)
	{
		return passThrough.getOrDefault(key(npcId, style.ordinal()), 0);
	}

	static long key(int a, int b)
	{
		return ((long) a << 32) | (b & 0xffffffffL);
	}

	private static Iterable<String[]> lines(String text)
	{
		java.util.List<String[]> out = new java.util.ArrayList<>();
		if (text == null)
		{
			return out;
		}
		for (String line : text.split("[\\r\\n,]+"))
		{
			String[] f = line.trim().split("\\s*:\\s*");
			if (f.length == 3)
			{
				out.add(f);
			}
		}
		return out;
	}

	private static Integer number(String s)
	{
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static AttackStyle style(String s)
	{
		switch (s.trim().toLowerCase(Locale.ROOT))
		{
			case "melee":
				return AttackStyle.MELEE;
			case "ranged":
			case "range":
				return AttackStyle.RANGED;
			case "magic":
			case "mage":
				return AttackStyle.MAGIC;
			case "special":
			case "typeless":
				return AttackStyle.SPECIAL;
			default:
				return null;
		}
	}
}
