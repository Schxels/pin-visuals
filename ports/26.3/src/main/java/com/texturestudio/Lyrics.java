package com.texturestudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spotify sing-along lyrics. The song that is playing is read from the window title of the Spotify desktop app
 * ("Artist - Song" while playing, "Spotify Premium"/"Spotify Free" while paused) through JNA, which Minecraft already
 * ships. No external program is started and nothing is written to disk. The playback position is counted from the
 * moment a new song shows up, so seeking inside a song is not picked up. The time-synced lyrics are looked up on
 * LRCLIB (lrclib.net, an open lyrics database) and are only kept in memory. The drawing is done by {@link LyricsHud}.
 */
public final class Lyrics {

	public record Line(long timeMs, String text) {
	}

	public enum State {
		OFF, NOT_WINDOWS, NO_SPOTIFY, AD, PAUSED, LOADING, NOT_FOUND, READY, ERROR
	}

	// ---- now playing (written by the reader thread)
	private static volatile boolean helperRunning;
	private static volatile boolean spotifyFound;
	private static volatile boolean playing;
	private static volatile String title = "";
	private static volatile String artist = "";
	private static volatile long durationMs;
	private static volatile long positionMs;
	/** System time (ms) at which {@link #positionMs} was valid. */
	private static volatile long positionAt;
	/** System time (ms) when the current track was first seen. */
	private static volatile long trackSince;

	// ---- lyrics of the current track
	private static volatile String loadedKey = "";
	private static volatile List<Line> lines = List.of();
	private static volatile State fetchState = State.NOT_FOUND;

	private static Thread reader;
	private static volatile boolean stopReader;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final String UA = "PinVisuals/2.0 (Minecraft mod, https://lrclib.net)";

	private Lyrics() {
	}

