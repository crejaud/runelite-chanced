package com.crejaud.chanced;

import lombok.Value;

/** A chance, with everything the chat message and Discord embed show. */
@Value
class ChancedEvent
{
	String username;
	String source;
	String style;
	/** Protection prayer that was up, e.g. "Protect from Melee", or "None". */
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
		return format(template, "");
	}

	/** Fills the placeholders, wrapping the name, source, max hit and damage in {@code emphasis}. */
	String format(String template, String emphasis)
	{
		return template
			.replace("%USERNAME%", emphasis + username + emphasis)
			.replace("%SOURCE%", emphasis + source + emphasis)
			.replace("%MAXHIT%", emphasis + maxHitText() + emphasis)
			.replace("%DAMAGE%", emphasis + damage + emphasis)
			.replace("%HPLEFT%", String.valueOf(hpAfter))
			.replace("%HP%", String.valueOf(hpBefore))
			.replace("%STYLE%", style)
			.replace("%PRAYER%", prayer)
			.replace("%WORLD%", String.valueOf(world));
	}
}
