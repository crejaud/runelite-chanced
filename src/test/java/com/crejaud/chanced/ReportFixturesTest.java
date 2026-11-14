package com.crejaud.chanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import org.junit.Test;

/**
 * Replays every debug report in {@code src/test/resources/reports}: a {@code <name>.json.gz} saved
 * as-is from a Discord message, with {@code <name>.expect.json} next to it, written by hand:
 * {@code "chanceTicks"}, the ticks that really were chances (empty when the alert was wrong), and
 * {@code "note"}, what happened in game.
 */
public class ReportFixturesTest
{
	private static final String REPORT = ".json.gz";
	private static final String EXPECT = ".expect.json";

	@Test
	public void everyReportReplaysToWhatReallyHappened() throws IOException, URISyntaxException
	{
		// GIVEN every debug report saved as a test fixture
		URL dir = ReportFixturesTest.class.getResource("/reports");
		assertTrue("src/test/resources/reports is missing", dir != null);
		int replayed = 0;
		try (DirectoryStream<Path> reports = Files.newDirectoryStream(Paths.get(dir.toURI()), "*" + REPORT))
		{
			for (Path path : reports)
			{
				String name = path.getFileName().toString();
				Path expectPath = path.resolveSibling(name.substring(0, name.length() - REPORT.length()) + EXPECT);
				JsonObject report = read(path, true);
				JsonObject expect = read(expectPath, false);
				Set<Integer> expected = new TreeSet<>();
				for (JsonElement tick : expect.getAsJsonArray("chanceTicks"))
				{
					expected.add(tick.getAsInt());
				}

				// WHEN it is replayed through the matcher
				Set<Integer> chances = HitMatcherTest.chanceTicks(ReportReplay.replay(report));

				// THEN only the ticks that really were chances alert
				assertEquals(name + ": " + expect.get("note"), expected, chances);
				replayed++;
			}
		}
		// AND the folder held at least one report
		assertTrue(replayed > 0);
	}

	private static JsonObject read(Path path, boolean gzipped) throws IOException
	{
		InputStream in = Files.newInputStream(path);
		try (Reader reader = new InputStreamReader(gzipped ? new GZIPInputStream(in) : in, StandardCharsets.UTF_8))
		{
			return new JsonParser().parse(reader).getAsJsonObject();
		}
	}
}
