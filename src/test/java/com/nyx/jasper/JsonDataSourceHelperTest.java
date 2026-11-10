package com.nyx.jasper;

import com.sun.net.httpserver.HttpServer;
import net.sf.jasperreports.json.data.JsonDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Fetching the datasource a report is built on.
 *
 * A real HTTP server on the loopback, not a doubled connection: the failure this code has to get
 * right is what happens on a 500 or an empty body, and a mock would mostly test the mock.
 */
public class JsonDataSourceHelperTest {

    private HttpServer server;
    private String base;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data", exchange -> respond(exchange, 200,
                "{\"data\":[{\"site\":\"BXL\",\"kwh\":12}]}"));
        server.createContext("/empty", exchange -> respond(exchange, 200, ""));
        server.createContext("/broken", exchange -> respond(exchange, 500, "{\"error\":\"boom\"}"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    public void aJsonBodyBecomesADatasource() throws Exception {
        JsonDataSource dataSource = JsonDataSourceHelper.createFromUrl(base + "/data?token=e414bde7&flat=true");
        assertNotNull(dataSource);
    }

    @Test
    public void aBodyThatIs404IsAnErrorNotAnEmptyReport() {
        try {
            JsonDataSourceHelper.createFromUrl(base + "/missing");
            fail("a 404 must not produce a datasource");
        } catch (Exception e) {
            assertTrue(e instanceof IOException);
            assertTrue(e.getMessage().contains("404"));
        }
    }

    @Test
    public void a500IsReported() {
        try {
            JsonDataSourceHelper.createFromUrl(base + "/broken");
            fail("a 500 must not produce a datasource");
        } catch (Exception e) {
            assertTrue(e instanceof IOException);
            assertTrue(e.getMessage().contains("500"));
            assertFalse("the token never reaches the message", e.getMessage().contains("e414bde7"));
        }
    }

    @Test
    public void anEmptyBodyIsAnError() {
        try {
            JsonDataSourceHelper.createFromUrl(base + "/empty");
            fail("an empty body must not produce a datasource");
        } catch (Exception e) {
            assertTrue(e instanceof IOException);
            assertTrue(e.getMessage().contains("No data received"));
            assertTrue("the url stays readable", e.getMessage().contains("/empty"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMissingUrlIsRefused() throws Exception {
        JsonDataSourceHelper.createFromUrl(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankUrlIsRefused() throws Exception {
        JsonDataSourceHelper.createFromUrl("   ");
    }

    @Test
    public void anUrlThatDoesNotParseIsNotAValidOne() {
        assertFalse(JsonDataSourceHelper.isValidUrl(null));
        assertFalse(JsonDataSourceHelper.isValidUrl(""));
        assertFalse(JsonDataSourceHelper.isValidUrl("  "));
        assertFalse(JsonDataSourceHelper.isValidUrl("pas une url"));
        assertFalse(JsonDataSourceHelper.isValidUrl("ftp://energy.example/x"));
        assertFalse(JsonDataSourceHelper.isValidUrl("file:///etc/passwd"));
        assertTrue(JsonDataSourceHelper.isValidUrl("http://energy.example/x"));
        assertTrue(JsonDataSourceHelper.isValidUrl("HTTPS://energy.example/x"));
    }
}
