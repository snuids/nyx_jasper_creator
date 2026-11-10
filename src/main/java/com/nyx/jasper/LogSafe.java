package com.nyx.jasper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a URL may look like in a log line.
 *
 * The datasource URLs carry the session token as a query parameter — {@code ?token=…} — and
 * {@code docker logs} is readable by anyone who can reach the docker socket on the host. A token
 * stays replayable for its whole life, so it is masked before the URL reaches the log file; the
 * page the URL addresses stays readable, which is what an operator needs to debug.
 */
public final class LogSafe {

    /** `token`, `password`, `secret` and `key`, as a query parameter or a form field. */
    private static final Pattern SECRET = Pattern.compile(
            "([?&](?:token|password|passwd|secret|key|apikey)=)[^&\\s\"']*",
            Pattern.CASE_INSENSITIVE);

    private LogSafe() {
    }

    /** The URL with every credential-bearing parameter replaced by `***`. */
    public static String url(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = SECRET.matcher(url);
        return matcher.replaceAll("$1***");
    }
}
