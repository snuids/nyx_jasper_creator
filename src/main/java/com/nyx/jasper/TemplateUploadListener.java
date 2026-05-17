package com.nyx.jasper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Listener for JRXML template upload messages.
 * Receives base64-encoded JRXML templates and saves them to jasperdef/{login} folder.
 */
public class TemplateUploadListener implements MessageListener {
    private static final Logger logger = LoggerFactory.getLogger(TemplateUploadListener.class);
    private static final String JASPER_DEF_FOLDER = "jasperdef";
    private final StatusMessage statusMessage;
    private final ObjectMapper objectMapper;

    public TemplateUploadListener(StatusMessage statusMessage) {
        this.statusMessage = statusMessage;
        this.objectMapper = new ObjectMapper();
        ensureJasperDefFolderExists();
    }

    /**
     * Create jasperdef folder if it doesn't exist
     */
    private void ensureJasperDefFolderExists() {
        try {
            Path jasperDefPath = Paths.get(JASPER_DEF_FOLDER);
            if (!Files.exists(jasperDefPath)) {
                Files.createDirectories(jasperDefPath);
                logger.info("Created jasperdef folder: {}", jasperDefPath.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create jasperdef folder", e);
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            // Extract filename from message headers
            String filename = message.getStringProperty("file");
            if (filename == null || filename.trim().isEmpty()) {
                logger.error("No filename found in message headers");
                statusMessage.incrementErrors();
                message.acknowledge();
                return;
            }

            // Extract login from user header
            String userJson = message.getStringProperty("user");
            String login = extractLogin(userJson);
            if (login == null) {
                logger.error("Failed to extract login from user header");
                statusMessage.incrementErrors();
                message.acknowledge();
                return;
            }

            logger.info("Received template upload request for file: {} from user: {}", filename, login);
            
            // Get base64 content from message body
            String base64Content = extractMessageBody(message);
            if (base64Content == null) {
                logger.error("Failed to extract message body");
                statusMessage.incrementErrors();
                message.acknowledge();
                return;
            }

            // Decode base64 and save to file
            saveTemplate(login, filename, base64Content);
            
            // Update statistics
            statusMessage.incrementMessages();
            statusMessage.incrementReceived(message.getJMSDestination().toString());
            
            // Acknowledge the message
            message.acknowledge();
            
            logger.info("Successfully saved template: {} for user: {}", filename, login);

        } catch (Exception e) {
            logger.error("Error processing template upload message", e);
            statusMessage.incrementErrors();
            try {
                message.acknowledge();
            } catch (Exception ackEx) {
                logger.error("Failed to acknowledge message", ackEx);
            }
        }
    }

    /**
     * Extract login from user JSON header
     */
    private String extractLogin(String userJson) {
        if (userJson == null || userJson.trim().isEmpty()) {
            return null;
        }
        
        try {
            JsonNode userNode = objectMapper.readT/{login} folder
     */
    private void saveTemplate(String login, String filename, String base64Content) throws IOException {
        // Remove any whitespace/newlines from base64 string
        String cleanBase64 = base64Content.replaceAll("\\s+", "");
        
        // Decode base64
        byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64);
        
        // Create user-specific directory
        Path userDir = Paths.get(JASPER_DEF_FOLDER, login);
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir);
            logger.info("Created directory: {}", userDir.toAbsolutePath());
        }
        
        // Save to jasperdef/{login} folder
        Path outputPath = userDir.resolve(

    /**
     * Extract message body as string
     */
    private String extractMessageBody(Message message) throws Exception {
        if (message instanceof TextMessage) {
            return ((TextMessage) message).getText();
        } else if (message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            long bodyLength = bytesMessage.getBodyLength();
            byte[] bytes = new byte[(int) bodyLength];
            bytesMessage.readBytes(bytes);
            return new String(bytes, "UTF-8");
        } else {
            logger.error("Unsupported message type: {}", message.getClass().getName());
            return null;
        }
    }

    /**
     * Decode base64 content and save to jasperdef folder
     */
    private void saveTemplate(String filename, String base64Content) throws IOException {
        // Remove any whitespace/newlines from base64 string
        String cleanBase64 = base64Content.replaceAll("\\s+", "");
        
        // Decode base64
        byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64);
        
        // Save to jasperdef folder
        Path outputPath = Paths.get(JASPER_DEF_FOLDER, filename);
        
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            fos.write(decodedBytes);
            logger.info("Template saved to: {}", outputPath.toAbsolutePath());
        }
    }
}
