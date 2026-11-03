package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class WikiMonsterParserTest
{
	private static final int SCURRIUS_MELEE = 13;
	private static final int SCURRIUS_MAGIC = 8;
	private static final int SCURRIUS_RANGED = 7;
	private static final int SCURRIUS_ROCKFALL = 22;
	private static final int SCURRIUS_GROUP_MAGIC = 14;
	private static final int SCURRIUS_GROUP_RANGED = 13;
	private static final int SCURRIUS_GROUP_ROCKFALL = 24;
	private static final int SCURRIUS_ALT_MELEE = 9;
	private static final int SCURRIUS_SOLO_ID = 7222;
	private static final int SCURRIUS_GROUP_ID = 7221;
	private static final int GOBLIN_ID = 3028;
	private static final int GOBLIN_ALT_ID = 3029;
	private static final int GOBLIN_MAX = 1;
	private static final int NON_ATTACKER_ID = 9999;

	private static final int SLASHER_MAX = 36;
	private static final int KRIL_MAGIC = 30;
	private static final int KRIL_MELEE = 46;
	private static final int KRIL_SPECIAL = 49;

	private static final int MULTI_HIT = 7;
	private static final int MULTI_COUNT = 2;
	private static final int SUM_SECOND = 6;
	private static final int CHOICE_LOW = 50;
	private static final int CHOICE_HIGH = 60;
	private static final int RANGE_LOW = 30;
	private static final int RANGE_HIGH = 37;
	private static final int DRAGONFIRE_MIN = 50;

	private static final int AUTO_MAX = 31;
	private static final int LIST_SPECIAL = 45;
	private static final int BASE_MELEE = 22;
	private static final int WHIRLWINDS = 64;
	private static final int KNOCKBACK = 30;
	private static final int BOTH_STYLES = 33;
	private static final int MAGICAL_MELEE = 20;

	private static final int SPECTRE_MAGIC = 14;
	private static final int SPECTRE_UNPROTECTED = 44;
	private static final int SHIELDED = 60;
	private static final int BOOTLESS = 20;

	private static MonsterInfo parse(String attackStyle, String... maxHits)
	{
		return WikiMonsterParser.parse("Test", Arrays.asList(maxHits),
			attackStyle == null ? Collections.emptyList() : Collections.singletonList(attackStyle));
	}

	private static String row(int id, String name, String attackStyle, String... maxHits)
	{
		StringBuilder hits = new StringBuilder();
		for (String h : maxHits)
		{
			hits.append(hits.length() == 0 ? "" : ",").append('"').append(h).append('"');
		}
		return "{\"id\":[\"" + id + "\"],\"max_hit\":[" + hits + "],\"name\":\"" + name
			+ "\",\"attack_style\":[\"" + attackStyle + "\"]}";
	}

	@Test
	public void labelledStyles()
	{
		// GIVEN Scurrius's max hits, one per style plus Rockfall
		String[] maxHits = {SCURRIUS_MELEE + " (Melee)", SCURRIUS_MAGIC + " (Magic)", SCURRIUS_RANGED + " (Ranged)",
			SCURRIUS_ROCKFALL + " (Rockfall)"};

		// WHEN parsing them
		MonsterInfo info = parse("Magic", maxHits);

		// THEN each style has its max and Rockfall is the special
		assertEquals(SCURRIUS_MELEE, info.max(AttackStyle.MELEE));
		assertEquals(SCURRIUS_MAGIC, info.max(AttackStyle.MAGIC));
		assertEquals(SCURRIUS_RANGED, info.max(AttackStyle.RANGED));
		assertEquals(SCURRIUS_ROCKFALL, info.max(AttackStyle.SPECIAL));
		assertEquals("Rockfall", info.getSpecialName());
	}

	@Test
	public void unlabelledUsesAttackStyle()
	{
		// GIVEN an unlabelled max hit on a slash attacker
		String maxHit = String.valueOf(SLASHER_MAX);

		// WHEN parsing it
		MonsterInfo info = parse("Slash", maxHit);

		// THEN it is a melee max only
		assertEquals(SLASHER_MAX, info.max(AttackStyle.MELEE));
		assertFalse(info.has(AttackStyle.MAGIC));
	}

	@Test
	public void specialAttackLabel()
	{
		// GIVEN K'ril's max hits, including "special attack"
		String[] maxHits = {KRIL_MAGIC + " (magic)", KRIL_MELEE + " (melee)", KRIL_SPECIAL + " (special attack)"};

		// WHEN parsing them
		MonsterInfo info = parse("Slash", maxHits);

		// THEN the special is named plainly "Special"
		assertEquals(KRIL_MELEE, info.max(AttackStyle.MELEE));
		assertEquals(KRIL_MAGIC, info.max(AttackStyle.MAGIC));
		assertEquals(KRIL_SPECIAL, info.max(AttackStyle.SPECIAL));
		assertEquals("Special", info.getSpecialName());
	}

	@Test
	public void multipliersSumsAndRanges()
	{
		// GIVEN max hits written as a multiplier, a sum, a choice, a range and a minimum
		// WHEN parsing each
		int multiplier = parse(null, MULTI_HIT + "x" + MULTI_COUNT + " (Magic)").max(AttackStyle.MAGIC);
		int sum = parse("Crush", MULTI_HIT + "+" + SUM_SECOND).max(AttackStyle.MELEE);
		int choice = parse(null, CHOICE_LOW + "/" + CHOICE_HIGH + " (Melee)").max(AttackStyle.MELEE);
		int range = parse(null, RANGE_LOW + "-" + RANGE_HIGH + " (Melee)").max(AttackStyle.MELEE);
		int minimum = parse(null, DRAGONFIRE_MIN + "+ (Dragonfire)").max(AttackStyle.SPECIAL);

		// THEN each gives the most it can hit
		assertEquals(MULTI_HIT * MULTI_COUNT, multiplier);
		assertEquals(MULTI_HIT + SUM_SECOND, sum);
		assertEquals(CHOICE_HIGH, choice);
		assertEquals(RANGE_HIGH, range);
		assertEquals(DRAGONFIRE_MIN, minimum);
	}

	@Test
	public void htmlListsAndRefs()
	{
		// GIVEN max hits in a wiki plainlist and split by <br/>
		String plainlist = "<div class=\"plainlist \" >\n*" + AUTO_MAX + " (auto)\n*" + LIST_SPECIAL + " (special)\n</div>";
		String breaks = BASE_MELEE + " <br/> " + WHIRLWINDS + " (whirlwinds) <br/> " + KNOCKBACK + " (knockback)";

		// WHEN parsing them
		MonsterInfo list = parse(null, plainlist);
		MonsterInfo split = parse("Melee", breaks);

		// THEN every entry is read
		assertEquals(AUTO_MAX, list.max(AttackStyle.MELEE));
		assertEquals(LIST_SPECIAL, list.max(AttackStyle.SPECIAL));
		assertEquals(BASE_MELEE, split.max(AttackStyle.MELEE));
		assertEquals(WHIRLWINDS, split.max(AttackStyle.SPECIAL));
		assertEquals("Whirlwinds", split.getSpecialName());
	}

	@Test
	public void bothStylesInOneLabel()
	{
		// GIVEN one max hit labelled with two styles
		String maxHit = BOTH_STYLES + " (Ranged and Magic)";

		// WHEN parsing it
		MonsterInfo info = parse(null, maxHit);

		// THEN both styles get it and melee does not
		assertEquals(BOTH_STYLES, info.max(AttackStyle.RANGED));
		assertEquals(BOTH_STYLES, info.max(AttackStyle.MAGIC));
		assertFalse(info.has(AttackStyle.MELEE));
	}

	@Test
	public void magicalMeleeIsMagic()
	{
		// GIVEN a "Magical melee" attacker
		String attackStyle = "Magical melee";

		// WHEN parsing its max hit
		MonsterInfo info = parse(attackStyle, String.valueOf(MAGICAL_MELEE));

		// THEN it is magic, which Protect from Magic blocks
		assertEquals(MAGICAL_MELEE, info.max(AttackStyle.MAGIC));
		assertFalse(info.has(AttackStyle.MELEE));
	}

	@Test
	public void skipsGearConditionalMaxes()
	{
		// GIVEN max hits that only apply with or without some gear
		// WHEN parsing them
		MonsterInfo spectre = parse("Magic", String.valueOf(SPECTRE_MAGIC), SPECTRE_UNPROTECTED + " (without Nose peg)");
		MonsterInfo shielded = parse(null, SHIELDED + " (with anti-dragon shield)");
		MonsterInfo boots = parse("Melee", BOOTLESS + " (Ranged/Melee; without insulated boots)");

		// THEN those maxes are ignored
		assertEquals(SPECTRE_MAGIC, spectre.max(AttackStyle.MAGIC));
		assertFalse(spectre.has(AttackStyle.SPECIAL));
		assertFalse(shielded.has(AttackStyle.SPECIAL));
		assertTrue(boots.getMaxHits().isEmpty());
	}

	@Test
	public void skipsNonAttacks()
	{
		// GIVEN max hits that are not numbers to alert on
		// WHEN parsing them
		MonsterInfo passive = parse(null, "0 (Does not attack)");
		MonsterInfo varies = parse(null, "Varies");
		MonsterInfo none = parse(null, "N/A");

		// THEN nothing is recorded
		assertTrue(passive.getMaxHits().isEmpty());
		assertTrue(varies.getMaxHits().isEmpty());
		assertTrue(none.getMaxHits().isEmpty());
	}

	@Test
	public void responseMergesIdsAndRows()
	{
		// GIVEN a bucket response with solo Scurrius, two group Scurrius rows sharing an id
		String json = "{\"bucket\":["
			+ row(SCURRIUS_SOLO_ID, "Scurrius", "Magic", SCURRIUS_MELEE + " (Melee)", SCURRIUS_MAGIC + " (Magic)")
			+ "," + row(SCURRIUS_GROUP_ID, "Scurrius", "Magic", SCURRIUS_MELEE + " (Melee)",
			SCURRIUS_GROUP_MAGIC + " (Magic)", SCURRIUS_GROUP_RANGED + " (Ranged)",
			SCURRIUS_GROUP_ROCKFALL + " (Rockfall)")
			+ "," + row(SCURRIUS_GROUP_ID, "Scurrius", "Melee", SCURRIUS_ALT_MELEE + " (Melee)")
			// AND a goblin listed under two ids, and a non-attacker
			+ ",{\"id\":[\"" + GOBLIN_ID + "\",\"" + GOBLIN_ALT_ID + "\"],\"max_hit\":[\"" + GOBLIN_MAX
			+ "\"],\"name\":\"Goblin\",\"attack_style\":[\"Crush\"]}"
			+ "," + row(NON_ATTACKER_ID, "Nothing", "None", "N/A")
			+ "]}";

		// WHEN parsing it
		Map<Integer, MonsterInfo> out = WikiMonsterParser.parseResponse(new Gson().fromJson(json, JsonObject.class));

		// THEN each id has its max hits
		assertNotNull(out.get(SCURRIUS_SOLO_ID));
		assertEquals(SCURRIUS_MAGIC, out.get(SCURRIUS_SOLO_ID).max(AttackStyle.MAGIC));
		// AND the higher value wins for an id listed twice
		assertEquals(SCURRIUS_MELEE, out.get(SCURRIUS_GROUP_ID).max(AttackStyle.MELEE));
		assertEquals(SCURRIUS_GROUP_MAGIC, out.get(SCURRIUS_GROUP_ID).max(AttackStyle.MAGIC));
		// AND every listed id is covered, but a non-attacker is not
		assertEquals(GOBLIN_MAX, out.get(GOBLIN_ALT_ID).max(AttackStyle.MELEE));
		assertNull(out.get(NON_ATTACKER_ID));
	}
}
