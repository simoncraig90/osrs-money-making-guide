package com.moneymakingguide.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The generated dataset, as produced by {@code tools/build_dataset.py}. */
public class MmgDataset
{
	public int schema;

	/** Item ids the Grand Exchange does not tax. */
	public List<Integer> taxExempt;

	public List<MmgMethod> methods;

	public List<MmgMethod> methods()
	{
		return methods == null ? Collections.emptyList() : methods;
	}

	public Set<Integer> taxExemptIds()
	{
		return taxExempt == null ? Collections.emptySet() : new HashSet<>(taxExempt);
	}
}
