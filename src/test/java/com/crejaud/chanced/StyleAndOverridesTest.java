package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.SpotanimID;
import org.junit.Test;

public class StyleAndOverridesTest
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
	public void tableKnowsScurrius()
	{
		assertEquals(AttackHint.MELEE, AttackStyleTable.animation(AnimationID.NPC_RAT_BOSS_ATTACK_MELEE_01));
		assertEquals(AttackHint.MAGIC, AttackStyleTable.animation(AnimationID.NPC_RAT_BOSS_ATTACK_MAGIC_01));
		assertEquals(AttackStyle.MAGIC, AttackStyleTable.projectile(SpotanimID.VFX_RAT_BOSS_PROJ_MAGIC_01));
		assertEquals(AttackStyle.RANGED, AttackStyleTable.projectile(SpotanimID.VFX_RAT_BOSS_PROJ_RANGED_01));
		assertTrue(AttackStyleTable.animationCount() > 1000);
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
	public void parsesOverrides()
	{
		Overrides o = Overrides.parse("7222:10692:magic\nbad line\n1:2:nope", " 7222 : magic : 50 ",
			"graphic:1234:20\nprojectile:55:30\nregion:12850:15\nfoo:1:2");
		assertEquals(AttackStyle.MAGIC, o.style(7222, 10692));
		assertNull(o.style(1, 2));
		assertEquals(50, o.passThrough(7222, AttackStyle.MAGIC));
		assertEquals(0, o.passThrough(7222, AttackStyle.MELEE));
		assertEquals(Integer.valueOf(20), o.getGraphicHazards().get(1234));
		assertEquals(Integer.valueOf(30), o.getProjectileHazards().get(55));
		assertEquals(Integer.valueOf(15), o.getRegionHazards().get(12850));
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
