package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

public class StyleTest
{
	private static MonsterInfo scurrius()
	{
		MonsterInfo info = new MonsterInfo("Scurrius");
		info.put(AttackStyle.MELEE, 13);
		info.put(AttackStyle.MAGIC, 8);
		info.put(AttackStyle.RANGED, 7);
		info.putSpecial(22, "Rockfall");
		return info;
	}

	private static ChancedPlugin.Pending pending(AttackStyle style, boolean projectile)
	{
		ChancedPlugin.Pending p = new ChancedPlugin.Pending();
		p.style = style;
		p.projectile = projectile;
		return p;
	}

	private static final Set<AttackStyle> NONE = EnumSet.noneOf(AttackStyle.class);

	@Test
	public void bundledTableKnowsScurrius()
	{
		AttackStyleTable table = new AttackStyleTable();
		// Scurrius: NPC_RAT_BOSS_ATTACK_MELEE_01, _MAGIC_01, VFX_RAT_BOSS_PROJ_MAGIC_01, _RANGED_01
		assertEquals(AttackHint.MELEE, table.animation(10692));
		assertEquals(AttackHint.MAGIC, table.animation(10696));
		assertEquals(AttackStyle.MAGIC, table.projectile(2640));
		assertEquals(AttackStyle.RANGED, table.projectile(2642));
		assertTrue(table.animationCount() > 1000);
	}

	@Test
	public void knownStyleWins()
	{
		assertEquals(AttackStyle.MELEE,
			ChancedPlugin.chooseStyle(scurrius(), pending(AttackStyle.MELEE, false), false, EnumSet.of(AttackStyle.MELEE)));
	}

	@Test
	public void unknownProjectileTakesWorstUnprayedStyle()
	{
		assertEquals(AttackStyle.RANGED,
			ChancedPlugin.chooseStyle(scurrius(), pending(null, true), false, EnumSet.of(AttackStyle.MAGIC)));
		assertEquals(AttackStyle.MAGIC, ChancedPlugin.chooseStyle(scurrius(), pending(null, true), false, NONE));
	}

	@Test
	public void unexplainedHitIsTheSpecial()
	{
		assertEquals(AttackStyle.SPECIAL, ChancedPlugin.chooseStyle(scurrius(), pending(null, false), true, NONE));
		MonsterInfo goblin = new MonsterInfo("Goblin");
		goblin.put(AttackStyle.MELEE, 1);
		assertEquals(AttackStyle.MELEE, ChancedPlugin.chooseStyle(goblin, pending(null, false), true, NONE));
	}

	@Test
	public void pvpEstimates()
	{
		PvpEstimator.Gear gear = new PvpEstimator.Gear();
		gear.str = 100;
		gear.weaponMelee = 90;
		assertEquals(AttackStyle.MELEE, PvpEstimator.weaponStyle(gear));
		// effective 156 * (100 + 8 + 64) / 640 = 41.9 -> 42, no spec margin
		assertEquals(42, PvpEstimator.maxHit(gear, AttackStyle.MELEE, 8, 0, 0));
		assertEquals(52, PvpEstimator.maxHit(gear, AttackStyle.MELEE, 8, 0, 25));

		PvpEstimator.Gear mage = new PvpEstimator.Gear();
		mage.weaponMagic = 20;
		mage.mdmg = 20f;
		assertEquals(AttackStyle.MAGIC, PvpEstimator.weaponStyle(mage));
		assertEquals(36, PvpEstimator.maxHit(mage, AttackStyle.MAGIC, 0, 0, 0));
	}
}
