package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import net.runelite.api.SoundEffectVolume;
import org.junit.Test;

public class SoundTest
{
	private static final int FULL = 100;
	private static final int HALF = 50;
	private static final int TOO_LOUD = 250;
	private static final int HALF_VOLUME = SoundEffectVolume.HIGH * HALF / FULL;

	@Test
	public void volumePercentMapsToClientScale()
	{
		// GIVEN volume percentages from 1% up to out of range
		// WHEN converting each to the client's sound effect volume
		int full = ChancedPlugin.soundVolume(FULL);
		int half = ChancedPlugin.soundVolume(HALF);
		int quietest = ChancedPlugin.soundVolume(1);
		int tooLoud = ChancedPlugin.soundVolume(TOO_LOUD);

		// THEN 100% is the loudest, and out of range values are capped there
		assertEquals(SoundEffectVolume.HIGH, full);
		assertEquals(HALF_VOLUME, half);
		assertEquals(1, quietest);
		assertEquals(SoundEffectVolume.HIGH, tooLoud);
	}

	// no-gherkin
	@Test
	public void footerIconIsADirectImageLink()
	{
		assertEquals("https://raw.githubusercontent.com/crejaud/runelite-chanced/master/icon.png",
			ChancedConfig.DEFAULT_FOOTER_ICON);
	}
}
