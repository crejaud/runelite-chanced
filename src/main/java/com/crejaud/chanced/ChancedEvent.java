package com.crejaud.chanced;

import lombok.Value;

/** A chance, with everything the chat message and Discord embed show. */
@Value
class ChancedEvent
{
	String username;
	String source;
	String style;
	/** Protection prayer that was up, or "None". */
	String prayer;
	int maxHit;
	int damage;
	int hpBefore;
	int hpAfter;
	int world;
	boolean estimate;

	static ChancedEvent of(Verdict v, String username, String prayer, int world)
	{
		return new ChancedEvent(username, v.getSource(), v.getStyle(), prayer, v.getMaxHit(), v.getDamage(),
			v.getHpBefore(), v.getHpAfter(), world, v.isEstimate());
	}

	/** Max hit as shown: "?" when unknown, "~" in front of a player estimate. */
	String maxHitText()
	{
		if (maxHit < 0)
		{
			return "?";
		}
		return (estimate ? "~" : "") + maxHit;
	}

	String format(String template)
	{
		return template
			.replace("%USERNAME%", username)
			.replace("%SOURCE%", source)
			.replace("%MAXHIT%", maxHitText())
			.replace("%DAMAGE%", String.valueOf(damage))
			.replace("%HP%", String.valueOf(hpBefore))
			.replace("%STYLE%", style)
			.replace("%WORLD%", String.valueOf(world));
	}

	/** "Max hit 8 (Magic), actually hit 0, at 8 HP." */
	String summary()
	{
		return "Max hit " + maxHitText() + " (" + style + "), actually hit " + damage + ", at " + hpBefore + " HP."
			+ (estimate ? " (PvP estimate)" : "");
	}
}
