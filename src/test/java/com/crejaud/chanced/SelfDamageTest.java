package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SelfDamageTest
{
	private static final int HP = 50;
	/** 12% of 50, rounded down, plus 2 of slack. */
	private static final int BREW_MAX = 8;
	private static final int FLAT = 10;

	@Test
	public void itemNamesNameTheirSelfDamage()
	{
		// GIVEN the names of doses as the game shows them
		// WHEN each is looked up
		SelfDamage divine = SelfDamage.fromItem("Divine super combat potion(4)");
		SelfDamage bastion = SelfDamage.fromItem("Divine bastion potion(2)");
		SelfDamage brew = SelfDamage.fromItem("Zamorak brew(3)");
		SelfDamage mix = SelfDamage.fromItem("Zamorak mix(1)");
		SelfDamage overload = SelfDamage.fromItem("Overload (+)(4)");
		SelfDamage prayer = SelfDamage.fromItem("Prayer potion(4)");
		SelfDamage saradomin = SelfDamage.fromItem("Saradomin brew(4)");

		// THEN only the self-damaging ones are found
		assertEquals(SelfDamage.DIVINE_POTION, divine);
		assertEquals(SelfDamage.DIVINE_POTION, bastion);
		assertEquals(SelfDamage.ZAMORAK_BREW, brew);
		assertEquals(SelfDamage.ZAMORAK_BREW, mix);
		assertEquals(SelfDamage.OVERLOAD, overload);
		assertNull(prayer);
		assertNull(saradomin);
	}

	@Test
	public void gameMessagesNameTheirSelfDamage()
	{
		// GIVEN the rock cake's and locator orb's messages, and an unrelated one
		// WHEN each is looked up
		SelfDamage cake = SelfDamage.fromMessage(SelfDamage.ROCK_CAKE_MESSAGE);
		SelfDamage orb = SelfDamage.fromMessage(SelfDamage.LOCATOR_ORB_MESSAGE);
		SelfDamage other = SelfDamage.fromMessage("You drink some of your divine super combat potion.");

		// THEN the two are found and the other is not
		assertEquals(SelfDamage.ROCK_CAKE, cake);
		assertEquals(SelfDamage.LOCATOR_ORB, orb);
		assertNull(other);
	}

	@Test
	public void rangesFollowTheWiki()
	{
		// GIVEN 50 hitpoints
		// WHEN reading each source's biggest hit
		int divine = SelfDamage.DIVINE_POTION.max(HP);
		int brew = SelfDamage.ZAMORAK_BREW.max(HP);
		int orb = SelfDamage.LOCATOR_ORB.max(HP);

		// THEN divine and the orb are a flat 10, the brew 12% with slack
		assertEquals(FLAT, divine);
		assertEquals(BREW_MAX, brew);
		assertEquals(FLAT, orb);
	}
}
