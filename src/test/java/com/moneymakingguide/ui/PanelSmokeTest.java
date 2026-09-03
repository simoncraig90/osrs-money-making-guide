package com.moneymakingguide.ui;

import com.google.gson.Gson;
import com.moneymakingguide.MoneyMakingGuideConfig;
import com.moneymakingguide.data.MmgDataset;
import com.moneymakingguide.data.MmgMethod;
import com.moneymakingguide.service.PlayerState;
import com.moneymakingguide.service.ProfitCalculator;
import com.moneymakingguide.service.RequirementService;
import com.moneymakingguide.service.TestPrices;
import java.io.InputStream;
import java.util.Arrays;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.runelite.api.Skill;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Builds every card from the real bundled dataset. Compiling proves the types line up;
 * this proves 641 rows of real wiki data render without tripping over a null field,
 * an empty requirement list or malformed tooltip HTML.
 */
public class PanelSmokeTest
{
	private static MmgDataset dataset;

	@BeforeClass
	public static void headless()
	{
		System.setProperty("java.awt.headless", "true");
	}

	private static MmgDataset dataset() throws Exception
	{
		if (dataset == null)
		{
			try (InputStream in = PanelSmokeTest.class
				.getResourceAsStream("/com/moneymakingguide/mmg-data.json"))
			{
				assertNotNull("bundled dataset is missing from resources", in);
				dataset = new Gson().fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8), MmgDataset.class);
			}
		}
		return dataset;
	}

	/** Every skill at 60, no quests done -- enough to be eligible for some and not others. */
	private static PlayerState midLevelAccount()
	{
		int[] levels = new int[Skill.values().length];
		Arrays.fill(levels, 60);
		return PlayerState.builder()
			.loggedIn(true)
			.levelSource(PlayerState.LevelSource.LIVE)
			.levels(levels)
			.combatLevel(80)
			.membersWorld(true)
			.coins(5_000_000)
			.bankSeen(true)
			.quests(java.util.Collections.emptyMap())
			.build();
	}

	@Test
	public void bundledDatasetLooksSane() throws Exception
	{
		MmgDataset d = dataset();
		assertTrue("expected a few hundred methods", d.methods().size() > 300);
		assertTrue("expected a tax exemption list", d.taxExemptIds().size() > 10);

		for (MmgMethod m : d.methods())
		{
			assertNotNull(m.page, m.name);
			assertTrue(m.page + " should be a guide subpage", m.page.startsWith("Money making guide/"));
			assertTrue(m.name + " has an unusable wiki url", m.wikiUrl().startsWith("https://"));
		}
	}

	@Test
	public void everyMethodRendersACard() throws Exception
	{
		MmgDataset d = dataset();
		ProfitCalculator calculator = new ProfitCalculator();
		RequirementService requirements = new RequirementService();
		TestPrices prices = new TestPrices(1000);
		MoneyMakingGuideConfig config = new MoneyMakingGuideConfig()
		{
		};

		// Run every method past both a logged-out account and a mid-level one, so the
		// eligible, blocked and warning branches all get built at least once.
		int rendered = 0;
		int blocked = 0;
		for (PlayerState player : Arrays.asList(PlayerState.EMPTY, midLevelAccount()))
		{
			for (MmgMethod m : d.methods())
			{
				ProfitCalculator.Result profit = calculator.compute(
					m, prices, config.priceMode(), d.taxExemptIds());

				RequirementService.Assessment assessment = requirements.assess(
					m, player, profit.getCostPerHour(), 5_000_000L, false, true);

				MethodCard card = new MethodCard(m, profit, assessment);
				assertTrue(m.name + " rendered no content", card.getComponentCount() > 0);
				// Force a layout pass so BoxLayout and the tooltip HTML are exercised.
				card.setSize(225, card.getPreferredSize().height);
				card.doLayout();
				rendered++;

				if (!assessment.isEligible())
				{
					blocked++;
				}
			}
		}

		assertTrue(rendered > 600);
		assertTrue("a level 60 account should be blocked from something", blocked > 50);
	}
}
