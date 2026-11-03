package com.crejaud.chanced;

/** What an attack animation's name says about its style. UNKNOWN still marks an attack. */
enum AttackHint
{
	MELEE,
	RANGED,
	MAGIC,
	SPECIAL,
	UNKNOWN;

	AttackStyle toStyle()
	{
		switch (this)
		{
			case MELEE:
				return AttackStyle.MELEE;
			case RANGED:
				return AttackStyle.RANGED;
			case MAGIC:
				return AttackStyle.MAGIC;
			case SPECIAL:
				return AttackStyle.SPECIAL;
			default:
				return null;
		}
	}
}
