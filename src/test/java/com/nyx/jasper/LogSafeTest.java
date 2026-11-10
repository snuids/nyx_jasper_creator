package com.nyx.jasper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * `LogSafe` is what keeps a session token out of the logs.
 *
 * The datasource URLs carry the token as a query parameter, and the worker logs them on every
 * generation: `docker logs` is readable by anyone who can reach the docker socket, and the token
 * stays replayable for its whole life.
 */
public class LogSafeTest {

    @Test
    public void aTokenInTheQueryIsMaskedAndTheRestOfTheUrlIsKept() {
        assertEquals(
                "https://energy.example/api/v1/datasource/my_ds2?token=***&flat=true&start=2026-10-09T23:00:00.000Z",
                LogSafe.url("https://energy.example/api/v1/datasource/my_ds2?token=e414bde7-6cfd&flat=true&start=2026-10-09T23:00:00.000Z"));
    }

    @Test
    public void aTokenInTheMiddleOfTheQueryIsMasked() {
        assertEquals(
                "https://energy.example/api?flat=true&token=***&end=now",
                LogSafe.url("https://energy.example/api?flat=true&token=abcdef&end=now"));
    }

    @Test
    public void aJdbcPropertyIsMaskedToo() {
        assertEquals("jdbc:sqlserver://sql.example:1433;databaseName=nyx;user=sa;password=***",
                LogSafe.url("jdbc:sqlserver://sql.example:1433;databaseName=nyx;user=sa;password=hunter2"));
    }

    @Test
    public void theOtherCredentialNamesAreMaskedToo() {
        assertEquals("https://x/?password=***", LogSafe.url("https://x/?password=hunter2"));
        assertEquals("https://x/?secret=***", LogSafe.url("https://x/?secret=s3cr3t"));
        assertEquals("https://x/?key=***", LogSafe.url("https://x/?key=abc"));
    }

    @Test
    public void aUrlWithoutACredentialIsLeftAlone() {
        String url = "https://energy.example/api/v1/datasource/my_ds2?flat=true&start=2026-10-09";
        assertEquals(url, LogSafe.url(url));
    }

    @Test
    public void aNullUrlStaysNull() {
        assertNull(LogSafe.url(null));
    }
}
