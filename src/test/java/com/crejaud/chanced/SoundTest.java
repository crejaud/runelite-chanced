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
	private static final int TENTH = 10;
	private static final float TENTH_GAIN_DB = -20f;
	private static final float DB_TOLERANCE = 0.001f;

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

	@Test
	public void volumePercentMapsToFileGain()
	{
		// GIVEN volume percentages for a custom sound file
		// WHEN converting each to decibels of gain
		float full = ChancedPlugin.soundGainDb(FULL);
		float tenth = ChancedPlugin.soundGainDb(TENTH);
		float tooLoud = ChancedPlugin.soundGainDb(TOO_LOUD);

		// THEN 100% plays the file as recorded and each tenfold drop is 20 dB quieter
		assertEquals(0f, full, DB_TOLERANCE);
		assertEquals(TENTH_GAIN_DB, tenth, DB_TOLERANCE);
		assertEquals(0f, tooLoud, DB_TOLERANCE);
	}

	// no-gherkin
	@Test
	public void footerIconIsADirectImageLink()
	{
		assertEquals("https://raw.githubusercontent.com/crejaud/runelite-chanced/master/icon.png",
			ChancedConfig.DEFAULT_FOOTER_ICON);
	}
}
