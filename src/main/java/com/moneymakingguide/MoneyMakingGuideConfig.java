package com.moneymakingguide;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.hiscore.HiscoreEndpoint;

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
		keyName = "hiscoreName",
		name = "Filter by hiscores",
		description = "A username to pull levels from when you are not logged in, so you can plan before you play. Live levels always win while logged in.",
		section = filtering,
		position = 6
	)
	default String hiscoreName()
	{
		return "";
	}

	@ConfigItem(
		keyName = "hiscoreAccountType",
		name = "Hiscores account type",
		description = "Which hiscore table to read the above name from.",
		section = filtering,
		position = 7
	)
	default AccountType hiscoreAccountType()
	{
		return AccountType.NORMAL;
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
		return "https://simoncraig90.github.io/osrs-money-making-guide/mmg-data.json";
	}

	/** The hiscore tables worth offering; HiscoreEndpoint has several more. */
	enum AccountType
	{
		NORMAL("Normal", HiscoreEndpoint.NORMAL),
		IRONMAN("Ironman", HiscoreEndpoint.IRONMAN),
		HARDCORE_IRONMAN("Hardcore ironman", HiscoreEndpoint.HARDCORE_IRONMAN),
		ULTIMATE_IRONMAN("Ultimate ironman", HiscoreEndpoint.ULTIMATE_IRONMAN);

		private final String label;
		private final HiscoreEndpoint endpoint;

		AccountType(String label, HiscoreEndpoint endpoint)
		{
			this.label = label;
			this.endpoint = endpoint;
		}

		public HiscoreEndpoint getEndpoint()
		{
			return endpoint;
		}

		@Override
		public String toString()
		{
			return label;
		}
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
