package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DragonfireRulesTest
{
	/** The wiki's dragonfire table: chromatic dragons. */
	private static final int CHROMATIC = 50;
	private static final int CHROMATIC_SHIELD = 5;
	private static final int CHROMATIC_PRAYER = 10;
	private static final int CHROMATIC_ANTIFIRE = 35;
	/** The wiki's dragonfire table: King Black Dragon. */
	private static final int KBD = 65;
	private static final int KBD_SHIELD = 15;
	private static final int KBD_PRAYER = 20;
	private static final int KBD_ANTIFIRE = 50;
	private static final int KBD_PRAYER_ANTIFIRE = 5;

	private static DragonfireRules.Protection protection(boolean shield, boolean prayer, boolean antifire,
		boolean superAntifire)
	{
		DragonfireRules.Protection p = new DragonfireRules.Protection();
		p.shield = shield;
		p.prayer = prayer;
		p.antifire = antifire;
		p.superAntifire = superAntifire;
		return p;
	}

	@Test
	public void appliesToDragonfireButNotVorkathsBomb()
	{
		// GIVEN wiki special names
		// WHEN asking whether dragonfire protection applies
		boolean dragonfire = DragonfireRules.applies("Dragonfire");
		boolean bomb = DragonfireRules.applies("Dragonfire Bomb/Special");
		boolean rockfall = DragonfireRules.applies("Rockfall");

		// THEN only regular dragonfire is protected against
		assertTrue(dragonfire);
		assertFalse(bomb);
		assertFalse(rockfall);
	}

	@Test
	public void chromaticTable()
	{
		// GIVEN a chromatic dragon's unprotected dragonfire max
		int max = CHROMATIC;

		// WHEN the player has each combination of protection
		int none = DragonfireRules.max(max, false, protection(false, false, false, false));
		int shield = DragonfireRules.max(max, false, protection(true, false, false, false));
		int prayer = DragonfireRules.max(max, false, protection(false, true, false, false));
		int antifire = DragonfireRules.max(max, false, protection(false, false, true, false));
		int superAntifire = DragonfireRules.max(max, false, protection(false, false, false, true));
		int shieldAndAntifire = DragonfireRules.max(max, false, protection(true, false, true, false));
		int prayerAndAntifire = DragonfireRules.max(max, false, protection(false, true, true, false));

		// THEN the max matches the wiki's table
		assertEquals(CHROMATIC, none);
		assertEquals(CHROMATIC_SHIELD, shield);
		assertEquals(CHROMATIC_PRAYER, prayer);
		assertEquals(CHROMATIC_ANTIFIRE, antifire);
		assertEquals(0, superAntifire);
		assertEquals(0, shieldAndAntifire);
		assertEquals(0, prayerAndAntifire);
	}

	@Test
	public void kingBlackDragonTable()
	{
		// GIVEN the King Black Dragon's unprotected dragonfire max
		int max = KBD;

		// WHEN the player has each combination of protection
		int none = DragonfireRules.max(max, true, protection(false, false, false, false));
		int shield = DragonfireRules.max(max, true, protection(true, false, false, false));
		int prayer = DragonfireRules.max(max, true, protection(false, true, false, false));
		int antifire = DragonfireRules.max(max, true, protection(false, false, true, false));
		int prayerAndAntifire = DragonfireRules.max(max, true, protection(false, true, true, false));
		int shieldAndSuper = DragonfireRules.max(max, true, protection(true, false, false, true));

		// THEN the max matches the wiki's table
		assertEquals(KBD, none);
		assertEquals(KBD_SHIELD, shield);
		assertEquals(KBD_PRAYER, prayer);
		assertEquals(KBD_ANTIFIRE, antifire);
		assertEquals(KBD_PRAYER_ANTIFIRE, prayerAndAntifire);
		assertEquals(0, shieldAndSuper);
	}
}
