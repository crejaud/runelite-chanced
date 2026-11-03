package com.crejaud.chanced;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses rows of the OSRS Wiki {@code infobox_monster} bucket into max hits per NPC id.
 *
 * <p>The wiki's {@code max_hit} field is free text: "13 (Melee)", "7x2 (Magic)", "7+6",
 * "50/60 (Melee)", "30-37 (Melee)", several entries split by {@code <br>}. An unlabelled
 * number applies to every style in {@code attack_style}. A label that names no normal style
 * (Rockfall, Dragonfire, special attack) is a special: typeless, not blocked by prayer.
 */
final class WikiMonsterParser
{
	private static final Pattern SPLIT = Pattern.compile("<br\\s*/?>|\\n|\\*");
	private static final Pattern TAGS = Pattern.compile("<[^>]*>|\u007f'\"`UNIQ--[^`]*`\"'\u007f|&[a-z]+;");
	/** Leading number, with an optional xN multiplier or +N / /N / -N follow-ups. */
	private static final Pattern NUMBER = Pattern.compile(
		"^~?(\\d+)\\s*(?:[x×]\\s*(\\d+)|((?:\\s*[+/-]\\s*\\d+)+))?\\+?\\s*(.*)$");
	private static final Pattern FOLLOW = Pattern.compile("([+/-])\\s*(\\d+)");
	private static final Pattern LABEL = Pattern.compile("\\(([^)]*)\\)?");

	private WikiMonsterParser()
	{
	}

	/** Parses a bucket API response body ({@code {"bucket": [...]}}). */
	static Map<Integer, MonsterInfo> parseResponse(JsonObject response)
	{
		Map<Integer, MonsterInfo> out = new HashMap<>();
		JsonArray rows = response.getAsJsonArray("bucket");
		if (rows != null)
		{
			parseRows(rows, out);
		}
		return out;
	}

	static void parseRows(JsonArray rows, Map<Integer, MonsterInfo> out)
	{
		for (JsonElement el : rows)
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject row = el.getAsJsonObject();
			String name = row.has("name") ? row.get("name").getAsString() : "Unknown";
			MonsterInfo info = parse(name, strings(row, "max_hit"), strings(row, "attack_style"));
			if (info.getMaxHits().isEmpty())
			{
				continue;
			}
			for (String id : strings(row, "id"))
			{
				try
				{
					int npcId = Integer.parseInt(id.trim());
					out.computeIfAbsent(npcId, k -> new MonsterInfo(name)).merge(info);
				}
				catch (NumberFormatException ignored)
				{
					// some rows list non-numeric ids; skip them
				}
			}
		}
	}

	static MonsterInfo parse(String name, List<String> maxHits, List<String> attackStyles)
	{
		Set<AttackStyle> defaults = EnumSet.noneOf(AttackStyle.class);
		for (String style : attackStyles)
		{
			defaults.addAll(stylesOf(style, false));
		}
		if (defaults.isEmpty())
		{
			defaults = EnumSet.of(AttackStyle.MELEE, AttackStyle.RANGED, AttackStyle.MAGIC);
		}

		MonsterInfo info = new MonsterInfo(name);
		for (String field : maxHits)
		{
			for (String part : SPLIT.split(field))
			{
				parsePart(TAGS.matcher(part).replaceAll(" ").trim(), defaults, info);
			}
		}
		return info;
	}

	private static void parsePart(String part, Set<AttackStyle> defaults, MonsterInfo info)
	{
		Matcher m = NUMBER.matcher(part);
		if (!m.matches())
		{
			return;
		}
		int value = Integer.parseInt(m.group(1));
		if (m.group(2) != null)
		{
			value *= Integer.parseInt(m.group(2));
		}
		else if (m.group(3) != null)
		{
			Matcher f = FOLLOW.matcher(m.group(3));
			while (f.find())
			{
				int n = Integer.parseInt(f.group(2));
				value = "+".equals(f.group(1)) ? value + n : Math.max(value, n);
			}
		}
		if (value <= 0)
		{
			return;
		}

		String rest = m.group(4).trim();
		Matcher l = LABEL.matcher(rest);
		String rawLabel = (l.find() ? l.group(1) : rest).trim();
		String label = rawLabel.toLowerCase(Locale.ROOT);
		if (label.contains("does not attack"))
		{
			return;
		}

		Set<AttackStyle> styles;
		if (isDefaultLabel(label))
		{
			styles = defaults;
		}
		else
		{
			styles = stylesOf(label, true);
		}
		for (AttackStyle style : styles)
		{
			if (style == AttackStyle.SPECIAL)
			{
				info.putSpecial(value, specialLabel(rawLabel));
			}
			else
			{
				info.put(style, value);
			}
		}
	}

	/** "special attack" and friends become "Special"; named specials keep their name. */
	private static String specialLabel(String raw)
	{
		String lower = raw.toLowerCase(Locale.ROOT);
		if (raw.isEmpty() || lower.startsWith("spec") || lower.startsWith("typeless"))
		{
			return AttackStyle.SPECIAL.getDisplayName();
		}
		return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
	}

	private static boolean isDefaultLabel(String label)
	{
		return label.isEmpty() || label.equals("normal") || label.equals("standard") || label.equals("default")
			|| label.startsWith("auto") || label.equals("regular hit") || label.equals("approx")
			|| label.startsWith("x") || label.equals("total") || label.equals("unconfirmed");
	}

	/**
	 * Styles named by an attack_style value or a max_hit label. With {@code specialFallback},
	 * a label naming no normal style is a special; otherwise it names nothing.
	 */
	static Set<AttackStyle> stylesOf(String text, boolean specialFallback)
	{
		String s = text.toLowerCase(Locale.ROOT);
		Set<AttackStyle> out = EnumSet.noneOf(AttackStyle.class);
		boolean typeless = s.contains("typeless");
		if (!typeless && !s.contains("special") && !s.contains("spec "))
		{
			if (s.contains("melee") || s.contains("stab") || s.contains("slash") || s.contains("crush"))
			{
				out.add(AttackStyle.MELEE);
			}
			if (s.contains("ranged") || s.contains("range") || s.contains("missile"))
			{
				out.add(AttackStyle.RANGED);
			}
			if (s.contains("magic") || s.contains("magical"))
			{
				out.add(AttackStyle.MAGIC);
			}
			// "Magical melee"/"Magical ranged" are blocked by Protect from Magic
			if (s.contains("magical"))
			{
				out.remove(AttackStyle.MELEE);
				out.remove(AttackStyle.RANGED);
			}
		}
		if (out.isEmpty() && (specialFallback || typeless || s.contains("dragonfire")))
		{
			out.add(AttackStyle.SPECIAL);
		}
		return out;
	}

	private static List<String> strings(JsonObject row, String key)
	{
		List<String> out = new ArrayList<>();
		JsonElement el = row.get(key);
		if (el == null || el.isJsonNull())
		{
			return out;
		}
		if (el.isJsonArray())
		{
			for (JsonElement e : el.getAsJsonArray())
			{
				if (!e.isJsonNull())
				{
					out.add(e.getAsString());
				}
			}
		}
		else
		{
			out.add(el.getAsString());
		}
		return out;
	}
}
