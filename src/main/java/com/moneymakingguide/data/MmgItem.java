package com.moneymakingguide.data;

/**
 * One input or output line of a money making guide.
 *
 * <p>Either {@link #id} is set (the item is priced live off the Grand Exchange) or
 * {@link #flat} is set (the wiki baked in a static value -- spell costs, drop-table
 * expected values, coins). Never both.
 */
public class MmgItem
{
	public String name;
	public double qty;

	/** Wiki item id, absent for statically valued lines. */
	public Integer id;

	/** Static value per unit, absent for GE-priced lines. */
	public Double flat;

	/** 4-hour Grand Exchange buy limit, where one exists. */
	public Integer buyLimit;

	/**
	 * True when {@link #qty} is already expressed per hour. False means it is per
	 * kill/action and must be scaled by the method's rate.
	 */
	public boolean perHour;

	public boolean isGePriced()
	{
		return id != null;
	}
}
