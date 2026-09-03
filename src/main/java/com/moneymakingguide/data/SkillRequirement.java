package com.moneymakingguide.data;

/** A single skill line parsed out of a guide's requirements. */
public class SkillRequirement
{
	/** Wiki skill name, e.g. {@code "Runecraft"}. See {@link #pseudo}. */
	public String skill;

	/** The hard gate. A guide listing "44 Runecraft (91 recommended)" has level 44. */
	public int level;

	/**
	 * False when the wiki marks the line optional, or recommends the level without
	 * requiring it. Optional lines never hide a method.
	 */
	public boolean required;

	/** A higher level the wiki suggests, if it named one. Always above {@link #level}. */
	public Integer recommended;

	/**
	 * True for entries that are not real skills -- "Combat level", "Quest points",
	 * "Skills". These need bespoke handling rather than a skill lookup.
	 */
	public boolean pseudo;
}
