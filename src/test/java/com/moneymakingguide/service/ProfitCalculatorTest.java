package com.moneymakingguide.service;

import com.moneymakingguide.PriceMode;
import com.moneymakingguide.data.MmgItem;
import com.moneymakingguide.data.MmgMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ProfitCalculatorTest
{
	private static final double EPSILON = 0.001D;

	/** A PriceService that returns fixed prices, so the maths is testable offline. */
	private static class FakePrices extends PriceService
	{
		private final Map<Integer, Integer> prices = new HashMap<>();

		FakePrices()
		{
			super(null, null, null);
		}

		FakePrices with(int id, int price)
		{
			prices.put(id, price);
			return this;
		}

		@Override
		public int buyPrice(int itemId, PriceMode mode)
		{
			return prices.getOrDefault(itemId, 0);
		}

		@Override
		public int sellPrice(int itemId, PriceMode mode)
		{
			return prices.getOrDefault(itemId, 0);
		}
	}

	private static MmgItem item(Integer id, double qty, boolean perHour)
	{
		MmgItem i = new MmgItem();
		i.id = id;
		i.qty = qty;
		i.perHour = perHour;
		return i;
	}

	private static MmgItem flat(double value, double qty)
	{
		MmgItem i = new MmgItem();
		i.flat = value;
		i.qty = qty;
		return i;
	}

	private static MmgMethod method(boolean perKill, Double kph, MmgItem[] in, MmgItem[] out)
	{
		MmgMethod m = new MmgMethod();
		m.page = "Money making guide/Test";
		m.name = "Test";
		m.perKill = perKill;
		m.defaultKph = kph;
		m.inputs = new ArrayList<>(Arrays.asList(in));
		m.outputs = new ArrayList<>(Arrays.asList(out));
		return m;
	}

	@Test
	public void appliesTwoPercentTaxToOutputsButNotInputs()
	{
		MmgMethod m = method(false, null,
			new MmgItem[]{item(1, 1, true)},
			new MmgItem[]{item(2, 1, true)});

		ProfitCalculator.Result r = new ProfitCalculator().compute(
			m, new FakePrices().with(1, 1000).with(2, 1000), PriceMode.INSTANT, Collections.emptySet());

		assertEquals(1000, r.getCostPerHour(), EPSILON);
		// 1000 - floor(1000 * 0.02) = 980
		assertEquals(980, r.getRevenuePerHour(), EPSILON);
		assertEquals(-20, r.getProfitPerHour(), EPSILON);
	}

	@Test
	public void doesNotTaxItemsUnderOneHundredGp()
	{
		MmgMethod m = method(false, null, new MmgItem[]{}, new MmgItem[]{item(2, 10, true)});

		ProfitCalculator.Result r = new ProfitCalculator().compute(
			m, new FakePrices().with(2, 99), PriceMode.INSTANT, Collections.emptySet());

		assertEquals(990, r.getRevenuePerHour(), EPSILON);
	}

	@Test
	public void doesNotTaxExemptItems()
	{
		MmgMethod m = method(false, null, new MmgItem[]{}, new MmgItem[]{item(7, 1, true)});

		ProfitCalculator.Result r = new ProfitCalculator().compute(
			m, new FakePrices().with(7, 10_000), PriceMode.INSTANT, Set.of(7));

		assertEquals(10_000, r.getRevenuePerHour(), EPSILON);
	}

	@Test
	public void capsTaxAtFiveMillion()
	{
		MmgMethod m = method(false, null, new MmgItem[]{}, new MmgItem[]{item(3, 1, true)});

		// 2% of 1b would be 20m, but the cap holds it to 5m.
		ProfitCalculator.Result r = new ProfitCalculator().compute(
			m, new FakePrices().with(3, 1_000_000_000), PriceMode.INSTANT, Collections.emptySet());

		assertEquals(995_000_000D, r.getRevenuePerHour(), EPSILON);
	}

	@Test
	public void scalesPerKillQuantitiesByRateButLeavesHourlyOnesAlone()
	{
		MmgMethod m = method(true, 100D,
			new MmgItem[]{item(1, 2, false), item(9, 1, true)},
			new MmgItem[]{item(2, 1, false)});

		ProfitCalculator.Result r = new ProfitCalculator().compute(
			m, new FakePrices().with(1, 10).with(9, 5000).with(2, 50),
			PriceMode.INSTANT, Collections.emptySet());

		// per kill: 2 x 10 x 100 kills = 2000; hourly line stays at 5000
		assertEquals(7000, r.getCostPerHour(), EPSILON);
		// 50 is under the tax threshold, so 50 x 1 x 100
		assertEquals(5000, r.getRevenuePerHour(), EPSILON);
		assertEquals(100D, r.getKph(), EPSILON);
	}

	@Test
	public void staticallyValuedLinesAreNeverTaxed()
	{
		MmgMethod m = method(false, null, new MmgItem[]{}, new MmgItem[]{flat(10_000, 2)});

		ProfitCalculator.Result r = new ProfitCalculator().compute(
			m, new FakePrices(), PriceMode.INSTANT, Collections.emptySet());

		assertEquals(20_000, r.getRevenuePerHour(), EPSILON);
	}
}
