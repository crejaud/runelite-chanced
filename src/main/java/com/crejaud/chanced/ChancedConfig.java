package com.crejaud.chanced;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ChancedConfig.GROUP)
public interface ChancedConfig extends Config
{
	String GROUP = "chanced";
	String WEBHOOK_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";
	String TEST_CHAT = "testChat";
	String TEST_DISCORD = "testDiscord";
	String TEST_SOUND = "testSound";
	String OPEN_SOUND_LIST = "openSoundList";
	String SOUND_LIST_URL = "https://oldschool.runescape.wiki/w/List_of_in-game_sound_IDs";
	/** snp_warning_ping: a short ping that stands out from the hitsplat and prayer sounds of a fight. */
	int DEFAULT_SOUND_ID = 4832;

	String PLACEHOLDERS = "Macros: %USERNAME% %SOURCE% %MAXHIT% %DAMAGE% %HP% (before the hit) %HPLEFT% "
		+ "%STYLE% %PRAYER% %WORLD%";
	String DEFAULT_CHAT_TEMPLATE =
		"You were chanced by %SOURCE%: max hit %MAXHIT% (%STYLE%), hit %DAMAGE%, %HPLEFT% HP left.";
	/** The embed's fields already show max hit, actual hit, HP, style, prayer and world. */
	String DEFAULT_DISCORD_TEMPLATE = "%USERNAME% was chanced by %SOURCE%";

	@ConfigSection(name = "Chat message", description = "Game chat message when you get chanced", position = 0)
	String chatSection = "chat";

	@ConfigSection(name = "Sound", description = "Sound effect when you get chanced", position = 5)
	String soundSection = "sound";

	@ConfigSection(name = "Discord", description = "Post chances to a Discord webhook", position = 10)
	String discordSection = "discord";

	@ConfigSection(name = "PvP", description = "Chances from other players", position = 20)
	String pvpSection = "pvp";

	@ConfigSection(name = "Leagues", description = "Seasonal worlds such as Leagues", position = 30)
	String leaguesSection = "leagues";

	@ConfigSection(name = "Advanced", description = "Webhook delivery and embed details", position = 40,
		closedByDefault = true)
	String advancedSection = "advanced";

	/** Direct image link: Discord does not follow github.com's redirect to it. */
	String DEFAULT_FOOTER_ICON = "https://raw.githubusercontent.com/crejaud/runelite-chanced/master/icon.png";

