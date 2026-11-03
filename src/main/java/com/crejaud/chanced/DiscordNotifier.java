package com.crejaud.chanced;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Posts a chance to the user's Discord webhook(s), with an optional screenshot. */
@Slf4j
@Singleton
class DiscordNotifier
{
	private static final MediaType PNG = MediaType.get("image/png");
	private static final String SCREENSHOT = "chanced.png";
	private static final int EMBED_COLOR = 0xC0392B;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;

	@Inject
	DiscordNotifier(OkHttpClient httpClient, Gson gson, DrawManager drawManager, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.drawManager = drawManager;
		this.executor = executor;
	}

	void send(ChancedEvent event, String template, String webhooks, boolean screenshot)
	{
		String payload = gson.toJson(payload(event, template, screenshot));
		if (!screenshot)
		{
			post(webhooks, payload, null);
			return;
		}
		drawManager.requestNextFrameListener(image -> executor.execute(() ->
		{
			byte[] png = null;
			try
			{
				BufferedImage buffered = ImageUtil.bufferedImageFromImage(image);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(buffered, "png", out);
				png = out.toByteArray();
			}
			catch (IOException e)
			{
				log.debug("Could not encode screenshot", e);
			}
			post(webhooks, payload, png);
		}));
	}

	static JsonObject payload(ChancedEvent event, String template, boolean screenshot)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("title", "Chanced!");
		embed.addProperty("color", EMBED_COLOR);
		String headline = event.format(template)
			.replace(event.getUsername(), "**" + event.getUsername() + "**")
			.replace(event.getSource(), "**" + event.getSource() + "**");
		embed.addProperty("description", headline + "\nMax hit **" + event.maxHitText() + "** ("
			+ event.getStyle() + "), actually hit **" + event.getDamage() + "**, at **" + event.getHpBefore()
			+ " HP**." + (event.isEstimate() ? "\n_Max hit is a PvP estimate._" : ""));

		JsonArray fields = new JsonArray();
		fields.add(field("Max hit", event.maxHitText()));
		fields.add(field("Actual hit", String.valueOf(event.getDamage())));
		fields.add(field("HP", event.getHpBefore() + " → " + event.getHpAfter()));
		fields.add(field("Attack style", event.getStyle()));
		fields.add(field("Your prayer", event.getPrayer()));
		fields.add(field("World", String.valueOf(event.getWorld())));
		embed.add("fields", fields);

		if (screenshot)
		{
			JsonObject image = new JsonObject();
			image.addProperty("url", "attachment://" + SCREENSHOT);
			embed.add("image", image);
		}

		JsonArray embeds = new JsonArray();
		embeds.add(embed);
		JsonObject payload = new JsonObject();
		payload.add("embeds", embeds);
		return payload;
	}

	private static JsonObject field(String name, String value)
	{
		JsonObject f = new JsonObject();
		f.addProperty("name", name);
		f.addProperty("value", value);
		f.addProperty("inline", true);
		return f;
	}

	private void post(String webhooks, String payload, byte[] png)
	{
		for (String line : webhooks.split("[\\r\\n]+"))
		{
			HttpUrl url = HttpUrl.parse(line.trim());
			if (url == null)
			{
				continue;
			}
			MultipartBody.Builder body = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", payload);
			if (png != null)
			{
				body.addFormDataPart("file", SCREENSHOT, RequestBody.create(PNG, png));
			}
			Request request = new Request.Builder().url(url).post(body.build()).build();
			httpClient.newCall(request).enqueue(new Callback()
			{
				@Override
				public void onFailure(@Nonnull Call call, @Nonnull IOException e)
				{
					log.debug("Discord webhook failed", e);
				}

				@Override
				public void onResponse(@Nonnull Call call, @Nonnull Response response)
				{
					if (!response.isSuccessful())
					{
						log.debug("Discord webhook returned HTTP {}", response.code());
					}
					response.close();
				}
			});
		}
	}
}
