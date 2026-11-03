package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DragonfireRulesTest
{
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
		assertTrue(DragonfireRules.applies("Dragonfire"));
		assertFalse(DragonfireRules.applies("Dragonfire Bomb/Special"));
		assertFalse(DragonfireRules.applies("Rockfall"));
	}

	@Test
	public void chromaticTable()
	{
		assertEquals(50, DragonfireRules.max(50, false, protection(false, false, false, false)));
		assertEquals(5, DragonfireRules.max(50, false, protection(true, false, false, false)));
		assertEquals(10, DragonfireRules.max(50, false, protection(false, true, false, false)));
		assertEquals(35, DragonfireRules.max(50, false, protection(false, false, true, false)));
		assertEquals(0, DragonfireRules.max(50, false, protection(false, false, false, true)));
		assertEquals(0, DragonfireRules.max(50, false, protection(true, false, true, false)));
		assertEquals(0, DragonfireRules.max(50, false, protection(false, true, true, false)));
	}

	@Test
	public void kingBlackDragonTable()
	{
		assertEquals(65, DragonfireRules.max(65, true, protection(false, false, false, false)));
		assertEquals(15, DragonfireRules.max(65, true, protection(true, false, false, false)));
		assertEquals(20, DragonfireRules.max(65, true, protection(false, true, false, false)));
		assertEquals(50, DragonfireRules.max(65, true, protection(false, false, true, false)));
		assertEquals(5, DragonfireRules.max(65, true, protection(false, true, true, false)));
		assertEquals(0, DragonfireRules.max(65, true, protection(true, false, false, true)));
	}
}
