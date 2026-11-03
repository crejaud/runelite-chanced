package com.crejaud.chanced;

import lombok.Value;

/** A tick on which the player could have died and did not. */
@Value
class Verdict
{
	String source;
	/** Style of the biggest threat, e.g. "Magic". */
	String style;
	/** Total of what every hit this tick could have done; -1 when unknown. */
	int maxHit;
	int damage;
	int hpBefore;
	int hpAfter;
	/** Max hit is a worst-case estimate of a player's gear. */
	boolean estimate;
}
