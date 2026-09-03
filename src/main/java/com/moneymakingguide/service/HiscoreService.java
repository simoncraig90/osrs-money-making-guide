package com.moneymakingguide.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreManager;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Fetches skill levels from the OSRS hiscores.
 *
 * <p>Lets the filter work before you have logged in -- the point of the panel is deciding
 * what to go and do, which is exactly the moment you are not yet in game. Live client
 * levels always win when they are available; this only fills the gap.
 */
@Slf4j
@Singleton
public class HiscoreService
{
	private final HiscoreManager hiscoreManager;
	private final ScheduledExecutorService executor;

	@Getter
	private volatile int[] levels;

	@Getter
	private volatile int combatLevel;

	@Getter
	private volatile String loadedName;

	@Getter
	private volatile String lastError;

	private volatile boolean inFlight;

	@Inject
	HiscoreService(HiscoreManager hiscoreManager, ScheduledExecutorService executor)
	{
		this.hiscoreManager = hiscoreManager;
		this.executor = executor;
	}

	public boolean hasLevels()
	{
		return levels != null;
	}

	public void clear()
	{
		levels = null;
		loadedName = null;
		lastError = null;
	}

	/** Looks the name up off-thread, then runs {@code onDone} whatever the outcome. */
	public void lookup(String name, HiscoreEndpoint endpoint, Runnable onDone)
	{
		if (name == null || name.trim().isEmpty())
		{
			clear();
			onDone.run();
			return;
		}

		String trimmed = name.trim();
		if (inFlight || trimmed.equalsIgnoreCase(loadedName))
		{
			onDone.run();
			return;
		}

		inFlight = true;
		executor.execute(() ->
		{
			try
			{
				HiscoreResult result = hiscoreManager.lookup(trimmed, endpoint);
				if (result == null)
				{
					lastError = "not found";
					clear();
					return;
				}

				levels = toLevels(result);
				combatLevel = combatLevel(levels);
				loadedName = trimmed;
				lastError = null;
				log.debug("loaded hiscore levels for {}", trimmed);
			}
			catch (IOException e)
			{
				lastError = e.getMessage();
				log.warn("hiscore lookup failed for {}", trimmed, e);
			}
			finally
			{
				inFlight = false;
				onDone.run();
			}
		});
	}

	/** Maps the hiscore skill table onto an array indexed by {@link Skill#ordinal()}. */
	private static int[] toLevels(HiscoreResult result)
	{
		int[] out = new int[Skill.values().length];

		for (Map.Entry<HiscoreSkill, net.runelite.client.hiscore.Skill> e : result.getSkills().entrySet())
		{
			Skill skill;
			try
			{
				skill = Skill.valueOf(e.getKey().name().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ex)
			{
				// Hiscore entries that are not skills -- bosses, clue tiers, LMS.
				continue;
			}

			int level = e.getValue().getLevel();
			// The hiscores report -1 for anything the account is unranked in.
			out[skill.ordinal()] = Math.max(level, skill == Skill.HITPOINTS ? 10 : 1);
		}

		return out;
	}

	/** The standard combat level formula; the hiscores do not report it directly. */
	static int combatLevel(int[] levels)
	{
		int attack = levels[Skill.ATTACK.ordinal()];
		int strength = levels[Skill.STRENGTH.ordinal()];
		int defence = levels[Skill.DEFENCE.ordinal()];
		int hitpoints = levels[Skill.HITPOINTS.ordinal()];
		int prayer = levels[Skill.PRAYER.ordinal()];
		int ranged = levels[Skill.RANGED.ordinal()];
		int magic = levels[Skill.MAGIC.ordinal()];

		double base = 0.25D * (defence + hitpoints + Math.floor(prayer / 2D));
		double melee = 0.325D * (attack + strength);
		double range = 0.325D * Math.floor(ranged * 3D / 2D);
		double mage = 0.325D * Math.floor(magic * 3D / 2D);

		return (int) Math.floor(base + Math.max(melee, Math.max(range, mage)));
	}
}
