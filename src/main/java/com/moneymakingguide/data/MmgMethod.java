package com.moneymakingguide.data;

import java.util.Collections;
import java.util.List;

/** One money making guide, as published by the wiki's {@code money_making_guide} bucket. */
public class MmgMethod
{
	/** Full wiki page name, e.g. {@code "Money making guide/Killing green dragons"}. */
	public String page;

	/** Page name with the {@code Money making guide/} prefix removed. */
	public String name;

	public String activity;
	public boolean members;
	public String category;
	public String skillCategory;
	public String intensity;
	public boolean recurring;

	/**
	 * True when inputs and outputs are quoted per kill rather than per hour, in which
	 * case they scale by {@link #defaultKph}.
	 */
	public boolean perKill;

	/** Kills or actions per hour. Fractional for slow methods -- some guides run 2.5/hr. */
	public Double defaultKph;

	/** Label for the rate, e.g. "Kills per hour" or "Trips per hour". */
	public String kphLabel;

	public List<SkillRequirement> skills;
	public String skillsText;
	public List<String> quests;
	public String questsText;
	public List<MmgItem> inputs;
	public List<MmgItem> outputs;

	/**
	 * The profit stored on the wiki at page-render time.
	 *
	 * <p>Two caveats make this unsuitable for display: it is a snapshot of whatever
	 * prices the wiki last cached, and it is <em>pre-tax</em> -- the wiki applies Grand
	 * Exchange tax when it renders the table, not when it stores this number. Always
	 * show {@link com.moneymakingguide.service.ProfitCalculator}'s output instead.
	 */
	public double wikiValue;

	public List<SkillRequirement> skills()
	{
		return skills == null ? Collections.emptyList() : skills;
	}

	public List<String> quests()
	{
		return quests == null ? Collections.emptyList() : quests;
	}

	public List<MmgItem> inputs()
	{
		return inputs == null ? Collections.emptyList() : inputs;
	}

	public List<MmgItem> outputs()
	{
		return outputs == null ? Collections.emptyList() : outputs;
	}

	public String wikiUrl()
	{
		return "https://oldschool.runescape.wiki/w/" + page.replace(' ', '_');
	}
}
