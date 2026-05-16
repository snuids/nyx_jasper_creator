package com.nyx.jasper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.nio.charset.StandardCharsets;

/**
 * JMS Message Listener that processes messages from ActiveMQ queue
 * Supports both TextMessage and BytesMessage formats
 */
public class QueueMessageListener implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(QueueMessageListener.class);
    
    private final StatusMessage statusMessage;
    private final String queueName;
    private final ReportGeneratorManager reportGeneratorManager;
    private final ObjectMapper objectMapper;

    public QueueMessageListener(StatusMessage statusMessage, String queueName, ReportGeneratorManager reportGeneratorManager) {
        this.statusMessage = statusMessage;
        this.queueName = queueName;
        this.reportGeneratorManager = reportGeneratorManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onMessage(Message message) {
        try {
            String messageContent = null;
            
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                messageContent = textMessage.getText();
                logger.info("Received TextMessage: {}", messageContent);
                
            } else if (message instanceof BytesMessage) {
                BytesMessage bytesMessage = (BytesMessage) message;
                long bodyLength = bytesMessage.getBodyLength();
                
                if (bodyLength > 0) {
                    byte[] bytes = new byte[(int) bodyLength];
                    bytesMessage.readBytes(bytes);
                    messageContent = new String(bytes, StandardCharsets.UTF_8);
                    logger.info("Received BytesMessage ({} bytes): {}", bodyLength, messageContent);
                } else {
                    logger.warn("Received empty BytesMessage");
                }
                
            } else {
                logger.warn("Received unsupported message type: {}", message.getClass().getName());
            }
            
            if (messageContent != null) {
                // Update statistics
                statusMessage.incrementReceived("/queue/" + queueName);
                statusMessage.incrementMessages();
                
                // Process the message
                processMessage(messageContent);
                
                // Acknowledge the message
                message.acknowledge();
            }
            
        } catch (JMSException e) {
            logger.error("Error processing message", e);
            statusMessage.setErrors(statusMessage.getErrors() + 1);
        }
    }

    /**
     * Process the received message by generating a Jasper report
     * 
     * @param messageText the message content in JSON format
     */
    private void processMessage(String messageText) {
        logger.info("Processing message: {}", messageText);
        
        try {
            // Parse JSON message to extract template path and parameters
            MessageData messageData = objectMapper.readValue(messageText, MessageData.class);
            logger.info("Parsed message data: {}", messageData);
            
            // Validate message data
            if (messageData.getTemplatePath() == null || messageData.getTemplatePath().isEmpty()) {
                logger.error("Message missing required 'template' field");
                statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
                return;
            }
            
            // Generate Jasper report from the message data
            String reportPath = reportGeneratorManager.generateReport(messageData);
            logger.info("Jasper report generated successfully: {}", reportPath);
            
        } catch (Exception e) {
            logger.error("Failed to process message or generate Jasper report", e);
            statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
        }
    }
}
