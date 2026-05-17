package com.nyx.jasper;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Main application that connects to ActiveMQ and listens to a queue
 */
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private MessageConsumer uploadConsumer;
    private StatusPublisher statusPublisher;

    public static void main(String[] args) {
        Application app = new Application();
        
        // Add shutdown hook to gracefully close connections
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down application...");
            app.close();
        }));

        try {
            app.start();
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }

    /**
     * Start the ActiveMQ listener
     */
    public void start() throws JMSException, IOException {
        // Load configuration
        Properties config = loadConfiguration();
        
        String brokerUrl = config.getProperty("activemq.broker.url", "tcp://localhost:61616");
        String username = config.getProperty("activemq.username", "admin");
        String password = config.getProperty("activemq.password", "admin");
        String queueName = config.getProperty("activemq.queue.name", "nyx.jasper.queue");
        String uploadQueueName = config.getProperty("activemq.upload.queue.name", "JASPER_UPLOAD");
        String statusTopicName = config.getProperty("activemq.status.topic", "RPN_MODULE_INFO");
        int statusIntervalSeconds = Integer.parseInt(config.getProperty("status.interval.seconds", "5"));
        String moduleName = config.getProperty("module.name", "nyx_jasper_creator");
        String moduleVersion = config.getProperty("module.version", "1.0.0");
        String outputDirectory = config.getProperty("jasper.output.directory", "reports");
        
        logger.info("Connecting to ActiveMQ broker: {}", brokerUrl);
        logger.info("Listening to queue: {}", queueName);
        logger.info("Listening to upload queue: {}", uploadQueueName);
        logger.info("Publishing status to topic: {}", statusTopicName);
        logger.info("Report output directory: {}", outputDirectory);

        // Create connection factory
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL(brokerUrl);
        connectionFactory.setUserName(username);
        connectionFactory.setPassword(password);

        // Create connection
        connection = connectionFactory.createConnection();
        connection.start();

        // Create session
        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        // Create and start status publisher
        statusPublisher = new StatusPublisher(session, statusTopicName, statusIntervalSeconds, 
                                             moduleName, moduleVersion);
        statusPublisher.start();

        // Create report generator manager
        ReportGeneratorManager reportGeneratorManager = null;
        try {
            reportGeneratorManager = new ReportGeneratorManager(outputDirectory);
            logger.info("ReportGeneratorManager initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to initialize ReportGeneratorManager. Report generation will be disabled.", e);
            statusPublisher.getStatusMessage().setInternalerrors(
                statusPublisher.getStatusMessage().getInternalerrors() + 1
            );
        }

        // Create queue
        Destination destination = session.createQueue(queueName);

        // Create consumer
        consumer = session.createConsumer(destination);

        // Set message listener with status tracking and report generation
        consumer.setMessageListener(new QueueMessageListener(
            statusPublisher.getStatusMessage(), queueName, reportGeneratorManager));

        // Create upload queue consumer
        Destination uploadDestination = session.createQueue(uploadQueueName);
        uploadConsumer = session.createConsumer(uploadDestination);
        
        // Set template upload listener
        uploadConsumer.setMessageListener(new TemplateUploadListener(
            statusPublisher.getStatusMessage()));

        logger.info("ActiveMQ listener started successfully");
        logger.info("Waiting for messages...");

        // Keep the application running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.warn("Application interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Load configuration from properties file
     */
    private Properties loadConfiguration() throws IOException {
        Properties properties = new Properties();
        
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                logger.info("Configuration loaded from application.properties");
            } else {
                logger.warn("application.properties not found, using default values");
            }
        }
        
        return properties;
    }

    /**
     * Close all connections gracefully
     */
    public void close() {
        try {
            if (uploadConsumer != null) {
                uploadConsumer.close();
                logger.info("Upload consumer closed");
            }
            if (statusPublisher != null) {
                statusPublisher.stop();
                logger.info("Status publisher stopped");
            }
            if (consumer != null) {
                consumer.close();
                logger.info("Consumer closed");
            }
            if (session != null) {
                session.close();
                logger.info("Session closed");
            }
            if (connection != null) {
                connection.close();
                logger.info("Connection closed");
            }
        } catch (JMSException e) {
            logger.error("Error closing connections", e);
        }
    }
}
