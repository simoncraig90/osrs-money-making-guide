package com.moneymakingguide.service;

import com.moneymakingguide.PriceMode;
import com.moneymakingguide.data.MmgItem;
import com.moneymakingguide.data.MmgMethod;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Reprices a guide against current Grand Exchange data.
 *
 * <p>This deliberately mirrors {@code Module:Mmgtable/display} on the wiki so the numbers
 * are comparable to the published table: inputs are untaxed, outputs are taxed, and
 * per-kill quantities scale by the guide's stated rate.
 */
@Singleton
public class ProfitCalculator
{
	/** The Grand Exchange takes 2% of the sale price... */
	private static final double TAX_RATE = 0.02D;

	/** ...capped at 5m per item... */
	private static final long TAX_CAP = 5_000_000L;

	/** ...and is not charged at all below 100gp. */
	private static final int TAX_FREE_BELOW = 100;

	@Inject
	public ProfitCalculator()
	{
	}

	public Result compute(MmgMethod method, PriceService prices, PriceMode mode, Set<Integer> taxExempt)
	{
		// Non-per-kill guides already quote everything hourly, so nothing scales.
		double kph = method.perKill
			? (method.defaultKph != null ? method.defaultKph : 1D)
			: 0D;

		double cost = total(method.inputs(), prices, mode, kph, false, taxExempt);
		double revenue = total(method.outputs(), prices, mode, kph, true, taxExempt);

		return new Result(cost, revenue, revenue - cost, kph);
	}

	private double total(List<MmgItem> items, PriceService prices, PriceMode mode,
						 double kph, boolean taxed, Set<Integer> taxExempt)
	{
		double total = 0;

		for (MmgItem item : items)
		{
			double unit;

			if (item.isGePriced())
			{
				unit = taxed
					? sellAfterTax(item.id, prices, mode, taxExempt)
					: prices.buyPrice(item.id, mode);
			}
			else
			{
				// A value the wiki computed at page-render time -- spell costs, drop-table
				// expected values, coins. Taken as-is; the wiki does not tax these either.
				unit = item.flat == null ? 0 : item.flat;
			}

			double line = unit * item.qty;

			if (kph > 0 && !item.perHour)
			{
				line *= kph;
			}

			total += line;
		}

		return total;
	}

	private double sellAfterTax(int itemId, PriceService prices, PriceMode mode, Set<Integer> taxExempt)
	{
		int price = prices.sellPrice(itemId, mode);

		if (price < TAX_FREE_BELOW || taxExempt.contains(itemId))
		{
			return price;
		}

		return price - Math.min((long) Math.floor(price * TAX_RATE), TAX_CAP);
	}

	/** Hourly figures for one guide. */
	@Value
	public static class Result
	{
		/** Cost of inputs per hour, before tax. This is the bankroll the method needs. */
		double costPerHour;

		/** Value of outputs per hour, after Grand Exchange tax. */
		double revenuePerHour;

		double profitPerHour;

		/** Kills/actions per hour used for scaling, or 0 if the guide is already hourly. */
		double kph;
	}
}
