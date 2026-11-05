package com.crejaud.chanced;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
	/** Longest wait between retries, however many there are. */
	private static final long MAX_RETRY_DELAY_MS = 60_000;
	/** Caps the shift so the delay cannot overflow before the minute cap applies. */
	private static final int MAX_DOUBLINGS = 20;
	private static final int HTTP_TOO_MANY_REQUESTS = 429;
	private static final int HTTP_SERVER_ERROR = 500;
	private static final int PERCENT = 100;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;
	/** Webhook calls in flight and retries waiting to run, cancelled when the plugin stops. */
	private final Set<Call> calls = ConcurrentHashMap.newKeySet();
	private final Set<ScheduledFuture<?>> retries = ConcurrentHashMap.newKeySet();

	@Inject
	DiscordNotifier(OkHttpClient httpClient, Gson gson, DrawManager drawManager, ScheduledExecutorService executor)
	{
		this.httpClient = httpClient;
		this.gson = gson;
		this.drawManager = drawManager;
		this.executor = executor;
	}

	/** Cancels every webhook call in flight and every retry still waiting. */
	void shutDown()
	{
		retries.forEach(retry -> retry.cancel(false));
		retries.clear();
		calls.forEach(Call::cancel);
		calls.clear();
	}

	/** Delivery settings, read from config when the message is sent. */
	static final class Delivery
	{
		int maxRetries;
		int baseRetryDelayMs;
		int networkTimeoutSeconds;
		int imageWriteTimeoutSeconds;
		int screenshotScale;
		String footerText;
		String footerIcon;

		static Delivery from(ChancedConfig config)
		{
			Delivery d = new Delivery();
			d.maxRetries = Math.max(0, config.maxRetries());
			d.baseRetryDelayMs = Math.max(0, config.baseRetryDelay());
			d.networkTimeoutSeconds = Math.max(1, config.networkTimeout());
			d.imageWriteTimeoutSeconds = Math.max(1, config.imageWriteTimeout());
			d.screenshotScale = Math.max(1, Math.min(PERCENT, config.screenshotScale()));
			d.footerText = config.embedFooterText();
			d.footerIcon = config.embedFooterIcon();
			return d;
		}
	}

	void send(ChancedEvent event, String template, String webhooks, boolean screenshot, Delivery delivery)
	{
		String payload = gson.toJson(payload(event, template, screenshot, delivery.footerText, delivery.footerIcon));
		if (!screenshot)
		{
			post(webhooks, payload, null, delivery);
			return;
		}
		drawManager.requestNextFrameListener(image -> executor.execute(() ->
		{
			byte[] png = null;
			try
			{
				BufferedImage buffered = scale(ImageUtil.bufferedImageFromImage(image), delivery.screenshotScale);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageIO.write(buffered, "png", out);
				png = out.toByteArray();
			}
			catch (IOException e)
			{
				log.debug("Could not encode screenshot", e);
			}
			post(webhooks, payload, png, delivery);
		}));
	}

	static BufferedImage scale(BufferedImage image, int percent)
	{
		if (percent >= PERCENT)
		{
			return image;
		}
		int width = Math.max(1, image.getWidth() * percent / PERCENT);
		int height = Math.max(1, image.getHeight() * percent / PERCENT);
		return ImageUtil.resizeImage(image, width, height, true);
	}

	static JsonObject payload(ChancedEvent event, String template, boolean screenshot, String footerText,
		String footerIcon)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("title", "Chanced!");
		embed.addProperty("color", EMBED_COLOR);
		embed.addProperty("description", event.format(template, "**")
			+ (event.isEstimate() ? "\n_Max hit is a PvP estimate._" : ""));

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

		if (footerText != null && !footerText.trim().isEmpty())
		{
			JsonObject footer = new JsonObject();
			footer.addProperty("text", footerText.trim());
			if (footerIcon != null && !footerIcon.trim().isEmpty())
			{
				footer.addProperty("icon_url", footerIcon.trim());
			}
			embed.add("footer", footer);
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

	/** Wait before retry number {@code attempt} (1-based): base, 2 x base, 4 x base... capped at a minute. */
	static long retryDelayMs(int baseMs, int attempt)
	{
		long delay = (long) baseMs << Math.min(attempt - 1, MAX_DOUBLINGS);
		return Math.min(delay, MAX_RETRY_DELAY_MS);
	}

	private void post(String webhooks, String payload, byte[] png, Delivery delivery)
	{
		OkHttpClient client = httpClient.newBuilder()
			.connectTimeout(delivery.networkTimeoutSeconds, TimeUnit.SECONDS)
			.readTimeout(delivery.networkTimeoutSeconds, TimeUnit.SECONDS)
			.writeTimeout(png != null ? delivery.imageWriteTimeoutSeconds : delivery.networkTimeoutSeconds,
				TimeUnit.SECONDS)
			.build();
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
			attempt(client, request, 0, delivery);
		}
	}

	private void attempt(OkHttpClient client, Request request, int retriesDone, Delivery delivery)
	{
		Call webhookCall = client.newCall(request);
		calls.add(webhookCall);
		webhookCall.enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				calls.remove(call);
				if (call.isCanceled())
				{
					return;
				}
				log.debug("Discord webhook failed", e);
				retry(client, request, retriesDone, delivery);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				calls.remove(call);
				int code = response.code();
				response.close();
				if (response.isSuccessful())
				{
					return;
				}
				log.debug("Discord webhook returned HTTP {}", code);
				// Rate limits and server errors are worth another try; a bad URL or payload is not.
				if (code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR)
				{
					retry(client, request, retriesDone, delivery);
				}
			}
		});
	}

	private void retry(OkHttpClient client, Request request, int retriesDone, Delivery delivery)
	{
		if (retriesDone >= delivery.maxRetries)
		{
			return;
		}
		int next = retriesDone + 1;
		retries.removeIf(ScheduledFuture::isDone);
		retries.add(executor.schedule(() -> attempt(client, request, next, delivery),
			retryDelayMs(delivery.baseRetryDelayMs, next), TimeUnit.MILLISECONDS));
	}
}
