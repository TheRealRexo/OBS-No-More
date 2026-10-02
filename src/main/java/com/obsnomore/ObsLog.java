package com.obsnomore;

/**
 * Mod logging that always lands somewhere readable.
 *
 * <p>Neither {@code System.out} nor the game logger reliably reach
 * output-client.log on all launcher builds, so every diagnostic line is
 * ALSO appended to {@code obsnomore/diag.log} in the game dir — plain
 * file I/O that always works and is trivial to send in bug reports.
 */
public final class ObsLog {
    private ObsLog() {
    }

    private static volatile boolean headerWritten;

    public static void info(String line) {
        if (line == null) line = "";
        try {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("Minecraft");
            logger.info(line);
            // Flush handlers: piped stdout can otherwise hold our lines
            // until process exit, making live debugging impossible.
            try {
                for (java.util.logging.Handler h : logger.getHandlers()) {
                    try {
                        h.flush();
                    } catch (Throwable ignored) {
                    }
                }
                java.util.logging.LogManager lm;
                try {
                    lm = java.util.logging.LogManager.getLogManager();
                } catch (Throwable t) {
                    lm = null;
                }
                if (lm != null) {
                    java.util.Enumeration<String> names = null;
                    try {
                        names = lm.getLoggerNames();
                    } catch (Throwable ignored) {
                    }
                    while (names != null && names.hasMoreElements()) {
                        try {
                            java.util.logging.Logger l = lm.getLogger(names.nextElement());
                            if (l == null) continue;
                            for (java.util.logging.Handler h : l.getHandlers()) {
                                try {
                                    h.flush();
                                } catch (Throwable ignored) {
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        try {
            System.out.println(line);
            System.out.flush();
        } catch (Throwable ignored) {
        }
        try {
            java.io.File dir = null;
            try {
                dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getGameDir().resolve("obsnomore").toFile();
            } catch (Throwable t) {
                dir = new java.io.File("obsnomore");
            }
            if (dir != null) {
                dir.mkdirs();
                java.io.File f = new java.io.File(dir, "diag.log");
                boolean fresh = !headerWritten;
                synchronized (ObsLog.class) {
                    if (!headerWritten) {
                        headerWritten = true;
                        fresh = true;
                    } else {
                        fresh = false;
                    }
                }
                java.io.FileWriter w = new java.io.FileWriter(f, !fresh);
                if (fresh) {
                    w.write("=== OBSNoMore session "
                            + new java.util.Date() + " ===\n");
                }
                w.write(line + "\n");
                w.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
