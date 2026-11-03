package com.crejaud.chanced;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Attack style implied by each NPC attack animation and projectile, read from the bundled
 * {@code attack_styles.txt} (generated from RuneLite's gameval names, refreshed weekly by CI).
 * Ids missing from it fall back to the NPC's wiki styles.
 */
@Slf4j
final class AttackStyleTable
{
	static final String RESOURCE = "attack_styles.txt";

	private static final Map<Integer, AttackHint> ANIMATIONS = new HashMap<>();
	private static final Map<Integer, AttackStyle> PROJECTILES = new HashMap<>();

	static
	{
		try (InputStream in = AttackStyleTable.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Chanced: {} is missing", RESOURCE);
			}
			else
			{
				load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
			}
		}
		catch (IOException e)
		{
			log.warn("Chanced: could not read {}", RESOURCE, e);
		}
	}

	private AttackStyleTable()
	{
	}

	/** Hint for an attack animation, or null when the animation is not an attack. */
	static AttackHint animation(int id)
	{
		return ANIMATIONS.get(id);
	}

	/** Style of a projectile, or null when its name does not say. */
	static AttackStyle projectile(int id)
	{
		return PROJECTILES.get(id);
	}

	static int animationCount()
	{
		return ANIMATIONS.size();
	}

	static int projectileCount()
	{
		return PROJECTILES.size();
	}

	private static void load(BufferedReader reader) throws IOException
	{
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
					ANIMATIONS.put(id, AttackHint.valueOf(f[2]));
				}
				else if ("p".equals(f[0]))
				{
					PROJECTILES.put(id, AttackStyle.valueOf(f[2]));
				}
			}
			catch (IllegalArgumentException e)
			{
				// a newer generator wrote a style this build does not know; skip the line
			}
		}
	}
}
