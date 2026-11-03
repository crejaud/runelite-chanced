package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import net.runelite.api.SoundEffectVolume;
import org.junit.Test;

public class SoundTest
{
	@Test
	public void volumePercentMapsToClientScale()
	{
		assertEquals(SoundEffectVolume.HIGH, ChancedPlugin.soundVolume(100));
		assertEquals(63, ChancedPlugin.soundVolume(50));
		assertEquals(1, ChancedPlugin.soundVolume(1));
		assertEquals(SoundEffectVolume.HIGH, ChancedPlugin.soundVolume(250));
	}

	@Test
	public void footerIconIsADirectImageLink()
	{
		assertEquals("https://raw.githubusercontent.com/crejaud/runelite-chanced/master/icon.png",
			ChancedConfig.DEFAULT_FOOTER_ICON);
	}
}
