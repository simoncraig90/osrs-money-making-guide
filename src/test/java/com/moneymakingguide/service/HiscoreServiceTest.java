package com.moneymakingguide.service;

import static org.junit.Assert.assertEquals;
import net.runelite.api.Skill;
import org.junit.Test;

public class HiscoreServiceTest
{
	private static int[] levels(int attack, int strength, int defence, int hitpoints,
								int prayer, int ranged, int magic)
	{
		int[] l = new int[Skill.values().length];
		l[Skill.ATTACK.ordinal()] = attack;
		l[Skill.STRENGTH.ordinal()] = strength;
		l[Skill.DEFENCE.ordinal()] = defence;
		l[Skill.HITPOINTS.ordinal()] = hitpoints;
		l[Skill.PRAYER.ordinal()] = prayer;
		l[Skill.RANGED.ordinal()] = ranged;
		l[Skill.MAGIC.ordinal()] = magic;
		return l;
	}

	/** The hiscores do not report combat level, so it has to be derived. */
	@Test
	public void maxedAccountIsCombat126()
	{
		assertEquals(126, HiscoreService.combatLevel(levels(99, 99, 99, 99, 99, 99, 99)));
	}

	@Test
	public void freshAccountIsCombat3()
	{
		assertEquals(3, HiscoreService.combatLevel(levels(1, 1, 1, 10, 1, 1, 1)));
	}

	@Test
	public void rangedAndMagicCountWhenTheyBeatMelee()
	{
		// A pure ranger: melee contributes nothing, ranged must carry the level.
		int melee = HiscoreService.combatLevel(levels(1, 1, 1, 99, 1, 99, 1));
		int none = HiscoreService.combatLevel(levels(1, 1, 1, 99, 1, 1, 1));
		assertEquals(true, melee > none);
		// Magic is weighted identically to ranged.
		assertEquals(melee, HiscoreService.combatLevel(levels(1, 1, 1, 99, 1, 1, 99)));
	}

	@Test
	public void prayerCountsAsHalfRoundedDown()
	{
		assertEquals(HiscoreService.combatLevel(levels(50, 50, 50, 50, 44, 1, 1)),
			HiscoreService.combatLevel(levels(50, 50, 50, 50, 45, 1, 1)));
	}
}
