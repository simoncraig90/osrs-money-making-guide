package com.moneymakingguide;

/** Which side of the Grand Exchange spread to price each line at. */
public enum PriceMode
{
	/**
	 * Pay the instant-buy price for inputs, take the instant-sell price for outputs.
	 * Pessimistic, and the closest thing to what actually happens if you do not wait
	 * around for offers to fill.
	 */
	INSTANT("Instant buy/sell"),

	/** Midpoint of the spread. Optimistic: assumes every offer eventually fills. */
	MID("Spread midpoint"),

	/** RuneLite's cached guide price, so numbers line up with the wiki's own table. */
	WIKI_GUIDE("Wiki guide price");

	private final String label;

	PriceMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
