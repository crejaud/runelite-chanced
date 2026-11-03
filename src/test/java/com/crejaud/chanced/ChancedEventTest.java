package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import com.google.gson.JsonObject;
import org.junit.Test;

public class ChancedEventTest
{
	private static final ChancedEvent SCURRIUS =
		new ChancedEvent("Durial321", "Scurrius", "Magic", "Protect from Melee", 8, 0, 8, 8, 302, false);

	@Test
	public void formatsEveryPlaceholder()
	{
		assertEquals("Durial321 Scurrius 8 0 8 8 Magic Protect from Melee 302",
			SCURRIUS.format("%USERNAME% %SOURCE% %MAXHIT% %DAMAGE% %HP% %HPLEFT% %STYLE% %PRAYER% %WORLD%"));
	}

	@Test
	public void defaultChatMessage()
	{
		ChancedEvent goblin = new ChancedEvent("Durial321", "Goblin", "Melee", "None", 2, 1, 2, 1, 302, false);
		assertEquals("You were chanced by Goblin: max hit 2 (Melee), hit 1, 1 HP left.",
			goblin.format(ChancedConfig.DEFAULT_CHAT_TEMPLATE));
	}

	@Test
	public void estimateAndUnknownMax()
	{
		ChancedEvent pvp = new ChancedEvent("Durial321", "Pker", "Magic", "Protect from Magic", 30, 12, 20, 8, 330, true);
		assertEquals("~30", pvp.maxHitText());
		ChancedEvent unknown = new ChancedEvent("Durial321", "Unknown", "Unknown", "None", -1, 3, 5, 2, 302, false);
		assertEquals("?", unknown.maxHitText());
	}

	@Test
	public void discordEmbed()
	{
		JsonObject payload = DiscordNotifier.payload(SCURRIUS, ChancedConfig.DEFAULT_DISCORD_TEMPLATE, true);
		JsonObject embed = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
		assertEquals("Chanced!", embed.get("title").getAsString());
		assertEquals("**Durial321** was chanced by **Scurrius**: max hit **8** (Magic), hit **0** with 8 HP left. "
			+ "Prayer: Protect from Melee. World 302.", embed.get("description").getAsString());
		assertEquals(6, embed.getAsJsonArray("fields").size());
		assertEquals("attachment://chanced.png", embed.getAsJsonObject("image").get("url").getAsString());
	}
}
