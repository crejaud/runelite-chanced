package com.crejaud.chanced;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Attack style implied by each NPC attack animation and projectile, from the bundled
 * {@code attack_styles.txt} snapshot (generated from RuneLite's gameval names by
 * {@code scripts/generate_attack_styles.py}). Ids missing from it fall back to the NPC's
 * wiki attack styles.
 */
@Slf4j
@Singleton
class AttackStyleTable
{
	static final String RESOURCE = "attack_styles.txt";

	private final Map<Integer, AttackHint> animations = new HashMap<>();
	private final Map<Integer, AttackStyle> projectiles = new HashMap<>();

	AttackStyleTable()
	{
		try (InputStream in = AttackStyleTable.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Chanced: {} is missing", RESOURCE);
				return;
			}
			load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		}
		catch (IOException e)
		{
			log.warn("Chanced: could not read {}", RESOURCE, e);
		}
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

	private void load(BufferedReader reader) throws IOException
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
					animations.put(id, AttackHint.valueOf(f[2]));
				}
				else if ("p".equals(f[0]))
				{
					projectiles.put(id, AttackStyle.valueOf(f[2]));
				}
			}
			catch (IllegalArgumentException e)
			{
				// unknown style name; skip the line
			}
		}
	}
}
