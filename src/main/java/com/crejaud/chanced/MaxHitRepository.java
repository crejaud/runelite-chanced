package com.crejaud.chanced;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Max hits per NPC id from the OSRS Wiki {@code infobox_monster} bucket. Downloaded once,
 * kept in memory and cached on disk for a day.
 */
@Slf4j
@Singleton
class MaxHitRepository
{
	static final String CACHE_FILE = "max-hits-v2.json";
	private static final Duration CACHE_TTL = Duration.ofHours(24);
	private static final int PAGE_SIZE = 5000;
	private static final HttpUrl WIKI_API = HttpUrl.get("https://oldschool.runescape.wiki/api.php");

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;

	private volatile Map<Integer, MonsterInfo> monsters = Collections.emptyMap();

	@Inject
	MaxHitRepository(OkHttpClient httpClient, Gson gson, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.executor = executor;
	}

	/** Max hits for an NPC id, or null when the wiki has none (or data is not loaded yet). */
	MonsterInfo get(int npcId)
	{
		return monsters.get(npcId);
	}

	int size()
	{
		return monsters.size();
	}

	void clear()
	{
		monsters = Collections.emptyMap();
	}

	/**
	 * Loads from the disk cache when it is fresh, otherwise downloads. Runs off the client
	 * thread; {@code done} receives a short status message.
	 */
	void load(Filepath dir, boolean force, Consumer<String> done)
	{
		executor.execute(() ->
		{
			Filepath cache = dir.joinSegment(CACHE_FILE);
			if (!force && readCache(cache))
			{
				done.accept("Loaded " + monsters.size() + " max hits from cache");
				return;
			}
			download(cache, new JsonArray(), 0, done);
		});
	}

	private boolean readCache(Filepath cache)
	{
		try
		{
			if (!cache.exists()
				|| cache.getLastModifiedTime().toInstant().isBefore(Instant.now().minus(CACHE_TTL)))
			{
				return false;
			}
			try (Reader reader = cache.openReader())
			{
				JsonObject response = gson.fromJson(reader, JsonObject.class);
				Map<Integer, MonsterInfo> parsed = WikiMonsterParser.parseResponse(response);
				if (parsed.isEmpty())
				{
					return false;
				}
				monsters = parsed;
				return true;
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Could not read max hit cache", e);
			return false;
		}
	}

	private void download(Filepath cache, JsonArray rows, int offset, Consumer<String> done)
	{
		HttpUrl url = WIKI_API.newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", "bucket('infobox_monster')"
				+ ".select('id','name','max_hit','attack_style')"
				+ ".limit(" + PAGE_SIZE + ").offset(" + offset + ").run()")
			.build();
		Request request = new Request.Builder().url(url).build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				log.debug("Max hit download failed", e);
				done.accept("Could not download max hits from the OSRS Wiki");
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						done.accept("OSRS Wiki returned HTTP " + response.code());
						return;
					}
					JsonObject page = gson.fromJson(body.charStream(), JsonObject.class);
					JsonArray pageRows = page.getAsJsonArray("bucket");
					if (pageRows == null)
					{
						done.accept("OSRS Wiki returned no monster data");
						return;
					}
					rows.addAll(pageRows);
					if (pageRows.size() >= PAGE_SIZE)
					{
						download(cache, rows, offset + PAGE_SIZE, done);
						return;
					}
					finish(cache, rows, done);
				}
				catch (RuntimeException e)
				{
					log.debug("Bad max hit response", e);
					done.accept("Could not read OSRS Wiki monster data");
				}
			}
		});
	}

	private void finish(Filepath cache, JsonArray rows, Consumer<String> done)
	{
		JsonObject all = new JsonObject();
		all.add("bucket", rows);
		Map<Integer, MonsterInfo> parsed = new HashMap<>();
		WikiMonsterParser.parseRows(rows, parsed);
		monsters = parsed;
		try
		{
			cache.getParent().createDirectories();
			cache.write(gson.toJson(all).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.debug("Could not write max hit cache", e);
		}
		done.accept("Downloaded " + parsed.size() + " max hits from the OSRS Wiki");
	}
}
