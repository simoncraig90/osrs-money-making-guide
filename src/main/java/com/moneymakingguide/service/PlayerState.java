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
	public static final PlayerState LOGGED_OUT = PlayerState.builder()
		.loggedIn(false)
		.levels(new int[Skill.values().length])
		.quests(Collections.emptyMap())
		.build();

	boolean loggedIn;

	/** Real (unboosted) levels, indexed by {@link Skill#ordinal()}. */
	int[] levels;

	int combatLevel;
	boolean membersWorld;

	/** Coins in the inventory plus, if the bank has been opened this session, the bank. */
	long coins;

	/** False until the bank has been opened once, at which point {@link #coins} includes it. */
	boolean bankSeen;

	/** Quest state by normalised quest name. */
	Map<String, QuestState> quests;

	public int level(Skill skill)
	{
		return levels[skill.ordinal()];
	}
}