	public static boolean enabled() {
		return !Cosmetics.NONE.equals(Cosmetics.lyrics);
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	/** Called every client tick: starts / stops the reader and loads the lyrics when the song changes. */
	public static synchronized void tick() {
		// the reader also feeds the Spotify overlay, so it runs when either is on
		if (!(enabled() || SpotifyHud.enabled()) || !windows()) {
			stopHelper();
			return;
		}
		if (reader == null || !reader.isAlive()) {
			startHelper();
		}
		if (!enabled()) {
			loadedKey = "";
			lines = List.of();
			return;
		}
		if (!spotifyFound || title.isEmpty() || artist.isEmpty()) {
			return; // nothing playing, or an ad (ads have no "Artist - Song" title)
		}
		String key = artist + "\u0000" + title;
		if (!key.equals(loadedKey)) {
			loadedKey = key;
			lines = List.of();
			fetchState = State.LOADING;
			String t = title;
			String a = artist;
			CompletableFuture.runAsync(() -> fetch(key, t, a));
		}
	}

	public static State state() {
		if (!enabled()) {
			return State.OFF;
		}
		if (!windows()) {
			return State.NOT_WINDOWS;
		}
		if (!spotifyFound || title.isEmpty()) {
			return State.NO_SPOTIFY;
		}
		if (artist.isEmpty()) {
			return State.AD;
		}
		if (!playing) {
			return State.PAUSED;
		}
		return fetchState;
	}

	/** One line for the menu. */
	public static String statusLine() {
		return switch (state()) {
			case OFF -> "Pick a style to turn it on";
			case NOT_WINDOWS -> "Needs Windows and the Spotify app";
			case NO_SPOTIFY -> helperRunning ? "Play a song in the Spotify app" : "Starting...";
			case AD -> "Ad playing, lyrics come with the next song";
			case PAUSED -> "Paused: " + title;
			case LOADING -> "Loading lyrics for " + title;
			case NOT_FOUND -> "No synced lyrics for " + title;
			case ERROR -> "Lyrics service not reachable";
			case READY -> "Now: " + title + " - " + artist;
		};
	}

	public static String title() {
		return title;
	}

	public static String artist() {
		return artist;
	}

	public static long trackSince() {
		return trackSince;
	}

	public static boolean windowsOk() {
		return windows();
	}

	public static boolean helperRunning() {
		return helperRunning;
	}

	/** Something is playing or paused in the Spotify app. */
	public static boolean hasTrack() {
		return spotifyFound && !title.isEmpty();
	}

	public static boolean playing() {
		return playing;
	}

	/** Length of the song in ms, known once its lyrics were found on LRCLIB (0 = unknown). */
	public static long durationMs() {
		return durationMs;
	}

	/** Album covers are not available without the Windows media helper: always null. */
	public static String coverFile() {
		return null;
	}

	public static int coverVersion() {
		return 0;
	}

	public static List<Line> lines() {
		return lines;
	}

	/** Current playback position in ms, counted from when the song started. */
	public static long position() {
		long p = positionMs;
		if (playing) {
			p += System.currentTimeMillis() - positionAt;
		}
		return durationMs > 0 ? Math.min(p, durationMs) : p;
	}

	/** Text colour, glow colour (0 = none) and the "sung" colour of a style. */
	public static int[] colors(String style) {
		return switch (style) {
			case "neon" -> new int[] { 0xFFFF7AD9, 0xFF22D3EE, 0xFF67E8F9 };
			case "gold" -> new int[] { 0xFFFDE68A, 0xFFF59E0B, 0xFFFFFFFF };
			case "aurora" -> new int[] { 0xFF6EE7B7, 0xFF8B5CF6, 0xFFC4B5FD };
			case "classic" -> new int[] { 0xFFFFFFFF, 0, 0xFFFDE047 };
			default -> new int[] { 0xFFE0F2FE, 0xFF60A5FA, 0xFFFFFFFF };
		};
	}

	// ================================================================ Spotify window title

	private static void startHelper() {
		stopReader = false;
		Thread t = new Thread(Lyrics::readLoop, "PinVisuals-Spotify");
		t.setDaemon(true);
		reader = t;
		t.start();
	}

	private static void readLoop() {
		helperRunning = true;
		try {
			while (!stopReader) {
				try {
					update(WindowTitles.spotifyTitle());
				} catch (Throwable e) {
					// JNA not usable on this system: stop quietly, the menu shows "Starting..."
					TextureStudioClient.LOGGER.warn("Pin Visuals: could not read the Spotify window", e);
					return;
				}
				try {
					Thread.sleep(400);
				} catch (InterruptedException e) {
					return;
				}
			}
		} finally {
			helperRunning = false;
			spotifyFound = false;
			playing = false;
		}
	}

	/** @param windowTitle title of the Spotify main window, or null if Spotify is not running */
	private static void update(String windowTitle) {
		long now = System.currentTimeMillis();
		if (windowTitle == null || windowTitle.isBlank()) {
			if (playing) {
				positionMs = position();
				positionAt = now;
			}
			spotifyFound = false;
			playing = false;
			return;
		}
		String w = windowTitle.trim();
		int dash = w.indexOf(" - ");
		if (dash <= 0) {
			if (w.toLowerCase(Locale.ROOT).startsWith("spotify")) {
				// "Spotify", "Spotify Premium", "Spotify Free": paused (or nothing loaded yet)
				if (playing) {
					positionMs = position();
					positionAt = now;
				}
				playing = false;
				spotifyFound = !title.isEmpty();
			} else {
				// any other title (e.g. "Advertisement") is an ad: no artist
				if (!w.equals(title) || !artist.isEmpty()) {
					trackSince = now;
					positionMs = 0;
					positionAt = now;
					durationMs = 0;
				}
				title = w;
				artist = "";
				playing = true;
				spotifyFound = true;
			}
			return;
		}
		String a = w.substring(0, dash).trim();
		String t = w.substring(dash + 3).trim();
		boolean newTrack = !t.equals(title) || !a.equals(artist);
		if (newTrack) {
			trackSince = now;
			positionMs = 0;
			positionAt = now;
			durationMs = 0;
		} else if (!playing) {
			positionAt = now; // resumed: carry on from where it was paused
		}
		title = t;
		artist = a;
		playing = true;
		spotifyFound = true;
	}

	private static synchronized void stopHelper() {
		stopReader = true;
		if (reader != null) {
			reader.interrupt();
			reader = null;
		}
		helperRunning = false;
		spotifyFound = false;
		loadedKey = "";
		lines = List.of();
	}

	public static void shutdown() {
		stopHelper();
	}

	/** Kept in its own class so JNA is only loaded on Windows. */
	private static final class WindowTitles {
		/** pid -> "is Spotify.exe", refreshed every 30 seconds */
		private static final Map<Integer, Boolean> SPOTIFY_PIDS = new HashMap<>();
		private static long pidsSince;

		static String spotifyTitle() {
			long now = System.currentTimeMillis();
			if (now - pidsSince > 30_000) {
				SPOTIFY_PIDS.clear();
				pidsSince = now;
			}
			User32 u = User32.INSTANCE;
			String[] found = new String[1];
			u.EnumWindows((WinDef.HWND hwnd, com.sun.jna.Pointer data) -> {
				if (!u.IsWindowVisible(hwnd)) {
					return true;
				}
				int len = u.GetWindowTextLength(hwnd);
				if (len <= 0) {
					return true;
				}
				IntByReference pid = new IntByReference();
				u.GetWindowThreadProcessId(hwnd, pid);
				if (!SPOTIFY_PIDS.computeIfAbsent(pid.getValue(), WindowTitles::isSpotify)) {
					return true;
				}
				char[] buf = new char[len + 1];
				u.GetWindowText(hwnd, buf, buf.length);
				String s = com.sun.jna.Native.toString(buf);
				if (!s.isBlank()) {
					found[0] = s;
					return false; // stop
				}
				return true;
			}, null);
			return found[0];
		}

		private static boolean isSpotify(int pid) {
			return ProcessHandle.of(pid).flatMap(h -> h.info().command())
					.map(c -> c.toLowerCase(Locale.ROOT).endsWith("spotify.exe")).orElse(false);
		}
	}

	// ================================================================ LRCLIB

	private record Found(List<Line> lines, long durationMs) {
	}

	private static void fetch(String key, String t, String a) {
		try {
			Found got = search(clean(t), a);
			if (got == null && !clean(t).equals(t)) {
				got = search(t, a);
			}
			if (key.equals(loadedKey)) {
				lines = got == null ? List.of() : got.lines();
				if (got != null && got.durationMs() > 0) {
					durationMs = got.durationMs();
				}
				fetchState = got == null ? State.NOT_FOUND : State.READY;
			}
		} catch (Exception e) {
			if (key.equals(loadedKey)) {
				fetchState = State.ERROR;
			}
		}
	}

	/** First search result that has synced lyrics (LRCLIB sorts by relevance). */
	private static Found search(String t, String a) throws Exception {
		JsonElement res = get("https://lrclib.net/api/search?track_name=" + enc(t) + "&artist_name=" + enc(a));
		if (res == null || !res.isJsonArray()) {
			return null;
		}
		JsonArray arr = res.getAsJsonArray();
		for (JsonElement e : arr) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			List<Line> l = synced(o);
			if (l == null) {
				continue;
			}
			double d = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
			return new Found(l, Math.round(d * 1000));
		}
		return null;
	}

