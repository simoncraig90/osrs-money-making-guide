package com.moneymakingguide.service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.moneymakingguide.PriceMode;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Live Grand Exchange prices from the wiki's real-time API.
 *
 * <p>The API reports the last instant-buy ({@code high}) and instant-sell ({@code low})
 * for every tradeable item. Those are not the same number, and which one applies depends
 * on which side of the trade you are on -- you pay {@code high} for an input and receive
 * {@code low} for an output. Collapsing them to a single "price", as a naive calculator
 * does, quietly overstates profit by the spread on every line.
 */
@Slf4j
@Singleton
public class PriceService
{
	private static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";

	/** The API asks callers to identify themselves and to poll no faster than this. */
	private static final Duration MIN_REFRESH = Duration.ofMinutes(5);

	private final OkHttpClient okHttpClient;
	private final ItemManager itemManager;
	private final Gson gson;

	private volatile Map<Integer, Entry> prices = Collections.emptyMap();

	/**
	 * ItemManager's prices, snapshotted on the client thread.
	 *
	 * <p>{@code ItemManager.getItemPrice} reaches into the item composition cache and
	 * asserts it is on the client thread, so it can never be called from the panel.
	 * These are read once, up front, and served from the map thereafter.
	 */
	private volatile Map<Integer, Integer> guidePrices = Collections.emptyMap();

	@Getter
	private volatile Instant lastUpdate;

	@Getter
	private volatile String lastError;

	private volatile boolean inFlight;

	@Inject
	PriceService(OkHttpClient okHttpClient, ItemManager itemManager, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.itemManager = itemManager;
		this.gson = gson;
	}

	/** Fetches prices unless a fetch is running or the cache is still fresh. */
	public void refresh(boolean force, Runnable onDone)
	{
		if (inFlight)
		{
			return;
		}

		Instant last = lastUpdate;
		if (!force && last != null && Duration.between(last, Instant.now()).compareTo(MIN_REFRESH) < 0)
		{
			onDone.run();
			return;
		}

		inFlight = true;
		Request request = new Request.Builder()
			.url(LATEST_URL)
			.header("User-Agent", DatasetService.USER_AGENT)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				inFlight = false;
				lastError = e.getMessage();
				log.warn("could not fetch live prices", e);
				onDone.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						lastError = "HTTP " + r.code();
						return;
					}

					LatestResponse parsed = gson.fromJson(
						new InputStreamReader(r.body().byteStream(), StandardCharsets.UTF_8),
						LatestResponse.class);

					if (parsed != null && parsed.data != null)
					{
						prices = parsed.data;
						lastUpdate = Instant.now();
						lastError = null;
						log.debug("loaded {} live prices", parsed.data.size());
					}
				}
				catch (Exception e)
				{
					lastError = e.getMessage();
					log.warn("could not parse live prices", e);
				}
				finally
				{
					inFlight = false;
					onDone.run();
				}
			}
		});
	}

	public boolean hasLivePrices()
	{
		return !prices.isEmpty();
	}

	/**
	 * Snapshots ItemManager's prices for the given items. Must run on the client thread.
	 */
	public void warmGuidePrices(Collection<Integer> itemIds)
	{
		Map<Integer, Integer> snapshot = new HashMap<>(itemIds.size());

		for (Integer id : itemIds)
		{
			try
			{
				int price = itemManager.getItemPrice(id);
				if (price > 0)
				{
					snapshot.put(id, price);
				}
			}
			catch (RuntimeException e)
			{
				// An id the client's cache does not know about, usually because the
				// wiki listed an item this revision has since removed. One bad id must
				// not abandon the other 1300.
				log.debug("no composition for item {}", id);
			}
		}

		guidePrices = snapshot;
		log.debug("warmed {} guide prices", snapshot.size());
	}

	/** What it costs to obtain one of this item. Safe to call from any thread. */
	public int buyPrice(int itemId, PriceMode mode)
	{
		Entry e = prices.get(itemId);

		if (mode == PriceMode.WIKI_GUIDE || e == null)
		{
			return guidePrices.getOrDefault(itemId, 0);
		}

		if (mode == PriceMode.MID)
		{
			return e.mid();
		}

		// Pay the instant-buy price; fall back to the other side for illiquid items.
		return e.high > 0 ? e.high : e.low;
	}

	/** What one of this item fetches, before Grand Exchange tax. Safe from any thread. */
	public int sellPrice(int itemId, PriceMode mode)
	{
		Entry e = prices.get(itemId);

		if (mode == PriceMode.WIKI_GUIDE || e == null)
		{
			return guidePrices.getOrDefault(itemId, 0);
		}

		if (mode == PriceMode.MID)
		{
			return e.mid();
		}

		return e.low > 0 ? e.low : e.high;
	}

	private static class LatestResponse
	{
		Map<Integer, Entry> data;
	}

	private static class Entry
	{
		@SerializedName("high")
		int high;

		@SerializedName("low")
		int low;

		int mid()
		{
			if (high <= 0)
			{
				return low;
			}
			if (low <= 0)
			{
				return high;
			}
			return (high + low) / 2;
		}
	}
}
