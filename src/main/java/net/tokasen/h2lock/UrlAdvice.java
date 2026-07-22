package net.tokasen.h2lock;

import net.bytebuddy.asm.Advice;

/**
 * Inlined advice copied into the body of {@code org.h2.jdbc.JdbcConnection}
 * constructors. It must only touch bootstrap classes ({@link String},
 * {@link System}) so it stays visible inside isolated plugin class loaders.
 */
public final class UrlAdvice {

    private UrlAdvice() {
    }

    @Advice.OnMethodEnter
    static void enter(@Advice.Argument(value = 0, readOnly = false) String url) {
        // Global kill switch.
        if ("false".equalsIgnoreCase(System.getProperty("h2lock.enabled"))) {
            return;
        }
        if (url == null || !url.startsWith("jdbc:h2:")) {
            return;
        }
        // Only embedded file databases have a FILE_LOCK; skip in-memory, remote and
        // zip URLs. AUTO_SERVER itself opens an in-memory management db we must ignore.
        String rest = url.substring(8).toLowerCase();
        if (rest.startsWith("mem:") || rest.startsWith("memfs:") || rest.startsWith("memlzf:")
                || rest.startsWith("tcp:") || rest.startsWith("ssl:") || rest.startsWith("zip:")) {
            return;
        }

        // Resolve the desired lock mode: fs (default) | socket | no.
        String type = System.getProperty("h2lock.type");
        if (type == null) {
            type = "fs";
        }
        type = type.trim().toLowerCase();
        String lock;
        if (type.equals("socket")) {
            lock = "SOCKET";
        } else if (type.equals("no")) {
            lock = "NO";
        } else {
            lock = "FS";
        }

        boolean autoServer = !"false".equalsIgnoreCase(System.getProperty("h2lock.autoserver"));
        boolean override = "true".equalsIgnoreCase(System.getProperty("h2lock.override"));
        boolean verbose = "true".equalsIgnoreCase(System.getProperty("h2lock.verbose"));

        // AUTO_SERVER is only supported with FILE_LOCK=SOCKET or FILE. Promote the
        // soft default (fs); an explicit "no" wins and disables AUTO_SERVER instead.
        if (autoServer) {
            if (lock.equals("FS")) {
                lock = "SOCKET";
            } else if (lock.equals("NO")) {
                autoServer = false;
            }
        }

        String original = url;
        String upper = url.toUpperCase();
        boolean hasLock = upper.indexOf("FILE_LOCK") >= 0;
        boolean hasAuto = upper.indexOf("AUTO_SERVER") >= 0;
        boolean lockSet = false; // did we set/replace FILE_LOCK ourselves?

        if (!hasLock) {
            url = url + ";FILE_LOCK=" + lock;
            lockSet = true;
        } else if (override) {
            int at = upper.indexOf("FILE_LOCK");
            int end = url.indexOf(';', at);
            if (end < 0) {
                end = url.length();
            }
            url = url.substring(0, at) + "FILE_LOCK=" + lock + url.substring(end);
            lockSet = true;
        }

        // Append AUTO_SERVER only when the effective lock is known compatible, so we
        // never emit an illegal combination that would break the connection.
        if (autoServer && !hasAuto) {
            boolean compatible;
            if (lockSet) {
                compatible = lock.equals("SOCKET") || lock.equals("FILE");
            } else {
                compatible = upper.indexOf("FILE_LOCK=SOCKET") >= 0
                        || upper.indexOf("FILE_LOCK=FILE") >= 0;
            }
            if (compatible) {
                url = url + ";AUTO_SERVER=TRUE";
            }
        }

        if (verbose) {
            if (url.equals(original)) {
                System.out.println("[H2LockAgent] left untouched: " + url);
            } else {
                System.out.println("[H2LockAgent] patched H2 URL: " + url);
            }
        }
    }
}
