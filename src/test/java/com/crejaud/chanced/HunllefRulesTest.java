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

	private static Verdict hit(Attack attack, int damage, int hpAfter)
	{
		return ChanceCalculator.evaluate(Collections.singletonList(new Hit(damage, false, attack)), hpAfter);
	}

	@Test
	public void recognisesBothHunllefs()
	{
		assertTrue(HunllefRules.isHunllef(CORRUPTED));
		assertTrue(HunllefRules.isHunllef(CRYSTALLINE));
	}

	@Test
	public void scalesWithArmour()
	{
		int[] unprayed = {68, 55, 45, 35};
		assertEquals(68, HunllefRules.scaled(unprayed, 0));
		assertEquals(55, HunllefRules.scaled(unprayed, 3));
		assertEquals(45, HunllefRules.scaled(unprayed, 6));
		assertEquals(35, HunllefRules.scaled(unprayed, 9));
		// mixed sets fall between the full-set values
		assertEquals(64, HunllefRules.scaled(unprayed, 1));
		assertEquals(42, HunllefRules.scaled(unprayed, 7));
	}

	@Test
	public void unprayedCorruptedUsesArmourTier()
	{
		// Full T3: max 35. At 65 HP a 26 is not a chance; at 30 HP it is.
		Attack t3 = HunllefRules.attack(CORRUPTED, "Corrupted Hunllef", AttackStyle.RANGED, false, 9);
		assertNull(hit(t3, 26, 39));
		Verdict v = hit(t3, 20, 10);
		assertNotNull(v);
		assertEquals(35, v.getMaxHit());
	}

	@Test
	public void prayedHitsThroughUpToPrayedMax()
	{
		// No armour, praying correctly: max 16, so an 8 at 20 HP is not a chance...
		Attack prayed = HunllefRules.attack(CORRUPTED, "Corrupted Hunllef", AttackStyle.MAGIC, true, 0);
		assertNull(hit(prayed, 8, 12));
		// ...but at 14 HP it is, and the max shown is 16, not 68.
		Verdict v = hit(prayed, 8, 6);
		assertNotNull(v);
		assertEquals(16, v.getMaxHit());
		assertEquals("Magic", v.getStyle());
	}

	@Test
	public void tornadoesAndFloor()
	{
		Attack tornado = HunllefRules.attack(CRYSTALLINE, "Crystalline Hunllef", AttackStyle.SPECIAL, false, 9);
		assertEquals(10, tornado.getMax());
		Verdict v = hit(tornado, 6, 3);
		assertNotNull(v);
		assertEquals("Tornado or floor", v.getStyle());
		assertNull(hit(tornado, 6, 5));
	}
}
