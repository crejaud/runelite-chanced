package com.crejaud.chanced;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.kit.KitType;

/**
 * The Gauntlet's Hunllef: its max hit depends on the crystal armour worn, its ranged and magic
 * attacks hit through the correct protection prayer, and its tornadoes and floor tiles hit with
 * no attack to see. Numbers are the OSRS Wiki's tables for full armour sets; mixed sets are
 * interpolated by total armour tier.
 */
final class HunllefRules
{
	private static final Set<Integer> CORRUPTED = ImmutableSet.of(
		NpcID.CRYSTAL_HUNLLEF_MELEE_HM, NpcID.CRYSTAL_HUNLLEF_RANGED_HM, NpcID.CRYSTAL_HUNLLEF_MAGIC_HM);
	private static final Set<Integer> CRYSTALLINE = ImmutableSet.of(
		NpcID.CRYSTAL_HUNLLEF_MELEE, NpcID.CRYSTAL_HUNLLEF_RANGED, NpcID.CRYSTAL_HUNLLEF_MAGIC,
		NpcID.LEAGUE_6_CRYSTAL_HUNLLEF_MELEE);

	/** Max hits for no armour, full T1, full T2 and full T3. */
	private static final int[] CORRUPTED_UNPRAYED = {68, 55, 45, 35};
	private static final int[] CORRUPTED_PRAYED = {16, 13, 10, 8};
	private static final int[] CORRUPTED_TORNADO = {30, 25, 20, 15};
	private static final int[] CRYSTALLINE_PRAYED = {12, 10, 8, 6};
	/** The wiki lists only "50+" unprayed; scaled from the Corrupted table by the prayed ratio. */
	private static final int[] CRYSTALLINE_UNPRAYED = {51, 41, 34, 26};
	private static final int[] CRYSTALLINE_TORNADO = {20, 16, 13, 10};

	private static final int T1 = 1;
	private static final int T2 = 2;
	private static final int T3 = 3;
	/** Helm, body and legs. */
	private static final int PIECES = 3;
	/** Full T3: the top of the armour tables. */
	private static final int MAX_POINTS = PIECES * T3;

	private static final Map<Integer, Integer> ARMOUR_TIER = ImmutableMap.<Integer, Integer>builder()
		.put(ItemID.GAUNTLET_HELMET_T1, T1).put(ItemID.GAUNTLET_HELMET_T2, T2).put(ItemID.GAUNTLET_HELMET_T3, T3)
		.put(ItemID.GAUNTLET_CHESTPLATE_T1, T1).put(ItemID.GAUNTLET_CHESTPLATE_T2, T2)
		.put(ItemID.GAUNTLET_CHESTPLATE_T3, T3)
		.put(ItemID.GAUNTLET_PLATELEGS_T1, T1).put(ItemID.GAUNTLET_PLATELEGS_T2, T2)
		.put(ItemID.GAUNTLET_PLATELEGS_T3, T3)
		.put(ItemID.GAUNTLET_HELMET_T1_HM, T1).put(ItemID.GAUNTLET_HELMET_T2_HM, T2)
		.put(ItemID.GAUNTLET_HELMET_T3_HM, T3)
		.put(ItemID.GAUNTLET_CHESTPLATE_T1_HM, T1).put(ItemID.GAUNTLET_CHESTPLATE_T2_HM, T2)
		.put(ItemID.GAUNTLET_CHESTPLATE_T3_HM, T3)
		.put(ItemID.GAUNTLET_PLATELEGS_T1_HM, T1).put(ItemID.GAUNTLET_PLATELEGS_T2_HM, T2)
		.put(ItemID.GAUNTLET_PLATELEGS_T3_HM, T3)
		.build();

	private HunllefRules()
	{
	}

	static boolean isHunllef(int npcId)
	{
		return CORRUPTED.contains(npcId) || CRYSTALLINE.contains(npcId);
	}

	/** Sum of the helm, body and legs tiers (0 to 9). */
	static int armourPoints(Player player)
	{
		PlayerComposition comp = player.getPlayerComposition();
		if (comp == null)
		{
			return 0;
		}
		int points = 0;
		for (KitType slot : new KitType[]{KitType.HEAD, KitType.TORSO, KitType.LEGS})
		{
			points += ARMOUR_TIER.getOrDefault(comp.getEquipmentId(slot), 0);
		}
		return points;
	}

	/**
	 * The Hunllef attack that landed.
	 *
	 * @param style        style of a seen attack, or SPECIAL for a tornado or floor hit
	 * @param prayed       whether the correct protection prayer was up
	 * @param armourPoints {@link #armourPoints} of the player
	 */
	static Attack attack(int npcId, String name, AttackStyle style, boolean prayed, int armourPoints)
	{
		boolean corrupted = CORRUPTED.contains(npcId);
		if (style == AttackStyle.SPECIAL)
		{
			int max = scaled(corrupted ? CORRUPTED_TORNADO : CRYSTALLINE_TORNADO, armourPoints);
			return Attack.builder().source(name).style(AttackStyle.SPECIAL).max(max)
				.specialName("Tornado or floor").build();
		}
		return Attack.builder()
			.source(name)
			.style(style)
			.max(scaled(corrupted ? CORRUPTED_UNPRAYED : CRYSTALLINE_UNPRAYED, armourPoints))
			.prayed(prayed)
			.prayedMax(scaled(corrupted ? CORRUPTED_PRAYED : CRYSTALLINE_PRAYED, armourPoints))
			.build();
	}

	/** Interpolates a full-set table (no armour, full T1, T2, T3) at any point total. */
	static int scaled(int[] table, int points)
	{
		int p = Math.max(0, Math.min(MAX_POINTS, points));
		int i = Math.min(p / PIECES, table.length - 1 - 1);
		int lo = table[i];
		int hi = table[i + 1];
		int rem = p - i * PIECES;
		return lo - ((lo - hi) * rem) / PIECES;
	}
}
