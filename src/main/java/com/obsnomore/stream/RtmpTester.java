package com.obsnomore.stream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RTMP connection + stability advisor.
 *
 * <p>Streams generated test content to the target for a few seconds, parses
 * ffmpeg progress (connect time, bitrate, speed, drops) and recommends the
 * highest quality preset that fits ~70% of measured throughput, plus a
 * latency/stability verdict. Everything runs on a daemon thread with a
 * callback.
 */
public final class RtmpTester {
    private RtmpTester() {
    }

    public static class Result {
        public boolean reachable;
        public long connectMs = -1;
        public double bitrateKbps;
        public double speed = 1.0;
        public int dropped;
        public String verdict = "not run";
        public String recommendedQuality = "720p30";
        public String detail = "";
    }    public interface Callback {
        void done(Result r);
    }

    private static final Pattern BITRATE = Pattern.compile("bitrate=\\s*([0-9.]+)kbits/s");
    private static final Pattern SPEED = Pattern.compile("speed=\\s*([0-9.]+)x");
    private static final Pattern FRAME = Pattern.compile("frame=\\s*(\\d+)");
    private static final Pattern DROP = Pattern.compile("drop=\\s*(\\d+)");

    public static void test(final String ffmpeg, final String url, final int seconds,
                            final Callback cb) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                cb.done(execute(ffmpeg, url, seconds));
            }
        }, "OBSNoMore-rtmp-test");
        t.setDaemon(true);
        t.start();
    }

    private static Result execute(String ffmpeg, String url, int seconds) {
        Result r = new Result();
        if (ffmpeg == null || ffmpeg.isEmpty()) {
            r.detail = "no ffmpeg binary";
            r.verdict = "FAIL (no ffmpeg)";
            return r;
        }
        if (url == null || url.trim().isEmpty()) {
            r.detail = "empty URL";
            r.verdict = "FAIL (no URL)";
            return r;
        }
        List<String> a = new ArrayList<String>();
        a.add(ffmpeg);
        a.add("-hide_banner");
        a.add("-loglevel");
        a.add("info");
        a.add("-y");
        a.add("-re");
        a.add("-f");
        a.add("lavfi");
        a.add("-i");
        a.add("testsrc2=size=1280x720:rate=30");
        a.add("-f");
        a.add("lavfi");
        a.add("-i");
        a.add("sine=frequency=440:sample_rate=48000");
        a.add("-t");
        a.add(String.valueOf(Math.max(5, Math.min(30, seconds))));
        a.add("-c:v");
        a.add("libx264");
        a.add("-preset");
        a.add("veryfast");
        a.add("-b:v");
        a.add("6000k");
        a.add("-c:a");
        a.add("aac");
        a.add("-f");
        a.add("flv");
        a.add(url.trim());
        long start = System.currentTimeMillis();
        boolean sawSetup = false;
        boolean connected = false;
        boolean refused = false;
        int frames = 0;
        Process p = null;
        try {
            p = new ProcessBuilder(a).start();
            BufferedReader err = new BufferedReader(
                    new InputStreamReader(p.getErrorStream()));
            String line;
            long deadline = start + (seconds + 15) * 1000L;
            while ((line = err.readLine()) != null) {
                // NOTE: "Stream #0:0" mapping lines print during setup even
                // when the server refuses us, so only real frame progress
                // counts as a connection.
                if (!sawSetup && line.contains("Stream mapping")) {
                    sawSetup = true;
                }
                if (line.contains("frame=")) {
                    if (!connected) {
                        connected = true;
                        r.connectMs = System.currentTimeMillis() - start;
                    }
                }
                if (line.contains("Connection refused")
                        || line.contains("No route to host")
                        || line.contains("Connection timed out")
                        || line.contains("Error opening output")) {
                    refused = true;
                }
                Matcher m = BITRATE.matcher(line);
                if (m.find()) {
                    try {
                        r.bitrateKbps = Double.parseDouble(m.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                m = SPEED.matcher(line);
                if (m.find()) {
                    try {
                        r.speed = Double.parseDouble(m.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                m = FRAME.matcher(line);
                if (m.find()) {
                    try {
                        frames = Integer.parseInt(m.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                m = DROP.matcher(line);
                if (m.find()) {
                    try {
                        r.dropped = Integer.parseInt(m.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (System.currentTimeMillis() > deadline) {
                    try {
                        p.destroy();
                    } catch (Throwable ignored) {
                    }
                    break;
                }
            }
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            try {
                err.close();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            r.detail = String.valueOf(t.getMessage());
            r.verdict = "FAIL (" + r.detail + ")";
            return r;
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        r.reachable = connected && frames > 30;
        r.recommendedQuality = Presets.recommend(r.bitrateKbps).id;
        StringBuilder sb = new StringBuilder();
        sb.append("conn=").append(r.connectMs).append("ms");
        sb.append(" avg=").append((int) r.bitrateKbps).append("kbps");
        sb.append(" speed=").append(String.format("%.2f", r.speed)).append("x");
        sb.append(" drop=").append(r.dropped);
        r.detail = sb.toString();
        if (!r.reachable) {
            if (refused) {
                r.verdict = "FAIL (connection refused — server/key/firewall?)";
            } else if (!sawSetup) {
                r.verdict = "FAIL (ffmpeg could not start — check binary)";
            } else {
                r.verdict = "FAIL (no frames accepted — check URL/key)";
            }
        } else if (r.speed < 0.9 || r.dropped > frames * 0.02) {
            r.verdict = "UNSTABLE (drops/slow mux — lower bitrate), try " + r.recommendedQuality;
        } else if (r.connectMs > 5000) {
            r.verdict = "SLOW handshake (" + r.connectMs + "ms latency?), try " + r.recommendedQuality;
        } else {
            r.verdict = "STABLE, recommended " + r.recommendedQuality;
        }
        return r;
    }
}
