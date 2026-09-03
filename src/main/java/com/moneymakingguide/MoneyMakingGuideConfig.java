package com.moneymakingguide;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MoneyMakingGuideConfig.GROUP)
public interface MoneyMakingGuideConfig extends Config
{
	String GROUP = "moneymakingguide";

	@ConfigSection(
		name = "Filtering",
		description = "Which methods the panel will show you",
		position = 0
	)
	String filtering = "filtering";

	@ConfigSection(
		name = "Pricing",
		description = "How profit is calculated",
		position = 1
	)
	String pricing = "pricing";

	@ConfigSection(
		name = "Data",
		description = "Where the guide data comes from",
		position = 2,
		closedByDefault = true
	)
	String data = "data";

	@ConfigItem(
		keyName = "hideIneligible",
		name = "Hide methods I can't do",
		description = "Hides any method with a skill requirement above your level. Turn off to see them greyed out instead.",
		section = filtering,
		position = 0
	)
	default boolean hideIneligible()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUnaffordable",
		name = "Hide unaffordable methods",
		description = "Hides methods whose hourly input cost is more than your bankroll.",
		section = filtering,
		position = 1
	)
	default boolean hideUnaffordable()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankrollOverride",
		name = "Bankroll override (gp)",
		description = "Coins to assume you have. Leave at 0 to use your actual coins -- note the bank only counts once you have opened it this session.",
		section = filtering,
		position = 2
	)
	default int bankrollOverride()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "hardFilterQuests",
		name = "Treat quests as hard requirements",
		description = "Off by default: the wiki often only needs a quest started or partly done, which the client cannot distinguish from unfinished.",
		section = filtering,
		position = 3
	)
	default boolean hardFilterQuests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "membersFilter",
		name = "Members content",
		description = "Whether to include members-only methods.",
		section = filtering,
		position = 4
	)
	default MembersFilter membersFilter()
	{
		return MembersFilter.AUTO;
	}

	@ConfigItem(
		keyName = "includeRecurring",
		name = "Include recurring methods",
		description = "Farm runs, birdhouses and similar. Their profit is per run, not per hour, so they sort oddly against hourly methods.",
		section = filtering,
		position = 5
	)
	default boolean includeRecurring()
	{
		return false;
	}

	@ConfigItem(
		keyName = "priceMode",
		name = "Price basis",
		description = "Instant is pessimistic and realistic. Midpoint assumes your offers fill. Wiki matches the published table.",
		section = pricing,
		position = 0
	)
	default PriceMode priceMode()
	{
		return PriceMode.INSTANT;
	}

	@ConfigItem(
		keyName = "minProfit",
		name = "Minimum profit/hr",
		description = "Hide anything below this, in gp per hour.",
		section = pricing,
		position = 1
	)
	default int minProfit()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "datasetUrl",
		name = "Dataset URL",
		description = "Where the generated guide data is fetched from. Clear to use the copy bundled with the plugin.",
		section = data,
		position = 0
	)
	default String datasetUrl()
	{
		return "https://simon.github.io/osrs-money-making-guide/mmg-data.json";
	}

	enum MembersFilter
	{
		AUTO("Match current world"),
		MEMBERS("Include"),
		FREE("Free-to-play only");

		private final String label;

		MembersFilter(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
