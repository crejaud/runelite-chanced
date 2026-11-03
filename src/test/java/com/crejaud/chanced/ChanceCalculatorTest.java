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
	private static final int GOBLIN_MAX = 2;
	private static final int GOBLIN_HIT = 1;
	private static final int SHARK_HEAL = 20;
	private static final int SAFE_HP = 6;

	private static final int SCURRIUS_MAGIC_MAX = 8;
	private static final int SCURRIUS_ROCKFALL_MAX = 22;
	private static final int ROCKFALL_HIT = 10;
	private static final int LOW_HP = 5;

	private static final int KRIL_MELEE_MAX = 46;
	private static final int KRIL_SMASH_MAX = 49;
	private static final int SMASH_HIT = 30;
	private static final int HP_AFTER_SMASH = 19;

	private static final int CORP_MAGIC_MAX = 60;
	private static final int CORP_HIT = 20;
	private static final int HP_AFTER_CORP = 10;

	private static final int RAT_MAX = 5;
	private static final int BAT_MAX = 4;
	private static final int POISON = 2;

	private static final int SPLIT_ATTACK_MAX = 14;
	private static final int SPLIT_HIT = 3;

	private static final int PERCENT = 100;
	private static final int PKER_ESTIMATE = 50;
	private static final int PKER_HIT = 10;
	private static final int PKER_PRAYED_MAX = PKER_ESTIMATE * ChanceCalculator.PVP_PRAYER_PERCENT / PERCENT;

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
		// GIVEN a goblin that can hit 2 hits for 1
		List<Hit> hits = one(GOBLIN_HIT, npc("Goblin", AttackStyle.MELEE, GOBLIN_MAX, false));

		// WHEN the player is left on its max minus the hit
		Verdict v = ChanceCalculator.evaluate(hits, GOBLIN_MAX - GOBLIN_HIT);

		// THEN it was a chance at the goblin's max
		assertNotNull(v);
		assertEquals("Goblin", v.getSource());
		assertEquals(GOBLIN_MAX, v.getMaxHit());
		assertEquals(GOBLIN_HIT, v.getDamage());
		assertEquals(GOBLIN_MAX, v.getHpBefore());
		assertEquals(GOBLIN_MAX - GOBLIN_HIT, v.getHpAfter());
		assertEquals("Melee", v.getStyle());
	}

	@Test
	public void hpAboveMaxIsNotAChance()
	{
		// GIVEN a goblin that can hit 2 hits for 1
		List<Hit> hits = one(GOBLIN_HIT, npc("Goblin", AttackStyle.MELEE, GOBLIN_MAX, false));

		// WHEN the player had more HP than its max
		Verdict v = ChanceCalculator.evaluate(hits, SAFE_HP);

		// THEN it was never in danger
		assertNull(v);
	}

	@Test
	public void eatingOnTheTickCounts()
	{
		// GIVEN a goblin hits for 1 when the player is on its max of 2
		List<Hit> hits = one(GOBLIN_HIT, npc("Goblin", AttackStyle.MELEE, GOBLIN_MAX, false));

		// WHEN the player eats a shark on the same tick
		Verdict v = ChanceCalculator.evaluate(hits, GOBLIN_MAX - GOBLIN_HIT + SHARK_HEAL);

		// THEN the healed HP counts at the hit, so it was not a chance
		assertNull(v);
	}

	@Test
	public void deathIsNotAChance()
	{
		// GIVEN a hit for the full max
		List<Hit> hits = one(RAT_MAX, npc("Rat", AttackStyle.MELEE, RAT_MAX, false));

		// WHEN the player dies
		Verdict v = ChanceCalculator.evaluate(hits, 0);

		// THEN there is no alert
		assertNull(v);
	}

	@Test
	public void scurriusMagicWrongPrayer()
	{
		// GIVEN Scurrius's magic attack hits 0 without Protect from Magic
		List<Hit> hits = one(0, npc("Scurrius", AttackStyle.MAGIC, SCURRIUS_MAGIC_MAX, false));

		// WHEN the player is on exactly its max
		Verdict v = ChanceCalculator.evaluate(hits, SCURRIUS_MAGIC_MAX);

		// THEN it was a magic chance at its max
		assertNotNull(v);
		assertEquals(SCURRIUS_MAGIC_MAX, v.getMaxHit());
		assertEquals("Magic", v.getStyle());
	}

	@Test
	public void correctPrayerBlocksNpc()
	{
		// GIVEN Scurrius's magic attack hits 0 through Protect from Magic
		List<Hit> hits = one(0, npc("Scurrius", AttackStyle.MAGIC, SCURRIUS_MAGIC_MAX, true));

		// WHEN the player is on exactly its max
		Verdict v = ChanceCalculator.evaluate(hits, SCURRIUS_MAGIC_MAX);

		// THEN the prayer blocked it, so it was not a chance
		assertNull(v);
	}

	@Test
	public void hitThroughPrayerIsTheSpecial()
	{
		// GIVEN K'ril, whose prayer smash ignores Protect from Melee
		Attack smash = Attack.builder().source("K'ril Tsutsaroth").style(AttackStyle.MELEE).max(KRIL_MELEE_MAX)
			.prayed(true).specialMax(KRIL_SMASH_MAX).specialName("Prayer smash").build();

		// WHEN a melee hit goes through the prayer
		Verdict v = ChanceCalculator.evaluate(one(SMASH_HIT, smash), HP_AFTER_SMASH);

		// THEN it counts as the prayer smash
		assertNotNull(v);
		assertEquals(KRIL_SMASH_MAX, v.getMaxHit());
		assertEquals("Prayer smash", v.getStyle());
	}

	@Test
	public void hitThroughPrayerWithoutSpecialCountsDamageDealt()
	{
		// GIVEN an NPC with no special whose attack the right prayer only reduces
		Attack corp = Attack.builder().source("Corporeal Beast").style(AttackStyle.MAGIC).max(CORP_MAGIC_MAX)
			.prayed(true).build();

		// WHEN a hit goes through Protect from Magic at less HP than its max
		Verdict v = ChanceCalculator.evaluate(one(CORP_HIT, corp), HP_AFTER_CORP);

		// THEN only the damage dealt counts, so it was not a chance
		assertNull(v);
	}

	@Test
	public void specialStyle()
	{
		// GIVEN Scurrius's rockfall
		Attack rockfall = Attack.builder().source("Scurrius").style(AttackStyle.SPECIAL).max(SCURRIUS_ROCKFALL_MAX)
			.specialMax(SCURRIUS_ROCKFALL_MAX).specialName("Rockfall").build();

		// WHEN it hits at less HP than its max
		Verdict v = ChanceCalculator.evaluate(one(ROCKFALL_HIT, rockfall), LOW_HP);

		// THEN the alert names the special
		assertNotNull(v);
		assertEquals("Rockfall", v.getStyle());
		assertEquals(SCURRIUS_ROCKFALL_MAX, v.getMaxHit());
	}

	@Test
	public void hitsOnOneTickAreSummed()
	{
		// GIVEN a rat and a bat each hit for 1
		Attack rat = npc("Rat", AttackStyle.MELEE, RAT_MAX, false);
		Attack bat = npc("Bat", AttackStyle.MELEE, BAT_MAX, false);
		// AND poison ticks on the same tick
		List<Hit> hits = Arrays.asList(new Hit(1, false, rat), new Hit(1, false, bat), new Hit(POISON, true, null));

		// WHEN the player is left on 6 HP
		Verdict v = ChanceCalculator.evaluate(hits, SAFE_HP);

		// THEN both maxes plus the poison could have hit, blamed on the biggest threat
		assertNotNull(v);
		assertEquals(RAT_MAX + BAT_MAX + POISON, v.getMaxHit());
		assertEquals("Rat", v.getSource());
	}

	@Test
	public void twoHitsplatsOfOneAttackCountOnce()
	{
		// GIVEN one attack that lands as two hitsplats
		Attack split = npc("Twin", AttackStyle.MAGIC, SPLIT_ATTACK_MAX, false);
		List<Hit> hits = Arrays.asList(new Hit(SPLIT_HIT, false, split), new Hit(SPLIT_HIT, false, split));

		// WHEN the player survives with less HP than its max
		Verdict v = ChanceCalculator.evaluate(hits, SAFE_HP);

		// THEN its max counts once
		assertNotNull(v);
		assertEquals(SPLIT_ATTACK_MAX, v.getMaxHit());
	}

	@Test
	public void playerPrayerReducesByFortyPercent()
	{
		// GIVEN a player's estimated max against the right protection prayer
		Attack pker = Attack.builder().source("Pker").style(AttackStyle.MAGIC).max(PKER_ESTIMATE).prayed(true)
			.player(true).build();

		// WHEN they hit at exactly the prayed max, and at one HP more
		Verdict atMax = ChanceCalculator.evaluate(one(PKER_HIT, pker), PKER_PRAYED_MAX - PKER_HIT);
		Verdict aboveMax = ChanceCalculator.evaluate(one(PKER_HIT, pker), PKER_PRAYED_MAX - PKER_HIT + 1);

		// THEN only the first was a chance, at the prayed max, marked as an estimate
		assertNotNull(atMax);
		assertEquals(PKER_PRAYED_MAX, atMax.getMaxHit());
		assertTrue(atMax.isEstimate());
		assertNull(aboveMax);
	}

	@Test
	public void unexplainedHitAloneIsNotAChance()
	{
		// GIVEN a hit that no attack explains
		List<Hit> hits = one(SPLIT_HIT, null);

		// WHEN the player is left on 1 HP
		Verdict v = ChanceCalculator.evaluate(hits, 1);

		// THEN there is no known max, so no alert
		assertNull(v);
	}

	@Test
	public void damageOverTimeAloneIsNotAChance()
	{
		// GIVEN only a poison tick
		List<Hit> hits = Collections.singletonList(new Hit(POISON, true, null));

		// WHEN the player is left on 1 HP
		Verdict v = ChanceCalculator.evaluate(hits, 1);

		// THEN there is no alert
		assertNull(v);
	}
}
