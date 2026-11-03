package com.crejaud.chanced;

import lombok.Value;

/** A damage hitsplat on the local player, and the attack it is attributed to (null if none). */
@Value
class Hit
{
	int damage;
	/** Poison, venom, burn and other damage over time: counts at the damage dealt. */
	boolean overTime;
	Attack attack;
}