	@ConfigItem(
		keyName = "chatMessage",
		name = "Enabled",
		description = "Show a game chat message when you get chanced",
		section = chatSection,
		position = 1
	)
	default boolean chatMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatTemplate",
		name = "Message",
		description = "Chat message text. " + PLACEHOLDERS,
		section = chatSection,
		position = 2
	)
	default String chatTemplate()
	{
		return DEFAULT_CHAT_TEMPLATE;
	}

	@ConfigItem(
		keyName = "chatColor",
		name = "Text color",
		description = "Color of the chat message",
		section = chatSection,
		position = 3
	)
	default Color chatColor()
	{
		return new Color(0xEF1020);
	}

	@ConfigItem(
		keyName = TEST_CHAT,
		name = "Send test",
		description = "Tick to show a test chat message (Scurrius); it unticks itself",
		section = chatSection,
		position = 4
	)
	default boolean testChat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "soundEnabled",
		name = "Enabled",
		description = "Play a sound effect when you get chanced",
		section = soundSection,
		position = 6
	)
	default boolean soundEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundId",
		name = "Sound ID",
		description = "Game sound effect to play. Tick Sound list below for every ID.<br>"
			+ "4832 warning ping (default), 3813 town crier bell, 1474 dice roll, 3129 heartbeat,<br>"
			+ "3879 God Wars gong, 2918 lucky lightning, 2114 coins jingle",
		section = soundSection,
		position = 7
	)
	default int soundId()
	{
		return DEFAULT_SOUND_ID;
	}

	@ConfigItem(
		keyName = OPEN_SOUND_LIST,
		name = "Sound list",
		description = "Tick to open the OSRS Wiki's list of sound IDs in your browser; it unticks itself",
		section = soundSection,
		position = 10
	)
	default boolean openSoundList()
	{
		return false;
	}

	@Units(Units.PERCENT)
	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Volume",
		description = "Volume of the sound, independent of the game's sound effect volume",
		section = soundSection,
		position = 8
	)
	default int soundVolume()
	{
		return 100;
	}

	@ConfigItem(
		keyName = TEST_SOUND,
		name = "Send test",
		description = "Tick to play the sound; it unticks itself",
		section = soundSection,
		position = 9
	)
	default boolean testSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "discordEnabled",
		name = "Enabled",
		description = "Post chances to your Discord webhook",
		warning = WEBHOOK_WARNING,
		section = discordSection,
		position = 11
	)
	default boolean discordEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "webhookUrl",
		name = "Webhook URL",
		description = "Discord webhook URL(s), one per line",
		section = discordSection,
		position = 12
	)
	default String webhookUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "discordTemplate",
		name = "Message",
		description = "Discord message text. " + PLACEHOLDERS,
		section = discordSection,
		position = 13
	)
	default String discordTemplate()
	{
		return DEFAULT_DISCORD_TEMPLATE;
	}

	@ConfigItem(
		keyName = "sendScreenshot",
		name = "Send screenshot",
		description = "Attach a screenshot of the game to the Discord message",
		section = discordSection,
		position = 14
	)
	default boolean sendScreenshot()
	{
		return true;
	}

	@ConfigItem(
		keyName = TEST_DISCORD,
		name = "Send test",
		description = "Tick to post a test message (Scurrius) to the webhook; it unticks itself",
		section = discordSection,
		position = 15
	)
	default boolean testDiscord()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pvpEnabled",
		name = "Player attacks",
		description = "Alert on chances from other players (max hit is a worst-case estimate from their visible gear)",
		section = pvpSection,
		position = 21
	)
	default boolean pvpEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pvpAnywhere",
		name = "Outside PvP areas",
		description = "Also check player attacks outside the Wilderness, PvP worlds and Deadman worlds",
		section = pvpSection,
		position = 22
	)
	default boolean pvpAnywhere()
	{
		return false;
	}

	@ConfigItem(
		keyName = "leaguesEnabled",
		name = "Alerts on Leagues worlds",
		description = "Alert on chances while playing on seasonal worlds such as Leagues",
		section = leaguesSection,
		position = 31
	)
	default boolean leaguesEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "maxRetries",
		name = "Webhook max retries",
		description = "How many times to retry a failed webhook message. 0 means no retries",
		section = advancedSection,
		position = 41
	)
	default int maxRetries()
	{
		return 3;
	}

	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "baseRetryDelay",
		name = "Webhook retry base delay",
		description = "Wait before the first retry; each later retry waits twice as long",
		section = advancedSection,
		position = 42
	)
	default int baseRetryDelay()
	{
		return 2000;
	}

	@Units(Units.SECONDS)
	@Range(min = 1)
	@ConfigItem(
		keyName = "networkTimeout",
		name = "Base network timeout",
		description = "Seconds allowed to connect to and read from the webhook",
		section = advancedSection,
		position = 43
	)
	default int networkTimeout()
	{
		return 15;
	}

	@Units(Units.SECONDS)
	@Range(min = 1)
	@ConfigItem(
		keyName = "imageWriteTimeout",
		name = "Image upload timeout",
		description = "Seconds allowed for uploading the screenshot",
		section = advancedSection,
		position = 44
	)
	default int imageWriteTimeout()
	{
		return 30;
	}

	@Units(Units.PERCENT)
	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "screenshotScale",
		name = "Screenshot scale",
		description = "Resizes the screenshot in each dimension by this percentage. Lower values upload faster",
		section = advancedSection,
		position = 45
	)
	default int screenshotScale()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "embedFooterText",
		name = "Embed footer text",
		description = "Footer text of the Discord embed. Leave empty for no footer",
		section = advancedSection,
		position = 46
	)
	default String embedFooterText()
	{
		return "Powered by Chanced";
	}

	@ConfigItem(
		keyName = "embedFooterIcon",
		name = "Embed footer icon",
		description = "URL of the footer icon. Needs footer text",
		section = advancedSection,
		position = 47
	)
	default String embedFooterIcon()
	{
		return DEFAULT_FOOTER_ICON;
	}
}
