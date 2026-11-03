package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonObject;
import org.junit.Test;

public class ChancedEventTest
{
	private static final ChancedEvent SCURRIUS =
		new ChancedEvent("Corey", "Scurrius", "Magic", "Melee", 8, 0, 8, 8, 302, false);

	@Test
	public void formatsEveryPlaceholder()
	{
		assertEquals("Corey was chanced by Scurrius: 8 Magic 0 8 302",
			SCURRIUS.format("%USERNAME% was chanced by %SOURCE%: %MAXHIT% %STYLE% %DAMAGE% %HP% %WORLD%"));
	}

	@Test
	public void summary()
	{
		assertEquals("Max hit 8 (Magic), actually hit 0, at 8 HP.", SCURRIUS.summary());
	}

	@Test
	public void estimateAndUnknownMax()
	{
		ChancedEvent pvp = new ChancedEvent("Corey", "Pker", "Magic", "Magic", 30, 12, 20, 8, 330, true);
		assertEquals("~30", pvp.maxHitText());
		assertTrue(pvp.summary().endsWith("(PvP estimate)"));
		ChancedEvent unknown = new ChancedEvent("Corey", "Unknown", "Unknown", "None", -1, 3, 5, 2, 302, false);
		assertEquals("?", unknown.maxHitText());
	}

	@Test
	public void discordEmbed()
	{
		JsonObject payload = DiscordNotifier.payload(SCURRIUS, "%USERNAME% was chanced by %SOURCE%", true);
		JsonObject embed = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
		assertEquals("Chanced!", embed.get("title").getAsString());
		assertEquals("**Corey** was chanced by **Scurrius**\nMax hit **8** (Magic), actually hit **0**, at **8 HP**.",
			embed.get("description").getAsString());
		assertEquals(6, embed.getAsJsonArray("fields").size());
		assertEquals("attachment://chanced.png", embed.getAsJsonObject("image").get("url").getAsString());
	}
}
