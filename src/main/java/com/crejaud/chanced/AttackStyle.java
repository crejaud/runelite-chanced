package com.crejaud.chanced;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Prayer;

/** How a hit is dealt. SPECIAL covers typeless and wiki-labelled special attacks. */
@Getter
@RequiredArgsConstructor
enum AttackStyle
{
	MELEE("Melee", Prayer.PROTECT_FROM_MELEE),
	RANGED("Ranged", Prayer.PROTECT_FROM_MISSILES),
	MAGIC("Magic", Prayer.PROTECT_FROM_MAGIC),
	SPECIAL("Special", null);

	private final String displayName;
	/** Protection prayer that blocks this style, or null when none does. */
	private final Prayer protection;

	boolean isNormal()
	{
		return this != SPECIAL;
	}
}
