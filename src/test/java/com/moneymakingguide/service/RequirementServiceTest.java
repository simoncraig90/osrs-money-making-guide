package com.moneymakingguide.service;

import com.moneymakingguide.data.MmgMethod;
import com.moneymakingguide.data.SkillRequirement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

public class RequirementServiceTest
{
	private final RequirementService service = new RequirementService();

	private static SkillRequirement req(String skill, int level, boolean required, Integer recommended)
	{
		SkillRequirement r = new SkillRequirement();
		r.skill = skill;
		r.level = level;
		r.required = required;
		r.recommended = recommended;
		return r;
	}

	private static MmgMethod method(SkillRequirement... skills)
	{
		MmgMethod m = new MmgMethod();
		m.page = "Money making guide/Test";
		m.name = "Test";
		m.skills = new ArrayList<>(Arrays.asList(skills));
		return m;
	}

	private static PlayerState player(Map<Skill, Integer> levels, Map<String, QuestState> quests)
	{
		int[] arr = new int[Skill.values().length];
		levels.forEach((s, l) -> arr[s.ordinal()] = l);
		return PlayerState.builder()
			.loggedIn(true)
			.levelSource(PlayerState.LevelSource.LIVE)
			.levels(arr)
			.combatLevel(100)
			.membersWorld(true)
			.coins(1_000_000)
			.bankSeen(true)
			.quests(quests)
			.build();
	}

	private static PlayerState player(Map<Skill, Integer> levels)
	{
		return player(levels, Collections.emptyMap());
	}

	@Test
	public void hidesMethodWhenARequiredSkillIsTooLow()
	{
		RequirementService.Assessment a = service.assess(
			method(req("Fishing", 76, true, null)),
			player(Map.of(Skill.FISHING, 62)), 0, -1, false, true);

		assertFalse(a.isEligible());
		assertEquals(1, a.getBlockers().size());
		assertTrue(a.getBlockers().get(0).contains("76 Fishing"));
	}

	@Test
	public void showsMethodWhenTheRequiredSkillIsMet()
	{
		RequirementService.Assessment a = service.assess(
			method(req("Fishing", 76, true, null)),
			player(Map.of(Skill.FISHING, 99)), 0, -1, false, true);

		assertTrue(a.isEligible());
		assertTrue(a.getWarnings().isEmpty());
	}

	/**
	 * The case that makes this filter worth writing: "44 Runecraft (91 recommended)" is
	 * doable at 44. Treating the recommendation as the gate would hide it from exactly
	 * the players who most need to see it.
	 */
	@Test
	public void meetingTheGateButNotTheRecommendationWarnsRatherThanHides()
	{
		RequirementService.Assessment a = service.assess(
			method(req("Runecraft", 44, true, 91)),
			player(Map.of(Skill.RUNECRAFT, 50)), 0, -1, false, true);

		assertTrue(a.isEligible());
		assertEquals(1, a.getWarnings().size());
		assertTrue(a.getWarnings().get(0).contains("91 Runecraft recommended"));
	}

	@Test
	public void optionalSkillsNeverHideAMethod()
	{
		RequirementService.Assessment a = service.assess(
			method(req("Mining", 70, false, null)),
			player(Map.of(Skill.MINING, 1)), 0, -1, false, true);

		assertTrue(a.isEligible());
		assertEquals(1, a.getWarnings().size());
	}

	@Test
	public void pseudoSkillsAreNotEnforced()
	{
		SkillRequirement qp = req("Quest points", 200, true, null);
		qp.pseudo = true;

		RequirementService.Assessment a = service.assess(
			method(qp), player(Collections.emptyMap()), 0, -1, false, true);

		assertTrue(a.isEligible());
	}

	@Test
	public void combatLevelIsEnforced()
	{
		SkillRequirement cb = req("Combat level", 126, true, null);
		cb.pseudo = true;

		RequirementService.Assessment a = service.assess(
			method(cb), player(Collections.emptyMap()), 0, -1, false, true);

		assertFalse(a.isEligible());
	}

	@Test
	public void membersContentIsHiddenOnFreeToPlay()
	{
		MmgMethod m = method();
		m.members = true;

		assertFalse(service.assess(m, player(Collections.emptyMap()), 0, -1, false, false).isEligible());
		assertTrue(service.assess(m, player(Collections.emptyMap()), 0, -1, false, true).isEligible());
	}

	@Test
	public void unaffordableMethodsAreBlockedOnlyWhenABankrollIsGiven()
	{
		MmgMethod m = method();

		assertFalse(service.assess(m, player(Collections.emptyMap()), 5_000_000, 1_000_000, false, true).isEligible());
		// A negative bankroll switches the check off entirely.
		assertTrue(service.assess(m, player(Collections.emptyMap()), 5_000_000, -1, false, true).isEligible());
	}

	/**
	 * The wiki routinely asks only for a quest to be started or partly done, which
	 * QuestState cannot express, so an unfinished quest warns by default.
	 */
	@Test
	public void unfinishedQuestsWarnByDefaultAndBlockWhenAsked()
	{
		MmgMethod m = method();
		m.quests = Collections.singletonList("Enter the Abyss");
		Map<String, QuestState> quests = new HashMap<>();
		quests.put(RequirementService.normalise("Enter the Abyss"), QuestState.NOT_STARTED);

		PlayerState p = player(Collections.emptyMap(), quests);

		RequirementService.Assessment soft = service.assess(m, p, 0, -1, false, true);
		assertTrue(soft.isEligible());
		assertEquals(1, soft.getWarnings().size());

		assertFalse(service.assess(m, p, 0, -1, true, true).isEligible());
	}

	@Test
	public void questNamesMatchDespitePunctuationDifferences()
	{
		assertEquals(RequirementService.normalise("Heroes' Quest"),
			RequirementService.normalise("Heroes quest"));
	}

	@Test
	public void nothingIsFilteredOnSkillsWithNoLevelSource()
	{
		PlayerState p = PlayerState.EMPTY;
		assertTrue(service.assess(method(req("Fishing", 99, true, null)), p, 0, -1, false, true).isEligible());
	}

	/** The point of the hiscores lookup: filtering has to work before you log in. */
	@Test
	public void hiscoreLevelsFilterEvenWhileLoggedOut()
	{
		int[] arr = new int[Skill.values().length];
		arr[Skill.FISHING.ordinal()] = 62;
		PlayerState p = PlayerState.builder()
			.loggedIn(false)
			.levelSource(PlayerState.LevelSource.HISCORES)
			.levels(arr)
			.quests(Collections.emptyMap())
			.playerName("SativaPls")
			.build();

		assertFalse(service.assess(method(req("Fishing", 76, true, null)), p, 0, -1, false, true).isEligible());
		assertTrue(service.assess(method(req("Fishing", 62, true, null)), p, 0, -1, false, true).isEligible());
	}

	/** Quests cannot be checked from the hiscores, so they must not block on that path. */
	@Test
	public void questsAreNotEnforcedFromHiscores()
	{
		MmgMethod m = method();
		m.quests = Collections.singletonList("Enter the Abyss");

		PlayerState p = PlayerState.builder()
			.loggedIn(false)
			.levelSource(PlayerState.LevelSource.HISCORES)
			.levels(new int[Skill.values().length])
			.quests(Collections.emptyMap())
			.build();

		assertTrue(service.assess(m, p, 0, -1, true, true).isEligible());
	}
}
