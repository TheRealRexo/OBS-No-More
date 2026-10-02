package com.obsnomore.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls generic web content for {@code browser} sources.
 *
 * <p>Any URL that returns plain-text or JSON lines works — chat endpoints,
 * counters, now-playing widgets, etc. Polling runs on daemon threads so the
 * render thread never blocks; each URL is cached and re-polled at most every
 * few seconds. No demo or simulated data: an unset URL shows a hint, an
 * unreachable one shows the last good lines (or a loading note).
 */
public final class WebFetcher {
    private static final WebFetcher INSTANCE = new WebFetcher();

    private static final long REFRESH_MS = 5000L;
    private static final int MAX_LINES = 40;

    private static final class Entry {
        List<String> lines = new ArrayList<String>();
        long fetchedAt = 0L;
        boolean inFlight = false;
    }

    private final Map<String, Entry> cache = new HashMap<String, Entry>();

    public static WebFetcher get() {
        return INSTANCE;
    }

    /** No-op kept for call compatibility (fetching is on-demand). */
    public void start() {
    }

    /**
     * Current lines for a URL (up to {@code max}, newest last). Kicks off a
     * background refresh when the cache is stale.
     */
    public List<String> getLines(String url, int max) {
        if (max < 1) max = 1;
        if (max > 25) max = 25;
        if (url == null || url.trim().isEmpty()) {
            return Collections.singletonList("Set a URL in Properties");
        }
        url = url.trim();
        Entry e;
        boolean stale;
        synchronized (cache) {
            e = cache.get(url);
            if (e == null) {
                e = new Entry();
                cache.put(url, e);
            }
            stale = !e.inFlight && System.currentTimeMillis() - e.fetchedAt > REFRESH_MS;
            if (stale) e.inFlight = true;
        }
        if (stale) fetchAsync(url, e);
        synchronized (e) {
            if (e.lines.isEmpty()) {
                return Collections.singletonList(e.fetchedAt > 0 ? "(no data)" : "Loading...");
            }
            int from = Math.max(0, e.lines.size() - max);
            return Collections.unmodifiableList(new ArrayList<String>(e.lines.subList(from, e.lines.size())));
        }
    }

    private void fetchAsync(final String url, final Entry e) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> fresh = fetchOnce(url);
                synchronized (e) {
                    if (fresh != null) {
                        e.lines = fresh;
                        e.fetchedAt = System.currentTimeMillis();
                    } else if (e.fetchedAt == 0) {
                        e.fetchedAt = 1L; // mark attempted so we show "(no data)"
                    }
                    e.inFlight = false;
                }
            }
        }, "OBSNoMore-webfetch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Best-effort poll of an endpoint returning plain text or JSON lines.
     * Returns null on any failure (caller keeps old lines).
     */
    static List<String> fetchOnce(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "OBSNoMore/1.3 (+minecraft 1.6.4)");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            String contentType = String.valueOf(conn.getContentType());
            if (contentType.contains("text/html")) {
                // Browser-app pages (JS overlays) are not pollable endpoints.
                return null;
            }
            List<String> found = new ArrayList<String>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                int lines = 0;
                while ((line = br.readLine()) != null && lines++ < 200 && found.size() < MAX_LINES) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.contains("\"author\"") || line.contains("\"message\"") || line.contains("\"text\"")) {
                        String author = extractJsonString(line, "author", "user", "name");
                        String text = extractJsonString(line, "message", "text", "body");
                        if (text != null && !text.isEmpty()) {
                            String a = (author == null || author.isEmpty()) ? "" : author + ": ";
                            found.add(trimLen(a + text, 140));
                        }
                    } else if (line.contains(":")) {
                        int idx = line.indexOf(':');
                        String author = trimLen(line.substring(0, idx).trim(), 20);
                        String text = trimLen(line.substring(idx + 1).trim(), 140);
                        if (!text.isEmpty()) {
                            found.add(author.isEmpty() ? text : author + ": " + text);
                        }
                    } else {
                        found.add(trimLen(line, 140));
                    }
                }
            }
            return found;
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractJsonString(String json, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]{1,200})\"");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static String trimLen(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
