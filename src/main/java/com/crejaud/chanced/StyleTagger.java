package com.crejaud.chanced;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tags NPC attack animations and projectiles with the attack style their RuneLite gameval
 * name implies, e.g. {@code NPC_RAT_BOSS_ATTACK_MAGIC_01} is a magic attack. Reads the
 * {@code AnimationID.java} / {@code SpotanimID.java} source text as data.
 */
final class StyleTagger
{
	private static final Pattern CONSTANT = Pattern.compile("public static final int (\\w+) = (-?\\d+);");

	private static final Pattern ATTACK = Pattern.compile("ATTACK|_ATK");
	private static final Pattern PROJECTILE = Pattern.compile("_PROJ|TRAVEL");
	/** Player and object animations are not NPC attacks. */
	private static final Pattern SKIP = Pattern.compile("^(HUMAN|PLAYER|BR_|SKILL|EMOTE|QIP)");

	private static final Pattern ANIM_SPECIAL = Pattern.compile("SPECIAL|_SPEC_|_SPEC$|STOMP");
	private static final Pattern ANIM_MAGIC = Pattern.compile("MAGIC|MAGE|SPELL|_CAST");
	private static final Pattern ANIM_RANGED = Pattern.compile("RANGED|_RANGE|ARROW|THROW|SHOOT|_BOW");
	private static final Pattern ANIM_MELEE = Pattern.compile("MELEE|STAB|SLASH|CRUSH|PUNCH|BITE|CLAW|SWIPE");

	private static final Pattern PROJ_MAGIC = Pattern.compile(
		"MAGIC|MAGE|SPELL|(WIND|WATER|EARTH|FIRE)_(STRIKE|BOLT|BLAST|WAVE|SURGE)");
	private static final Pattern PROJ_RANGED = Pattern.compile(
		"RANGED|_RANGE|ARROW|BOLT|DART|KNIFE|JAVELIN|THROWN|SPEAR|CHINCHOMPA");

	private StyleTagger()
	{
	}

	/** Attack animations in {@code AnimationID.java} source, with their hint. */
	static void animations(String source, Map<Integer, AttackHint> out)
	{
		constants(source, (name, id) ->
		{
			if (!SKIP.matcher(name).find() && ATTACK.matcher(name).find())
			{
				out.putIfAbsent(id, animationHint(name));
			}
		});
	}

	/** Projectiles in {@code SpotanimID.java} source whose name says their style. */
	static void projectiles(String source, Map<Integer, AttackStyle> out)
	{
		constants(source, (name, id) ->
		{
			if (!PROJECTILE.matcher(name).find())
			{
				return;
			}
			AttackStyle style = projectileStyle(name);
			if (style != null)
			{
				out.putIfAbsent(id, style);
			}
		});
	}

	static AttackHint animationHint(String name)
	{
		if (ANIM_SPECIAL.matcher(name).find())
		{
			return AttackHint.SPECIAL;
		}
		if (ANIM_MAGIC.matcher(name).find())
		{
			return AttackHint.MAGIC;
		}
		if (ANIM_RANGED.matcher(name).find())
		{
			return AttackHint.RANGED;
		}
		if (ANIM_MELEE.matcher(name).find())
		{
			return AttackHint.MELEE;
		}
		return AttackHint.UNKNOWN;
	}

	static AttackStyle projectileStyle(String name)
	{
		if (PROJ_MAGIC.matcher(name).find())
		{
			return AttackStyle.MAGIC;
		}
		if (PROJ_RANGED.matcher(name).find())
		{
			return AttackStyle.RANGED;
		}
		return null;
	}

	private static void constants(String source, BiConsumer<String, Integer> each)
	{
		Matcher m = CONSTANT.matcher(source);
		while (m.find())
		{
			try
			{
				each.accept(m.group(1), Integer.parseInt(m.group(2)));
			}
			catch (NumberFormatException ignored)
			{
				// not an int constant
			}
		}
	}
}
