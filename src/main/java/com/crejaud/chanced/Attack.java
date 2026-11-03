package com.crejaud.chanced;

import lombok.Builder;
import lombok.Value;

/** One attack that landed on the local player this tick, with what it could have hit. */
@Value
@Builder
class Attack
{
	/** Display name of the attacker. */
	String source;
	AttackStyle style;
	/** Max hit for {@link #style}, before prayer. */
	int max;
	/** Whether the correct protection prayer was up for this attack. */
	boolean prayed;
	/** Max hit through the correct protection prayer (NPCs); 0 when the prayer blocks it fully. */
	@Builder.Default
	int prayedMax = 0;
	/** The attacker's typeless/special max, or -1 when it has none. */
	@Builder.Default
	int specialMax = -1;
	/** Wiki label of that special, e.g. "Prayer smash" or "Rockfall". */
	@Builder.Default
	String specialName = "Special";
	/** A player attacker: prayer reduces by 40% instead of blocking, and the max is an estimate. */
	boolean player;
}
