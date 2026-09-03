package com.moneymakingguide.service;

import com.moneymakingguide.PriceMode;

/**
 * A PriceService with deterministic prices and no network or ItemManager behind it.
 * Lives in main test sources so UI tests can use it too.
 */
public class TestPrices extends PriceService
{
	private final int price;

	public TestPrices(int price)
	{
		super(null, null, null);
		this.price = price;
	}

	@Override
	public int buyPrice(int itemId, PriceMode mode)
	{
		return price;
	}

	@Override
	public int sellPrice(int itemId, PriceMode mode)
	{
		return price;
	}

	@Override
	public boolean hasLivePrices()
	{
		return true;
	}
}
