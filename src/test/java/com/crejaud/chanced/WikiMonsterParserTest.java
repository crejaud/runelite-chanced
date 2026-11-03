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
	private static MonsterInfo parse(String attackStyle, String... maxHits)
	{
		return WikiMonsterParser.parse("Test", Arrays.asList(maxHits),
			attackStyle == null ? Collections.emptyList() : Collections.singletonList(attackStyle));
	}

	@Test
	public void labelledStyles()
	{
		MonsterInfo info = parse("Magic", "13 (Melee)", "8 (Magic)", "7 (Ranged)", "22 (Rockfall)");
		assertEquals(13, info.max(AttackStyle.MELEE));
		assertEquals(8, info.max(AttackStyle.MAGIC));
		assertEquals(7, info.max(AttackStyle.RANGED));
		assertEquals(22, info.max(AttackStyle.SPECIAL));
		assertEquals("Rockfall", info.getSpecialName());
	}

	@Test
	public void unlabelledUsesAttackStyle()
	{
		MonsterInfo info = parse("Slash", "36");
		assertEquals(36, info.max(AttackStyle.MELEE));
		assertFalse(info.has(AttackStyle.MAGIC));
	}

	@Test
	public void specialAttackLabel()
	{
		MonsterInfo info = parse("Slash", "30 (magic)", "46 (melee)", "49 (special attack)");
		assertEquals(46, info.max(AttackStyle.MELEE));
		assertEquals(30, info.max(AttackStyle.MAGIC));
		assertEquals(49, info.max(AttackStyle.SPECIAL));
		assertEquals("Special", info.getSpecialName());
	}

	@Test
	public void multipliersSumsAndRanges()
	{
		assertEquals(14, parse(null, "7x2 (Magic)").max(AttackStyle.MAGIC));
		assertEquals(13, parse("Crush", "7+6").max(AttackStyle.MELEE));
		assertEquals(60, parse(null, "50/60 (Melee)").max(AttackStyle.MELEE));
		assertEquals(37, parse(null, "30-37 (Melee)").max(AttackStyle.MELEE));
		assertEquals(50, parse(null, "50+ (Dragonfire)").max(AttackStyle.SPECIAL));
	}

	@Test
	public void htmlListsAndRefs()
	{
		MonsterInfo info = parse(null, "<div class=\"plainlist \" >\n*31 (auto)\n*45 (special)\n</div>");
		assertEquals(31, info.max(AttackStyle.MELEE));
		assertEquals(45, info.max(AttackStyle.SPECIAL));

		info = parse("Melee", "22 <br/> 64 (whirlwinds) <br/> 30 (knockback)");
		assertEquals(22, info.max(AttackStyle.MELEE));
		assertEquals(64, info.max(AttackStyle.SPECIAL));
		assertEquals("Whirlwinds", info.getSpecialName());
	}

	@Test
	public void bothStylesInOneLabel()
	{
		MonsterInfo info = parse(null, "33 (Ranged and Magic)");
		assertEquals(33, info.max(AttackStyle.RANGED));
		assertEquals(33, info.max(AttackStyle.MAGIC));
		assertFalse(info.has(AttackStyle.MELEE));
	}

	@Test
	public void magicalMeleeIsMagic()
	{
		MonsterInfo info = parse("Magical melee", "20");
		assertEquals(20, info.max(AttackStyle.MAGIC));
		assertFalse(info.has(AttackStyle.MELEE));
	}

	@Test
	public void skipsGearConditionalMaxes()
	{
		MonsterInfo spectre = parse("Magic", "14", "44 (without Nose peg)");
		assertEquals(14, spectre.max(AttackStyle.MAGIC));
		assertFalse(spectre.has(AttackStyle.SPECIAL));
		assertFalse(parse(null, "60 (with anti-dragon shield)").has(AttackStyle.SPECIAL));
		assertTrue(parse("Melee", "20 (Ranged/Melee; without insulated boots)").getMaxHits().isEmpty());
	}

	@Test
	public void skipsNonAttacks()
	{
		assertTrue(parse(null, "0 (Does not attack)").getMaxHits().isEmpty());
		assertTrue(parse(null, "Varies").getMaxHits().isEmpty());
		assertTrue(parse(null, "N/A").getMaxHits().isEmpty());
	}

	@Test
	public void responseMergesIdsAndRows()
	{
		String json = "{\"bucket\":["
			+ "{\"id\":[\"7222\"],\"max_hit\":[\"13 (Melee)\",\"8 (Magic)\",\"7 (Ranged)\",\"22 (Rockfall)\"],"
			+ "\"name\":\"Scurrius\",\"attack_style\":[\"Magic\",\"Ranged\",\"Melee\"]},"
			+ "{\"id\":[\"7221\"],\"max_hit\":[\"13 (Melee)\",\"14 (Magic)\",\"13 (Ranged)\",\"24 (Rockfall)\"],"
			+ "\"name\":\"Scurrius\",\"attack_style\":[\"Magic\",\"Ranged\",\"Melee\"]},"
			+ "{\"id\":[\"7221\"],\"max_hit\":[\"9 (Melee)\"],\"name\":\"Scurrius\",\"attack_style\":[\"Melee\"]},"
			+ "{\"id\":[\"3028\",\"3029\"],\"max_hit\":[\"1\"],\"name\":\"Goblin\",\"attack_style\":[\"Crush\"]},"
			+ "{\"id\":[\"9999\"],\"max_hit\":[\"N/A\"],\"name\":\"Nothing\",\"attack_style\":[\"None\"]}"
			+ "]}";
		Map<Integer, MonsterInfo> out = WikiMonsterParser.parseResponse(new Gson().fromJson(json, JsonObject.class));

		MonsterInfo solo = out.get(7222);
		assertNotNull(solo);
		assertEquals(8, solo.max(AttackStyle.MAGIC));
		MonsterInfo group = out.get(7221);
		assertEquals("higher value wins when an id is listed twice", 13, group.max(AttackStyle.MELEE));
		assertEquals(14, group.max(AttackStyle.MAGIC));
		assertEquals(1, out.get(3029).max(AttackStyle.MELEE));
		assertNull(out.get(9999));
	}
}
