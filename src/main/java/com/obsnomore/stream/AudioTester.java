package com.obsnomore.stream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies a game-audio device actually delivers sound.
 *
 * <p>Records a few seconds from the device and reads ffmpeg's volumedetect
 * readout (mean/max volume). Silence (or a dead device) comes back as
 * {@code -inf} dB, which we report honestly instead of pretending.
 */
public final class AudioTester {
    private AudioTester() {
    }

    public static class Result {
        public boolean heard;
        public String meanDb = "n/a";
        public String maxDb = "n/a";
        public String verdict = "not run";
        public String detail = "";
    }

    public interface Callback {
        void done(Result r);
    }

    private static final Pattern MEAN = Pattern.compile("mean_volume:\\s*(\\S+)");
    private static final Pattern MAX = Pattern.compile("max_volume:\\s*(\\S+)");

    public static void test(final String ffmpeg, final String device, final int seconds,
                            final Callback cb) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                cb.done(execute(ffmpeg, device, seconds));
            }
        }, "OBSNoMore-audio-test");
        t.setDaemon(true);
        t.start();
    }

    private static Result execute(String ffmpeg, String device, int seconds) {
        Result r = new Result();
        if (ffmpeg == null || ffmpeg.isEmpty()) {
            r.verdict = "FAIL (no ffmpeg)";
            return r;
        }
        List<String> a = new ArrayList<String>();
        a.add(ffmpeg);
        a.add("-hide_banner");
        a.add("-loglevel");
        a.add("info");
        a.add("-y");
        switch (FFmpeg.os()) {
            case WINDOWS:
                a.add("-f");
                a.add("dshow");
                a.add("-i");
                a.add("audio=" + device);
                break;
            case MAC:
                a.add("-f");
                a.add("avfoundation");
                a.add("-i");
                a.add(device);
                break;
            default:
                a.add("-f");
                a.add("pulse");
                a.add("-i");
                a.add(device);
                break;
        }
        a.add("-t");
        a.add(String.valueOf(Math.max(2, Math.min(10, seconds))));
        a.add("-af");
        a.add("volumedetect");
        a.add("-f");
        a.add("null");
        a.add("-");
        Process p = null;
        try {
            p = new ProcessBuilder(a).start();
            BufferedReader err = new BufferedReader(
                    new InputStreamReader(p.getErrorStream()));
            String line;
            long deadline = System.currentTimeMillis() + (seconds + 15) * 1000L;
            while ((line = err.readLine()) != null) {
                Matcher m = MEAN.matcher(line);
                if (m.find()) r.meanDb = m.group(1);
                m = MAX.matcher(line);
                if (m.find()) r.maxDb = m.group(1);
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
        r.detail = "mean=" + r.meanDb + " max=" + r.maxDb;
        r.heard = isAudible(r.maxDb);
        if (r.heard) {
            r.verdict = "HEARD audio (" + r.detail + ")";
        } else {
            r.verdict = "SILENT (no signal — wrong device? game quiet?)";
        }
        return r;
    }

    private static boolean isAudible(String db) {
        if (db == null) return false;
        db = db.trim();
        if (db.equalsIgnoreCase("-inf") || db.equalsIgnoreCase("n/a")) return false;
        try {
            return Double.parseDouble(db) > -60.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
