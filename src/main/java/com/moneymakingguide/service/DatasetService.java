package com.moneymakingguide.service;

import com.google.gson.Gson;
import com.moneymakingguide.data.MmgDataset;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Loads the money making guide dataset.
 *
 * <p>The wiki's guides change far faster than Plugin Hub releases ship, so the dataset is
 * fetched from a published URL rather than read out of the jar. The bundled copy is only
 * a floor: it guarantees the panel has something to show on first run and offline.
 */
@Slf4j
@Singleton
public class DatasetService
{
	public static final String USER_AGENT =
		"runelite-money-making-guide/1.0 (github.com/simon/osrs-money-making-guide)";

	private static final String BUNDLED = "/com/moneymakingguide/mmg-data.json";
	private static final Duration CACHE_TTL = Duration.ofHours(12);

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final File cacheFile = new File(RuneLite.CACHE_DIR, "money-making-guide.json");

	@Getter
	private volatile MmgDataset dataset;

	@Getter
	private volatile String source = "none";

	@Getter
	private volatile String lastError;

	private volatile boolean inFlight;

	@Inject
	DatasetService(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	/**
	 * Populates {@link #getDataset()} from disk or the jar so the panel can render
	 * immediately, without waiting on the network.
	 */
	public void loadLocal()
	{
		if (cacheFile.isFile())
		{
			try (Reader r = Files.newBufferedReader(cacheFile.toPath(), StandardCharsets.UTF_8))
			{
				MmgDataset d = gson.fromJson(r, MmgDataset.class);
				if (d != null && !d.methods().isEmpty())
				{
					dataset = d;
					source = "cache";
					return;
				}
			}
			catch (Exception e)
			{
				log.warn("could not read cached dataset, falling back to bundled", e);
			}
		}

		// getResourceAsStream, not getResource: the Plugin Hub classloader does not
		// support URL-based resource access.
		try (InputStream in = getClass().getResourceAsStream(BUNDLED))
		{
			if (in == null)
			{
				lastError = "bundled dataset missing";
				return;
			}

			dataset = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), MmgDataset.class);
			source = "bundled";
		}
		catch (Exception e)
		{
			lastError = e.getMessage();
			log.warn("could not read bundled dataset", e);
		}
	}

	/** True when the on-disk copy is old enough to be worth refetching. */
	public boolean isStale()
	{
		if (!cacheFile.isFile())
		{
			return true;
		}

		Instant modified = Instant.ofEpochMilli(cacheFile.lastModified());
		return Duration.between(modified, Instant.now()).compareTo(CACHE_TTL) > 0;
	}

	public void refresh(String url, Runnable onDone)
	{
		if (inFlight || url == null || url.trim().isEmpty())
		{
			onDone.run();
			return;
		}

		inFlight = true;
		Request request = new Request.Builder()
			.url(url.trim())
			.header("User-Agent", USER_AGENT)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				inFlight = false;
				lastError = e.getMessage();
				log.warn("could not fetch dataset", e);
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

					byte[] body = r.body().bytes();
					MmgDataset parsed = gson.fromJson(
						new String(body, StandardCharsets.UTF_8), MmgDataset.class);

					if (parsed == null || parsed.methods().isEmpty())
					{
						lastError = "dataset was empty";
						return;
					}

					dataset = parsed;
					source = "live";
					lastError = null;
					writeCache(body);
				}
				catch (Exception e)
				{
					lastError = e.getMessage();
					log.warn("could not parse dataset", e);
				}
				finally
				{
					inFlight = false;
					onDone.run();
				}
			}
		});
	}

	private void writeCache(byte[] body) throws IOException
	{
		File tmp = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
		Files.createDirectories(cacheFile.getParentFile().toPath());
		Files.write(tmp.toPath(), body);
		Files.move(tmp.toPath(), cacheFile.toPath(),
			StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
