package com.crejaud.chanced;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ChancedConfig.GROUP)
public interface ChancedConfig extends Config
{
	String GROUP = "chanced";
	String WEBHOOK_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";
	String TEST_CHAT = "testChat";
	String TEST_DISCORD = "testDiscord";

	String PLACEHOLDERS = "Placeholders: %USERNAME% %SOURCE% %MAXHIT% %DAMAGE% %HP% (before the hit) %HPLEFT% "
		+ "%STYLE% %PRAYER% %WORLD%";
	String DEFAULT_CHAT_TEMPLATE =
		"You were chanced by %SOURCE%: max hit %MAXHIT% (%STYLE%), hit %DAMAGE%, %HPLEFT% HP left.";
	String DEFAULT_DISCORD_TEMPLATE = "%USERNAME% was chanced by %SOURCE%: max hit %MAXHIT% (%STYLE%), "
		+ "hit %DAMAGE% with %HPLEFT% HP left. Prayer: %PRAYER%. World %WORLD%.";

	@ConfigSection(name = "Chat message", description = "Game chat message when you get chanced", position = 0)
	String chatSection = "chat";

	@ConfigSection(name = "Discord", description = "Post chances to a Discord webhook", position = 10)
	String discordSection = "discord";

	@ConfigSection(name = "PvP", description = "Chances from other players", position = 20)
	String pvpSection = "pvp";

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
}
