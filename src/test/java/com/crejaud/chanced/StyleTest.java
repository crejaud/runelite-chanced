package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;

public class StyleTest
{
	private static final int SCURRIUS_MELEE_MAX = 13;
	private static final int SCURRIUS_MAGIC_MAX = 8;
	private static final int SCURRIUS_RANGED_MAX = 7;
	private static final int SCURRIUS_ROCKFALL_MAX = 22;

	/** Scurrius in the bundled snapshot: NPC_RAT_BOSS_ATTACK_MELEE_01, _MAGIC_01, VFX_RAT_BOSS_PROJ_MAGIC_01, _RANGED_01. */
	private static final int SCURRIUS_MELEE_ANIMATION = 10692;
	private static final int SCURRIUS_MAGIC_ANIMATION = 10696;
	private static final int SCURRIUS_MAGIC_PROJECTILE = 2640;
	private static final int SCURRIUS_RANGED_PROJECTILE = 2642;
	private static final int AT_LEAST_ANIMATIONS = 1000;

	private static final int STRENGTH_BONUS = 100;
	private static final int WEAPON_ATTACK = 90;
	private static final int RING_STRENGTH = 8;
	private static final int PERCENT = 100;
	private static final int SPEC_MARGIN = 25;
	/** 156 effective strength * (100 + 8 + 64) / 640, rounded. */
	private static final int MELEE_ESTIMATE = 42;
	private static final int MELEE_ESTIMATE_WITH_SPEC = MELEE_ESTIMATE * (PERCENT + SPEC_MARGIN) / PERCENT;
	private static final int MAGIC_ATTACK = 20;
	private static final float MAGIC_DAMAGE_PERCENT = 20f;
	/** A 30 Ice Barrage boosted by 20%. */
	private static final int MAGIC_ESTIMATE = 36;

	private static final Set<AttackStyle> NONE = EnumSet.noneOf(AttackStyle.class);

	private static MonsterInfo scurrius()
	{
		MonsterInfo info = new MonsterInfo("Scurrius");
		info.put(AttackStyle.MELEE, SCURRIUS_MELEE_MAX);
		info.put(AttackStyle.MAGIC, SCURRIUS_MAGIC_MAX);
		info.put(AttackStyle.RANGED, SCURRIUS_RANGED_MAX);
		info.putSpecial(SCURRIUS_ROCKFALL_MAX, "Rockfall");
		return info;
	}

	private static ChancedPlugin.Pending pending(AttackStyle style, boolean projectile)
	{
		ChancedPlugin.Pending p = new ChancedPlugin.Pending();
		p.style = style;
		p.projectile = projectile;
		return p;
	}

	@Test
	public void bundledTableKnowsScurrius()
	{
		// GIVEN the bundled attack style snapshot
		AttackStyleTable table = new AttackStyleTable();

		// WHEN looking up Scurrius's attack animations and projectiles
		AttackHint melee = table.animation(SCURRIUS_MELEE_ANIMATION);
		AttackHint magic = table.animation(SCURRIUS_MAGIC_ANIMATION);
		AttackStyle magicProjectile = table.projectile(SCURRIUS_MAGIC_PROJECTILE);
		AttackStyle rangedProjectile = table.projectile(SCURRIUS_RANGED_PROJECTILE);

		// THEN each has the style its gameval name implies
		assertEquals(AttackHint.MELEE, melee);
		assertEquals(AttackHint.MAGIC, magic);
		assertEquals(AttackStyle.MAGIC, magicProjectile);
		assertEquals(AttackStyle.RANGED, rangedProjectile);
		// AND the table covers the game's attack animations
		assertTrue(table.animationCount() > AT_LEAST_ANIMATIONS);
	}

	@Test
	public void knownStyleWins()
	{
		// GIVEN a Scurrius attack whose animation says melee
		ChancedPlugin.Pending attack = pending(AttackStyle.MELEE, false);

		// WHEN choosing its style while praying melee
		AttackStyle style = ChancedPlugin.chooseStyle(scurrius(), attack, false, EnumSet.of(AttackStyle.MELEE));

		// THEN it stays melee
		assertEquals(AttackStyle.MELEE, style);
	}

	@Test
	public void unknownProjectileTakesWorstUnprayedStyle()
	{
		// GIVEN a Scurrius projectile of unknown style
		ChancedPlugin.Pending projectile = pending(null, true);

		// WHEN choosing its style while praying magic, and while praying nothing
		AttackStyle prayingMagic = ChancedPlugin.chooseStyle(scurrius(), projectile, false,
			EnumSet.of(AttackStyle.MAGIC));
		AttackStyle prayingNothing = ChancedPlugin.chooseStyle(scurrius(), projectile, false, NONE);

		// THEN it is the worst unprayed of ranged and magic
		assertEquals(AttackStyle.RANGED, prayingMagic);
		assertEquals(AttackStyle.MAGIC, prayingNothing);
	}

	@Test
	public void unexplainedHitIsTheSpecial()
	{
		// GIVEN Scurrius, who has a special, and a goblin, who has none
		MonsterInfo goblin = new MonsterInfo("Goblin");
		goblin.put(AttackStyle.MELEE, 1);

		// WHEN each lands a hit with no attack seen
		AttackStyle scurriusStyle = ChancedPlugin.chooseStyle(scurrius(), pending(null, false), true, NONE);
		AttackStyle goblinStyle = ChancedPlugin.chooseStyle(goblin, pending(null, false), true, NONE);

		// THEN Scurrius's hit is the special and the goblin's is its normal style
		assertEquals(AttackStyle.SPECIAL, scurriusStyle);
		assertEquals(AttackStyle.MELEE, goblinStyle);
	}

	@Test
	public void pvpMeleeEstimate()
	{
		// GIVEN a melee weapon and strength bonus visible on another player
		PvpEstimator.Gear gear = new PvpEstimator.Gear();
		gear.str = STRENGTH_BONUS;
		gear.weaponMelee = WEAPON_ATTACK;

		// WHEN estimating with an assumed ring, without and with a spec margin
		AttackStyle style = PvpEstimator.weaponStyle(gear);
		int plain = PvpEstimator.maxHit(gear, AttackStyle.MELEE, RING_STRENGTH, 0, 0);
		int withSpec = PvpEstimator.maxHit(gear, AttackStyle.MELEE, RING_STRENGTH, 0, SPEC_MARGIN);

		// THEN it is a melee estimate, raised by the margin
		assertEquals(AttackStyle.MELEE, style);
		assertEquals(MELEE_ESTIMATE, plain);
		assertEquals(MELEE_ESTIMATE_WITH_SPEC, withSpec);
	}

	@Test
	public void pvpMagicEstimate()
	{
		// GIVEN a magic weapon and magic damage bonus visible on another player
		PvpEstimator.Gear mage = new PvpEstimator.Gear();
		mage.weaponMagic = MAGIC_ATTACK;
		mage.mdmg = MAGIC_DAMAGE_PERCENT;

		// WHEN estimating without a spec margin
		AttackStyle style = PvpEstimator.weaponStyle(mage);
		int max = PvpEstimator.maxHit(mage, AttackStyle.MAGIC, 0, 0, 0);

		// THEN it is an Ice Barrage boosted by the magic damage bonus
		assertEquals(AttackStyle.MAGIC, style);
		assertEquals(MAGIC_ESTIMATE, max);
	}
}
