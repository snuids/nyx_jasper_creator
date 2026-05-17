package com.nyx.jasper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * REST API server for report generation
 * Provides HTTP endpoint as alternative to ActiveMQ queue
 */
public class RestApiServer {
    private static final Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    
    private final Javalin app;
    private final ReportGeneratorManager reportGeneratorManager;
    private final StatusMessage statusMessage;
    private final ObjectMapper objectMapper;
    private final int port;

    public RestApiServer(int port, ReportGeneratorManager reportGeneratorManager, StatusMessage statusMessage) {
        this.port = port;
        this.reportGeneratorManager = reportGeneratorManager;
        this.statusMessage = statusMessage;
        this.objectMapper = new ObjectMapper();
        this.app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });
        
        setupRoutes();
    }

    /**
     * Configure REST API routes
     */
    private void setupRoutes() {
        // Health check endpoint
        app.get("/health", ctx -> {
            ctx.json(java.util.Map.of(
                "status", "UP",
                "module", statusMessage.getModule(),
                "version", statusMessage.getVersion()
            ));
        });

        // Report generation endpoint
        app.post("/api/v1/generate-report", this::handleReportGeneration);

        // Status endpoint
        app.get("/api/v1/status", ctx -> {
            ctx.json(statusMessage);
        });
    }

    /**
     * Handle report generation request
     */
    private void handleReportGeneration(Context ctx) {
        try {
            // Parse the JSON request body
            String requestBody = ctx.body();
            
            if (requestBody == null || requestBody.trim().isEmpty()) {
                ctx.status(400).json(java.util.Map.of(
                    "error", "Request body is required",
                    "message", "Please provide a JSON message with template, parameters, and optionally jsonUrl"
                ));
                return;
            }

            logger.info("Received REST API request for report generation");
            logger.debug("Request body: {}", requestBody);

            // Parse the message data
            MessageData messageData = objectMapper.readValue(requestBody, MessageData.class);

            // Validate required fields
            if (messageData.getTemplatePath() == null || messageData.getTemplatePath().trim().isEmpty()) {
                ctx.status(400).json(java.util.Map.of(
                    "error", "Missing required field",
                    "message", "Field 'template' is required"
                ));
                statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
                return;
            }

            // Generate the report
            String reportPath = reportGeneratorManager.generateReport(messageData);

            // Verify the report file exists
            File reportFile = new File(reportPath);
            if (!reportFile.exists() || !reportFile.isFile()) {
                logger.error("Report file was not created: {}", reportPath);
                ctx.status(500).json(java.util.Map.of(
                    "error", "Report generation failed",
                    "message", "Report file was not created at expected path: " + reportPath
                ));
                statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
                return;
            }

            // Update statistics
            statusMessage.incrementMessages();

            // Return success response
            ctx.status(200).json(java.util.Map.of(
                "success", true,
                "message", "Report generated successfully",
                "reportPath", reportPath,
                "template", messageData.getTemplatePath()
            ));

            logger.info("Report generated successfully via REST API: {}", reportPath);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to parse JSON request", e);
            ctx.status(400).json(java.util.Map.of(
                "error", "Invalid JSON",
                "message", e.getMessage()
            ));
            statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
            
        } catch (Exception e) {
            logger.error("Failed to generate report via REST API", e);
            ctx.status(500).json(java.util.Map.of(
                "error", "Report generation failed",
                "message", e.getMessage()
            ));
            statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
        }
    }

    /**
     * Start the REST API server
     */
    public void start() {
        try {
            app.start(port);
            logger.info("REST API server started on port {}", port);
            logger.info("Endpoints available:");
            logger.info("  POST http://localhost:{}/api/v1/generate-report", port);
            logger.info("  GET  http://localhost:{}/api/v1/status", port);
            logger.info("  GET  http://localhost:{}/health", port);
        } catch (Exception e) {
            logger.error("Failed to start REST API server", e);
            throw new RuntimeException("Failed to start REST API server", e);
        }
    }

    /**
     * Stop the REST API server
     */
    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("REST API server stopped");
        }
    }
}
