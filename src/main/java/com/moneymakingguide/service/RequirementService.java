package com.moneymakingguide.service;

import com.moneymakingguide.data.MmgMethod;
import com.moneymakingguide.data.SkillRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Decides whether an account can actually do a given money maker. */
@Singleton
public class RequirementService
{
	@Inject
	public RequirementService()
	{
	}

	/**
	 * @param bankroll       coins available to fund inputs, or a negative number to skip
	 *                       the affordability check
	 * @param costPerHour    what one hour of the method costs in inputs
	 * @param hardFilterQuests treat an unfinished quest as disqualifying rather than a warning
	 */
	public Assessment assess(MmgMethod method, PlayerState player, double costPerHour,
							 long bankroll, boolean hardFilterQuests, boolean allowMembers)
	{
		List<String> blockers = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		if (method.members && !allowMembers)
		{
			blockers.add("Members only");
		}

		// Levels may come from the client or the hiscores; quests only exist while
		// logged in, since the hiscores do not report them.
		if (player.hasLevels())
		{
			assessSkills(method, player, blockers, warnings);
		}

		if (player.isLoggedIn())
		{
			assessQuests(method, player, blockers, warnings, hardFilterQuests);
		}

		if (bankroll >= 0 && costPerHour > bankroll)
		{
			blockers.add(String.format("Needs %s/hr of inputs", gp(costPerHour)));
		}

		return new Assessment(blockers, warnings);
	}

	private void assessSkills(MmgMethod method, PlayerState player,
							  List<String> blockers, List<String> warnings)
	{
		for (SkillRequirement req : method.skills())
		{
			Integer have = currentLevel(req, player);

			if (have == null)
			{
				// "Quest points" and the like: shown on the card, not enforced.
				continue;
			}

			if (have < req.level)
			{
				String line = String.format("%d %s (you have %d)", req.level, req.skill, have);
				if (req.required)
				{
					blockers.add(line);
				}
				else
				{
					warnings.add("Optional: " + line);
				}
			}
			else if (req.recommended != null && have < req.recommended)
			{
				warnings.add(String.format("%d %s recommended (you have %d)",
					req.recommended, req.skill, have));
			}
		}
	}

	/** @return the player's level for this requirement, or null if it cannot be checked. */
	private Integer currentLevel(SkillRequirement req, PlayerState player)
	{
		if (req.pseudo)
		{
			return "Combat level".equalsIgnoreCase(req.skill) ? player.getCombatLevel() : null;
		}

		try
		{
			return player.level(Skill.valueOf(req.skill.toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException e)
		{
			// A skill the wiki knows about and this client build does not.
			return null;
		}
	}

	private void assessQuests(MmgMethod method, PlayerState player, List<String> blockers,
							  List<String> warnings, boolean hard)
	{
		for (String quest : method.quests())
		{
			QuestState state = player.getQuests().get(normalise(quest));

			if (state == null || state == QuestState.FINISHED)
			{
				// Unrecognised names are usually section links or prose the wiki wrote
				// freehand. Silently ignoring them beats inventing a requirement.
				continue;
			}

			// The wiki frequently only needs a quest *started*, or partially done, and
			// that is not something QuestState can express -- so warn rather than block
			// unless the user has asked for the stricter behaviour.
			String line = quest + (state == QuestState.IN_PROGRESS ? " (in progress)" : " (not started)");

			if (hard)
			{
				blockers.add(line);
			}
			else
			{
				warnings.add("Quest: " + line);
			}
		}
	}

	public static String normalise(String name)
	{
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static String gp(double v)
	{
		if (Math.abs(v) >= 1_000_000)
		{
			return String.format("%.1fm", v / 1_000_000);
		}
		if (Math.abs(v) >= 1_000)
		{
			return String.format("%.0fk", v / 1_000);
		}
		return String.format("%.0f", v);
	}

	@Value
	public static class Assessment
	{
		/** Reasons this method is hidden. Empty means the account can do it. */
		List<String> blockers;

		/** Things worth knowing that do not disqualify the method. */
		List<String> warnings;

		public boolean isEligible()
		{
			return blockers.isEmpty();
		}
	}
}
