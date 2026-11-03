package com.crejaud.chanced;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
 * Attack style implied by each NPC attack animation and projectile. Built at runtime from the
 * names in RuneLite's gameval sources, downloaded once a day and cached on disk, so new game
 * content is picked up without a plugin update. A bundled snapshot covers the first run offline.
 * Ids missing from the table fall back to the NPC's wiki styles.
 */
@Slf4j
@Singleton
class AttackStyleTable
{
	static final String BUNDLED = "attack_styles.txt";
	static final String CACHE_FILE = "attack-styles-v1.txt";
	private static final Duration CACHE_TTL = Duration.ofHours(24);
	private static final HttpUrl GAMEVAL = HttpUrl.get(
		"https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/");

	private final OkHttpClient httpClient;
	private final ScheduledExecutorService executor;

	private volatile Map<Integer, AttackHint> animations = new HashMap<>();
	private volatile Map<Integer, AttackStyle> projectiles = new HashMap<>();

	@Inject
	AttackStyleTable(OkHttpClient httpClient, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.executor = executor;
	}

	/** Hint for an attack animation, or null when the animation is not a known attack. */
	AttackHint animation(int id)
	{
		return animations.get(id);
	}

	/** Style of a projectile, or null when its name does not say. */
	AttackStyle projectile(int id)
	{
		return projectiles.get(id);
	}

	int animationCount()
	{
		return animations.size();
	}

	int projectileCount()
	{
		return projectiles.size();
	}

	/** Loads the disk cache when fresh, otherwise downloads; the bundled snapshot fills in meanwhile. */
	void load(Filepath dir, boolean force, Consumer<String> done)
	{
		executor.execute(() ->
		{
			Filepath cache = dir.joinSegment(CACHE_FILE);
			if (!force && readCache(cache))
			{
				done.accept("Loaded " + animations.size() + " attack styles from cache");
				return;
			}
			if (animations.isEmpty())
			{
				loadBundled();
			}
			download(cache, done);
		});
	}

	void clear()
	{
		animations = new HashMap<>();
		projectiles = new HashMap<>();
	}

	void loadBundled()
	{
		try (InputStream in = AttackStyleTable.class.getResourceAsStream(BUNDLED))
		{
			if (in != null)
			{
				install(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		}
		catch (IOException e)
		{
			log.debug("Could not read bundled attack styles", e);
		}
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
				install(reader);
			}
			return !animations.isEmpty();
		}
		catch (IOException e)
		{
			log.debug("Could not read attack style cache", e);
			return false;
		}
	}

	private void download(Filepath cache, Consumer<String> done)
	{
		fetch("AnimationID.java", animSource -> fetch("SpotanimID.java", spotSource ->
		{
			Map<Integer, AttackHint> anims = new HashMap<>();
			Map<Integer, AttackStyle> projs = new HashMap<>();
			StyleTagger.animations(animSource, anims);
			StyleTagger.projectiles(spotSource, projs);
			if (anims.isEmpty())
			{
				done.accept("RuneLite gameval names had no attack animations; using the bundled table");
				return;
			}
			String text = serialize(anims, projs);
			try
			{
				install(new StringReader(text));
				cache.getParent().createDirectories();
				cache.write(text.getBytes(StandardCharsets.UTF_8));
			}
			catch (IOException e)
			{
				log.debug("Could not write attack style cache", e);
			}
			done.accept("Built " + anims.size() + " attack styles from RuneLite's gameval names");
		}, done), done);
	}

	private void fetch(String file, Consumer<String> onBody, Consumer<String> done)
	{
		Request request = new Request.Builder().url(Objects.requireNonNull(GAMEVAL.resolve(file))).build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				log.debug("Gameval download failed", e);
				done.accept("Could not download RuneLite gameval names; using the bundled attack styles");
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						done.accept("Gameval download returned HTTP " + response.code()
							+ "; using the bundled attack styles");
						return;
					}
					onBody.accept(body.string());
				}
			}
		});
	}

	/** Line format shared by the bundled snapshot and the cache: {@code a|p id STYLE [name]}. */
	static String serialize(Map<Integer, AttackHint> anims, Map<Integer, AttackStyle> projs)
	{
		StringBuilder sb = new StringBuilder("# Chanced attack styles: kind id style\n");
		anims.forEach((id, hint) -> sb.append("a ").append(id).append(' ').append(hint.name()).append('\n'));
		projs.forEach((id, style) -> sb.append("p ").append(id).append(' ').append(style.name()).append('\n'));
		return sb.toString();
	}

	private void install(Reader source) throws IOException
	{
		Map<Integer, AttackHint> anims = new HashMap<>();
		Map<Integer, AttackStyle> projs = new HashMap<>();
		BufferedReader reader = new BufferedReader(source);
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.charAt(0) == '#')
			{
				continue;
			}
			String[] f = line.split(" ");
			if (f.length < 3)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(f[1]);
				if ("a".equals(f[0]))
				{
					anims.put(id, AttackHint.valueOf(f[2]));
				}
				else if ("p".equals(f[0]))
				{
					projs.put(id, AttackStyle.valueOf(f[2]));
				}
			}
			catch (IllegalArgumentException e)
			{
				// unknown style name; skip the line
			}
		}
		animations = anims;
		projectiles = projs;
	}
}
