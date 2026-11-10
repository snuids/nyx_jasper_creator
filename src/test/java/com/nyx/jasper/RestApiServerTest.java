package com.nyx.jasper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The HTTP door the API calls to ask for a report.
 *
 * Started for real on an ephemeral port and called with a real client: what matters here is the
 * status code the caller sees — 400 for a request that cannot be honoured, 200 with a path when the
 * PDF exists, 503 when the module has no JMS connection left, and 500 when the file it claims to
 * have written is not there.
 */
public class RestApiServerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private RestApiServer server;
    private int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private void start(ReportGeneratorManager manager, StatusMessage status) throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new RestApiServer(port, manager, status);
        server.start();
        Thread.sleep(300);
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private StatusMessage connected() {
        StatusMessage status = new StatusMessage();
        status.setAlive(1);
        status.setConnections(1);
        status.setModule("NYX_Jasper_Creator");
        status.setVersion("1.0.22");
        return status;
    }

    @Test
    public void theHealthEndpointSaysUpWhenTheBrokerIsConnected() throws Exception {
        start(mock(ReportGeneratorManager.class), connected());

        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"UP\""));
        assertTrue(response.body().contains("NYX_Jasper_Creator"));
        assertTrue(response.body().contains("1.0.22"));
    }

    @Test
    public void theHealthEndpointSaysDownWithoutAConnection() throws Exception {
        StatusMessage status = connected();
        status.setConnections(0);
        start(mock(ReportGeneratorManager.class), status);

        HttpResponse<String> response = get("/health");

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"DOWN\""));
    }

    @Test
    public void theHealthEndpointSaysDownWithoutAStatus() throws Exception {
        start(mock(ReportGeneratorManager.class), null);

        HttpResponse<String> response = get("/health");

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("unknown"));
        assertTrue("the version falls back to the build", response.body().contains(Application.Version));
    }

    @Test
    public void theStatusEndpointReturnsTheStatusMessage() throws Exception {
        StatusMessage status = connected();
        status.setMessages(7);
        start(mock(ReportGeneratorManager.class), status);

        HttpResponse<String> response = get("/api/v1/status");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"messages\":7"));
        assertTrue(response.body().contains("NYX_Jasper_Creator"));
    }

    @Test
    public void aReportIsGeneratedAndItsPathReturned() throws Exception {
        String template = template();
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());
        StatusMessage status = connected();
        start(manager, status);

        HttpResponse<String> response = post("/api/v1/generate-report",
                "{\"template\":\"" + template.replace("\\\\", "\\\\\\\\") + "\",\"outputName\":\"api\"}");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(response.body().contains("api.pdf"));
        assertTrue("the report exists on disk", new File(folder.getRoot(), "api.pdf").exists());
        assertEquals(1, status.getMessages());
    }

    @Test
    public void anEmptyBodyIsRefusedWithTheShapeOfWhatIsExpected() throws Exception {
        StatusMessage status = connected();
        start(mock(ReportGeneratorManager.class), status);

        HttpResponse<String> response = post("/api/v1/generate-report", "");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Request body is required"));
        assertTrue("the message explains the two parameter formats", response.body().contains("array"));
    }

    @Test
    public void aRequestWithoutATemplateIsRefused() throws Exception {
        StatusMessage status = connected();
        start(mock(ReportGeneratorManager.class), status);

        HttpResponse<String> response = post("/api/v1/generate-report", "{\"parameters\":{}}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Field 'template' is required"));
        assertEquals(1, status.getInternalerrors());
    }

    @Test
    public void aBodyThatIsNotJsonIsRefused() throws Exception {
        StatusMessage status = connected();
        start(mock(ReportGeneratorManager.class), status);

        HttpResponse<String> response = post("/api/v1/generate-report", "pas du json");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Invalid JSON"));
        assertEquals(1, status.getInternalerrors());
    }

    @Test
    public void aGenerationThatThrowsIsAServerError() throws Exception {
        ReportGeneratorManager manager = mock(ReportGeneratorManager.class);
        when(manager.generateReport(any(MessageData.class))).thenThrow(new RuntimeException("template gone"));
        StatusMessage status = connected();
        start(manager, status);

        HttpResponse<String> response = post("/api/v1/generate-report", "{\"template\":\"t.jrxml\"}");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("template gone"));
        assertEquals(1, status.getInternalerrors());
    }

    @Test
    public void aPathThatWasNotWrittenIsAServerError() throws Exception {
        ReportGeneratorManager manager = mock(ReportGeneratorManager.class);
        when(manager.generateReport(any(MessageData.class)))
                .thenReturn(new File(folder.getRoot(), "absent.pdf").getAbsolutePath());
        StatusMessage status = connected();
        start(manager, status);

        HttpResponse<String> response = post("/api/v1/generate-report", "{\"template\":\"t.jrxml\"}");

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("was not created"));
        assertEquals(1, status.getInternalerrors());
    }

    @Test(expected = RuntimeException.class)
    public void aPortThatIsAlreadyTakenIsReportedAtStartup() throws Exception {
        try (ServerSocket taken = new ServerSocket(0)) {
            RestApiServer blocked = new RestApiServer(taken.getLocalPort(), mock(ReportGeneratorManager.class),
                    connected());
            try {
                blocked.start();
            } finally {
                blocked.stop();
            }
        }
    }

    private String template() throws Exception {
        File target = new File(folder.getRoot(), "simple.jrxml");
        try (InputStream in = getClass().getResourceAsStream("/simple.jrxml")) {
            Files.copy(in, target.toPath());
        }
        return target.getAbsolutePath();
    }
}
