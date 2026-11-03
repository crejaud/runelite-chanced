package com.crejaud.chanced;

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

	@ConfigSection(name = "Discord", description = "Post chances to a Discord webhook", position = 10)
	String discordSection = "discord";

	@ConfigSection(name = "PvP", description = "Chances from other players", position = 20)
	String pvpSection = "pvp";

	@ConfigSection(name = "Advanced", description = "Overrides and hazards", position = 30, closedByDefault = true)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "chatMessage",
		name = "Chat message",
		description = "Show a game chat message when you get chanced",
		position = 1
	)
	default boolean chatMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "messageTemplate",
		name = "Message",
		description = "Message text. Placeholders: %USERNAME% %SOURCE% %MAXHIT% %DAMAGE% %HP% %STYLE% %WORLD%",
		position = 2
	)
	default String messageTemplate()
	{
		return "%USERNAME% was chanced by %SOURCE%";
	}

	@ConfigItem(
		keyName = "webhookUrl",
		name = "Webhook URL",
		description = "Discord webhook URL(s), one per line. Leave blank to turn Discord off.",
		warning = WEBHOOK_WARNING,
		section = discordSection,
		position = 11
	)
	default String webhookUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "sendScreenshot",
		name = "Send screenshot",
		description = "Attach a screenshot of the game to the Discord message",
		section = discordSection,
		position = 12
	)
	default boolean sendScreenshot()
	{
		return true;
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

	@Range(max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "pvpSpecMargin",
		name = "Spec margin",
		description = "Added to a player's estimated max hit to cover special attacks",
		section = pvpSection,
		position = 23
	)
	default int pvpSpecMargin()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "pvpAssumedAmmo",
		name = "Assumed ammo strength",
		description = "Ranged strength assumed for ammo, which other players' gear does not show",
		section = pvpSection,
		position = 24
	)
	default int pvpAssumedAmmo()
	{
		return 122;
	}

	@ConfigItem(
		keyName = "pvpAssumedRing",
		name = "Assumed ring strength",
		description = "Melee strength assumed for a ring, which other players' gear does not show",
		section = pvpSection,
		position = 25
	)
	default int pvpAssumedRing()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "unknownHpThreshold",
		name = "Unknown source HP",
		description = "Alert on hits with no known source when your HP was at or below this. 0 turns it off.",
		section = advancedSection,
		position = 31
	)
	default int unknownHpThreshold()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "styleOverrides",
		name = "Style overrides",
		description = "One per line: npcId:animationId:style (melee, ranged, magic or special)",
		section = advancedSection,
		position = 32
	)
	default String styleOverrides()
	{
		return "";
	}

	@ConfigItem(
		keyName = "prayerPassThrough",
		name = "Prayer pass-through",
		description = "One per line: npcId:style:percent, the share of a max hit that gets through the right protection prayer",
		section = advancedSection,
		position = 33
	)
	default String prayerPassThrough()
	{
		return "";
	}

	@ConfigItem(
		keyName = "hazards",
		name = "Hazards",
		description = "One per line: graphic:id:max, projectile:id:max or region:id:max",
		section = advancedSection,
		position = 34
	)
	default String hazards()
	{
		return "";
	}
}
