package com.crejaud.chanced;

import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;

/** One NPC id's max hit per attack style, merged from every wiki infobox that lists the id. */
@Getter
final class MonsterInfo
{
	private final String name;
	private final Map<AttackStyle, Integer> maxHits = new EnumMap<>(AttackStyle.class);
	/** Wiki label of the biggest special, e.g. "Rockfall". */
	private String specialName = AttackStyle.SPECIAL.getDisplayName();

	MonsterInfo(String name)
	{
		this.name = name;
	}

	/** Records a max hit, keeping the higher value when the style is already known. */
	void put(AttackStyle style, int max)
	{
		maxHits.merge(style, max, Math::max);
	}

	/** Records a special max hit with its wiki label. */
	void putSpecial(int max, String label)
	{
		if (max > max(AttackStyle.SPECIAL) && label != null && !label.isEmpty())
		{
			specialName = label;
		}
		put(AttackStyle.SPECIAL, max);
	}

	void merge(MonsterInfo other)
	{
		if (other.has(AttackStyle.SPECIAL))
		{
			putSpecial(other.max(AttackStyle.SPECIAL), other.specialName);
		}
		other.maxHits.forEach((style, max) ->
		{
			if (style.isNormal())
			{
				put(style, max);
			}
		});
	}

	/** Max hit for a style, or -1 when the wiki lists none. */
	int max(AttackStyle style)
	{
		return maxHits.getOrDefault(style, -1);
	}

	boolean has(AttackStyle style)
	{
		return maxHits.containsKey(style);
	}

	boolean hasNormalStyle()
	{
		for (AttackStyle style : maxHits.keySet())
		{
			if (style.isNormal())
			{
				return true;
			}
		}
		return false;
	}
}
