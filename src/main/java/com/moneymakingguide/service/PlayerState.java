package com.moneymakingguide.service;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * An immutable snapshot of everything the filter needs to know about the account.
 *
 * <p>Taken on the client thread and handed to Swing, so the panel never touches live
 * client state off-thread.
 */
@Value
@Builder
public class PlayerState
{
	/** Where {@link #levels} came from, if anywhere. */
	public enum LevelSource
	{
		/** Nothing to filter on -- show every method. */
		NONE,
		/** Read from the logged-in character. Authoritative. */
		LIVE,
		/** Fetched from the hiscores. As current as the player's last hiscore update. */
		HISCORES
	}

	public static final PlayerState EMPTY = PlayerState.builder()
		.loggedIn(false)
		.levelSource(LevelSource.NONE)
		.levels(new int[Skill.values().length])
		.quests(Collections.emptyMap())
		.build();

	boolean loggedIn;

	LevelSource levelSource;

	/** Real (unboosted) levels, indexed by {@link Skill#ordinal()}. */
	int[] levels;

	int combatLevel;
	boolean membersWorld;

	/** Coins in the inventory plus, if the bank has been opened this session, the bank. */
	long coins;

	/** False until the bank has been opened once, at which point {@link #coins} includes it. */
	boolean bankSeen;

	/** Quest state by normalised quest name. Only ever populated while logged in. */
	Map<String, QuestState> quests;

	/** Whose levels these are, for the panel to display. */
	String playerName;

	public boolean hasLevels()
	{
		return levelSource != LevelSource.NONE;
	}

	public int level(Skill skill)
	{
		return levels[skill.ordinal()];
	}
}
