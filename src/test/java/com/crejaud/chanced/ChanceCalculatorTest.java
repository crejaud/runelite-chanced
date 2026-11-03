package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ChanceCalculatorTest
{
	private static Attack npc(String source, AttackStyle style, int max, boolean prayed)
	{
		return Attack.builder().source(source).style(style).max(max).prayed(prayed).build();
	}

	private static List<Hit> one(int damage, Attack attack)
	{
		return Collections.singletonList(new Hit(damage, false, attack));
	}

	@Test
	public void maxAtOrAboveHpAndSurvived()
	{
		Verdict v = ChanceCalculator.evaluate(one(1, npc("Goblin", AttackStyle.MELEE, 2, false)), 1);
		assertNotNull(v);
		assertEquals("Goblin", v.getSource());
		assertEquals(2, v.getMaxHit());
		assertEquals(1, v.getDamage());
		assertEquals(2, v.getHpBefore());
		assertEquals(1, v.getHpAfter());
		assertEquals("Melee", v.getStyle());
	}

	@Test
	public void hpAboveMaxIsNotAChance()
	{
		assertNull(ChanceCalculator.evaluate(one(1, npc("Goblin", AttackStyle.MELEE, 2, false)), 5));
	}

	@Test
	public void eatingOnTheTickCounts()
	{
		// Hit for 1 at 2 HP, ate a shark the same tick: HP after is 21, so HP at the hit counts as 22.
		assertNull(ChanceCalculator.evaluate(one(1, npc("Goblin", AttackStyle.MELEE, 2, false)), 21));
	}

	@Test
	public void deathIsNotAChance()
	{
		assertNull(ChanceCalculator.evaluate(one(5, npc("Goblin", AttackStyle.MELEE, 5, false)), 0));
	}

	@Test
	public void scurriusMagicWrongPrayer()
	{
		Verdict v = ChanceCalculator.evaluate(one(0, npc("Scurrius", AttackStyle.MAGIC, 8, false)), 8);
		assertNotNull(v);
		assertEquals(8, v.getMaxHit());
		assertEquals("Magic", v.getStyle());
	}

	@Test
	public void correctPrayerBlocksNpc()
	{
		assertNull(ChanceCalculator.evaluate(one(0, npc("Scurrius", AttackStyle.MAGIC, 8, true)), 8));
	}

	@Test
	public void hitThroughPrayerIsTheSpecial()
	{
		Attack smash = Attack.builder().source("K'ril Tsutsaroth").style(AttackStyle.MELEE).max(46).prayed(true)
			.specialMax(49).specialName("Prayer smash").build();
		Verdict v = ChanceCalculator.evaluate(one(30, smash), 19);
		assertNotNull(v);
		assertEquals(49, v.getMaxHit());
		assertEquals("Prayer smash", v.getStyle());
	}

	@Test
	public void specialStyle()
	{
		Attack rockfall = Attack.builder().source("Scurrius").style(AttackStyle.SPECIAL).max(22)
			.specialMax(22).specialName("Rockfall").build();
		Verdict v = ChanceCalculator.evaluate(one(10, rockfall), 5);
		assertNotNull(v);
		assertEquals("Rockfall", v.getStyle());
		assertEquals(22, v.getMaxHit());
	}

	@Test
	public void hitsOnOneTickAreSummed()
	{
		Attack a = npc("Rat", AttackStyle.MELEE, 5, false);
		Attack b = npc("Bat", AttackStyle.MELEE, 4, false);
		List<Hit> hits = Arrays.asList(new Hit(1, false, a), new Hit(1, false, b), new Hit(2, true, null));
		// HP before = 6 + 4 = 10; potential = 5 + 4 + 2 (poison) = 11.
		Verdict v = ChanceCalculator.evaluate(hits, 6);
		assertNotNull(v);
		assertEquals(11, v.getMaxHit());
		assertEquals("Rat", v.getSource());
	}

	@Test
	public void twoHitsplatsOfOneAttackCountOnce()
	{
		Attack a = npc("Twin", AttackStyle.MAGIC, 14, false);
		List<Hit> hits = Arrays.asList(new Hit(3, false, a), new Hit(3, false, a));
		Verdict v = ChanceCalculator.evaluate(hits, 8);
		assertNotNull(v);
		assertEquals(14, v.getMaxHit());
	}

	@Test
	public void playerPrayerReducesByFortyPercent()
	{
		Attack pker = Attack.builder().source("Pker").style(AttackStyle.MAGIC).max(50).prayed(true).player(true)
			.build();
		Verdict v = ChanceCalculator.evaluate(one(10, pker), 20);
		assertNotNull(v);
		assertEquals(30, v.getMaxHit());
		assertTrue(v.isEstimate());
		assertNull(ChanceCalculator.evaluate(one(10, pker), 21));
	}

	@Test
	public void unexplainedHitAloneIsNotAChance()
	{
		assertNull(ChanceCalculator.evaluate(one(3, null), 2));
	}

	@Test
	public void damageOverTimeAloneIsNotAChance()
	{
		assertNull(ChanceCalculator.evaluate(Collections.singletonList(new Hit(4, true, null)), 1));
	}
}