	private static JsonElement get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
				.header("User-Agent", UA).GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() == 404) {
			return null;
		}
		if (res.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + res.statusCode());
		}
		return JsonParser.parseString(res.body());
	}

	private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+(?:[.:]\\d+)?)]");

	/** Parses "syncedLyrics" (LRC) of a result; null if it has none. */
	private static List<Line> synced(JsonObject o) {
		JsonElement s = o.get("syncedLyrics");
		if (s == null || s.isJsonNull()) {
			return null;
		}
		List<Line> out = new ArrayList<>();
		for (String raw : s.getAsString().split("\\r?\\n")) {
			Matcher m = TAG.matcher(raw);
			int end = 0;
			List<Long> times = new ArrayList<>();
			while (m.find() && m.start() == end) {
				long min = Long.parseLong(m.group(1));
				double sec = Double.parseDouble(m.group(2).replace(':', '.'));
				times.add(min * 60_000 + Math.round(sec * 1000));
				end = m.end();
			}
			String text = raw.substring(end).trim();
			for (long tm : times) {
				out.add(new Line(tm, text));
			}
		}
		out.sort((x, y) -> Long.compare(x.timeMs(), y.timeMs()));
		return out.isEmpty() ? null : List.copyOf(out);
	}

	/** "Song - Remastered 2011" / "Song (feat. X)" -> "Song" for a second search. */
	private static String clean(String t) {
		String s = t.replaceAll("\\s*[(\\[](feat\\.|ft\\.|with |from )[^)\\]]*[)\\]]", "");
		int dash = s.indexOf(" - ");
		if (dash > 0) {
			s = s.substring(0, dash);
		}
		return s.trim();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
