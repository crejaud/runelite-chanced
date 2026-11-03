package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class ChancedEventTest
{
	private static final int WORLD = 302;
	private static final int SCURRIUS_MAGIC_MAX = 8;
	private static final int SCURRIUS_HP = 8;
	private static final ChancedEvent SCURRIUS = new ChancedEvent("Durial321", "Scurrius", "Magic",
		"Protect from Melee", SCURRIUS_MAGIC_MAX, 0, SCURRIUS_HP, SCURRIUS_HP, WORLD, false);

	private static final int GOBLIN_MAX = 2;
	private static final int GOBLIN_HIT = 1;
	private static final int PVP_ESTIMATE = 30;
	private static final int PVP_HIT = 12;
	private static final int PVP_HP = 20;
	private static final int UNKNOWN_HIT = 3;
	private static final int UNKNOWN_HP = 5;

	private static final int EMBED_FIELDS = 6;
	private static final int BASE_DELAY_MS = 2000;
	private static final int TENTH_RETRY = 10;
	private static final long MINUTE_MS = 60_000;
	private static final int SCREEN_WIDTH = 800;
	private static final int SCREEN_HEIGHT = 600;
	private static final int HALF = 50;
	private static final int FULL = 100;

	private static JsonObject embed(JsonObject payload)
	{
		return payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
	}

	@Test
	public void formatsEveryMacro()
	{
		// GIVEN a template with every macro
		String template = "%USERNAME% %SOURCE% %MAXHIT% %DAMAGE% %HP% %HPLEFT% %STYLE% %PRAYER% %WORLD%";

		// WHEN filling it from the Scurrius chance
		String text = SCURRIUS.format(template);

		// THEN every macro is replaced
		assertEquals("Durial321 Scurrius " + SCURRIUS_MAGIC_MAX + " 0 " + SCURRIUS_HP + " " + SCURRIUS_HP
			+ " Magic Protect from Melee " + WORLD, text);
	}

	@Test
	public void defaultChatMessage()
	{
		// GIVEN a goblin chance
		ChancedEvent goblin = new ChancedEvent("Durial321", "Goblin", "Melee", "None", GOBLIN_MAX, GOBLIN_HIT,
			GOBLIN_MAX, GOBLIN_MAX - GOBLIN_HIT, WORLD, false);

		// WHEN filling the default chat template
		String text = goblin.format(ChancedConfig.DEFAULT_CHAT_TEMPLATE);

		// THEN it reads as a message to the player
		assertEquals("You were chanced by Goblin: max hit " + GOBLIN_MAX + " (Melee), hit " + GOBLIN_HIT + ", "
			+ (GOBLIN_MAX - GOBLIN_HIT) + " HP left.", text);
	}

	@Test
	public void estimateAndUnknownMax()
	{
		// GIVEN a PvP estimate and a chance with no known max
		ChancedEvent pvp = new ChancedEvent("Durial321", "Pker", "Magic", "Protect from Magic", PVP_ESTIMATE,
			PVP_HIT, PVP_HP, PVP_HP - PVP_HIT, WORLD, true);
		ChancedEvent unknown = new ChancedEvent("Durial321", "Unknown", "Unknown", "None", -1, UNKNOWN_HIT,
			UNKNOWN_HP, UNKNOWN_HP - UNKNOWN_HIT, WORLD, false);

		// WHEN showing their max hits
		String estimate = pvp.maxHitText();
		String missing = unknown.maxHitText();

		// THEN the estimate gets a ~ and the unknown max a ?
		assertEquals("~" + PVP_ESTIMATE, estimate);
		assertEquals("?", missing);
	}

	@Test
	public void discordEmbed()
	{
		// GIVEN the default Discord template with a screenshot and footer
		String template = ChancedConfig.DEFAULT_DISCORD_TEMPLATE;

		// WHEN building the webhook payload
		JsonObject embed = embed(DiscordNotifier.payload(SCURRIUS, template, true, "Powered by Chanced",
			ChancedConfig.DEFAULT_FOOTER_ICON));

		// THEN the embed has the bolded headline, the detail fields, the screenshot and the footer
		assertEquals("Chanced!", embed.get("title").getAsString());
		assertEquals("**Durial321** was chanced by **Scurrius**", embed.get("description").getAsString());
		assertEquals(EMBED_FIELDS, embed.getAsJsonArray("fields").size());
		assertEquals("attachment://chanced.png", embed.getAsJsonObject("image").get("url").getAsString());
		assertEquals("Powered by Chanced", embed.getAsJsonObject("footer").get("text").getAsString());
		assertEquals(ChancedConfig.DEFAULT_FOOTER_ICON, embed.getAsJsonObject("footer").get("icon_url").getAsString());
	}

	@Test
	public void emptyFooterTextSendsNoFooter()
	{
		// GIVEN blank footer text and no screenshot
		String footerText = " ";

		// WHEN building the webhook payload
		JsonObject embed = embed(DiscordNotifier.payload(SCURRIUS, "x", false, footerText,
			ChancedConfig.DEFAULT_FOOTER_ICON));

		// THEN there is no footer and no image
		assertFalse(embed.has("footer"));
		assertFalse(embed.has("image"));
	}

	@Test
	public void retryBackoffDoublesAndCaps()
	{
		// GIVEN the default base retry delay
		int base = BASE_DELAY_MS;

		// WHEN computing the delay before the first, second and tenth retries
		long first = DiscordNotifier.retryDelayMs(base, 1);
		long second = DiscordNotifier.retryDelayMs(base, 1 + 1);
		long tenth = DiscordNotifier.retryDelayMs(base, TENTH_RETRY);

		// THEN it starts at the base, doubles, and caps at a minute
		assertEquals(BASE_DELAY_MS, first);
		assertEquals(BASE_DELAY_MS + BASE_DELAY_MS, second);
		assertEquals(MINUTE_MS, tenth);
	}

	@Test
	public void screenshotScale()
	{
		// GIVEN an 800x600 screenshot
		BufferedImage image = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);

		// WHEN scaling it to 100% and to 50%
		BufferedImage full = DiscordNotifier.scale(image, FULL);
		BufferedImage half = DiscordNotifier.scale(image, HALF);

		// THEN 100% leaves it untouched and 50% halves each side
		assertSame(image, full);
		assertEquals(SCREEN_WIDTH * HALF / FULL, half.getWidth());
		assertEquals(SCREEN_HEIGHT * HALF / FULL, half.getHeight());
	}
}
