package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class HunllefRulesTest
{
	private static final int CORRUPTED = NpcID.CRYSTAL_HUNLLEF_RANGED_HM;
	private static final int CRYSTALLINE = NpcID.CRYSTAL_HUNLLEF_MAGIC;

	/** Armour points: helm, body and legs tiers summed. */
	private static final int NO_ARMOUR = 0;
	private static final int FULL_T1 = 3;
	private static final int FULL_T2 = 6;
	private static final int FULL_T3 = 9;

	/** The wiki's Corrupted Hunllef max hits with no protection prayer. */
	private static final int UNPRAYED_NONE = 68;
	private static final int UNPRAYED_T1 = 55;
	private static final int UNPRAYED_T2 = 45;
	private static final int UNPRAYED_T3 = 35;
	private static final int[] UNPRAYED = {UNPRAYED_NONE, UNPRAYED_T1, UNPRAYED_T2, UNPRAYED_T3};
	/** One T1 piece, and full T2 plus one T3 piece, fall a third of the way to the next set. */
	private static final int ONE_T1_PIECE = 1;
	private static final int UNPRAYED_ONE_T1_PIECE = 64;
	private static final int T2_PLUS_ONE_T3_PIECE = 7;
	private static final int UNPRAYED_T2_PLUS_ONE_T3_PIECE = 42;
	/** Corrupted Hunllef's max through the right prayer with no armour. */
	private static final int PRAYED_NONE = 16;
	/** Crystalline Hunllef's tornado and floor max in full T3. */
	private static final int CRYSTALLINE_TORNADO_T3 = 10;

	private static final int HIGH_HP = 65;
	private static final int HIT = 26;
	private static final int T3_HIT = 20;
	private static final int PRAYED_HIT = 8;
	private static final int TORNADO_HIT = 6;

	private static Verdict hit(Attack attack, int damage, int hpBefore)
	{
		return ChanceCalculator.evaluate(Collections.singletonList(new Hit(damage, false, attack)), hpBefore - damage);
	}

	@Test
	public void recognisesBothHunllefs()
	{
		// GIVEN the Corrupted and Crystalline Hunllef NPC ids
		// WHEN checking each
		boolean corrupted = HunllefRules.isHunllef(CORRUPTED);
		boolean crystalline = HunllefRules.isHunllef(CRYSTALLINE);

		// THEN both are the Hunllef
		assertTrue(corrupted);
		assertTrue(crystalline);
	}

	@Test
	public void scalesWithArmour()
	{
		// GIVEN the unprayed max hit table for no armour and full T1, T2 and T3
		int[] table = UNPRAYED;

		// WHEN reading it for full sets and for mixed sets
		int none = HunllefRules.scaled(table, NO_ARMOUR);
		int t1 = HunllefRules.scaled(table, FULL_T1);
		int t2 = HunllefRules.scaled(table, FULL_T2);
		int t3 = HunllefRules.scaled(table, FULL_T3);
		int oneT1Piece = HunllefRules.scaled(table, ONE_T1_PIECE);
		int t2PlusOneT3 = HunllefRules.scaled(table, T2_PLUS_ONE_T3_PIECE);

		// THEN full sets match the wiki and mixed sets fall in between
		assertEquals(UNPRAYED_NONE, none);
		assertEquals(UNPRAYED_T1, t1);
		assertEquals(UNPRAYED_T2, t2);
		assertEquals(UNPRAYED_T3, t3);
		assertEquals(UNPRAYED_ONE_T1_PIECE, oneT1Piece);
		assertEquals(UNPRAYED_T2_PLUS_ONE_T3_PIECE, t2PlusOneT3);
	}

	@Test
	public void unprayedCorruptedUsesArmourTier()
	{
		// GIVEN the Corrupted Hunllef's ranged attack against full T3 armour
		Attack t3 = HunllefRules.attack(CORRUPTED, "Corrupted Hunllef", AttackStyle.RANGED, false, FULL_T3);

		// WHEN it hits at more HP than the T3 max, and at less
		Verdict atHighHp = hit(t3, HIT, HIGH_HP);
		Verdict atLowHp = hit(t3, T3_HIT, UNPRAYED_T3 - 1);

		// THEN only the second is a chance, at the T3 max
		assertNull(atHighHp);
		assertNotNull(atLowHp);
		assertEquals(UNPRAYED_T3, atLowHp.getMaxHit());
	}

	@Test
	public void prayedHitsThroughUpToPrayedMax()
	{
		// GIVEN the Corrupted Hunllef's magic attack through the right prayer, with no armour
		Attack prayed = HunllefRules.attack(CORRUPTED, "Corrupted Hunllef", AttackStyle.MAGIC, true, NO_ARMOUR);

		// WHEN it hits at more HP than the prayed max, and at exactly the prayed max
		Verdict aboveMax = hit(prayed, PRAYED_HIT, PRAYED_NONE + 1);
		Verdict atMax = hit(prayed, PRAYED_HIT, PRAYED_NONE);

		// THEN only the second is a chance, at the prayed max rather than the unprayed one
		assertNull(aboveMax);
		assertNotNull(atMax);
		assertEquals(PRAYED_NONE, atMax.getMaxHit());
		assertEquals("Magic", atMax.getStyle());
	}

	@Test
	public void tornadoesAndFloor()
	{
		// GIVEN a Crystalline Hunllef tornado or floor hit against full T3 armour
		Attack tornado = HunllefRules.attack(CRYSTALLINE, "Crystalline Hunllef", AttackStyle.SPECIAL, false, FULL_T3);

		// WHEN it hits at exactly its max, and at one HP more
		Verdict atMax = hit(tornado, TORNADO_HIT, CRYSTALLINE_TORNADO_T3);
		Verdict aboveMax = hit(tornado, TORNADO_HIT, CRYSTALLINE_TORNADO_T3 + 1);

		// THEN the tornado max is used and only the first is a chance
		assertEquals(CRYSTALLINE_TORNADO_T3, tornado.getMax());
		assertNotNull(atMax);
		assertEquals("Tornado or floor", atMax.getStyle());
		assertNull(aboveMax);
	}
}
