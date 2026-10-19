package com.nyx.jasper;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Main application that connects to ActiveMQ and listens to a queue.
 *
 * The JMS connection is supervised: if the broker restarts or the connection
 * is lost, the application closes the old connection and reconnects without
 * exiting, recreating the session, consumers and status publisher.
 */
public class Application {
    public static final String Version = "1.0.21";

    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final long RECONNECT_DELAY_MS = 5000L;

    private volatile boolean running = true;
    private final Object reconnectMonitor = new Object();
    private boolean reconnectRequested = false;

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private MessageConsumer uploadConsumer;
    private StatusPublisher statusPublisher;
    private RestApiServer restApiServer;
    private StatusMessage statusMessage;
    private ReportGeneratorManager reportGeneratorManager;

    public static void main(String[] args) {
        Application app = new Application();

        Runtime.getRuntime().addShutdownHook(new Thread(app::close, "shutdown-hook"));

        try {
            app.start();
        } catch (Exception e) {
            logger.error("Application terminated", e);
            System.exit(1);
        }
    }

    /**
     * Supervise the JMS connection: connect, wait for a failure/shutdown, close
     * and retry. Configuration errors still terminate the application.
     */
    public void start() throws Exception {
        Properties config = loadConfiguration();

        String outputDirectory = config.getProperty("jasper.output.directory", "reports");

        try {
            reportGeneratorManager = new ReportGeneratorManager(outputDirectory);
            logger.info("ReportGeneratorManager initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to initialize ReportGeneratorManager. Report generation will be disabled.", e);
            reportGeneratorManager = null;
        }

        while (running) {
            try {
                connectAndListen(config);
            } catch (Exception e) {
                logger.error("JMS connection lost or failed; reconnecting in {} ms", RECONNECT_DELAY_MS, e);
            } finally {
                closeJms();
            }

            if (!running) {
                break;
            }

            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        close();
    }

    /**
     * Create the JMS connection, session, consumers and status publisher, then
     * block until the connection is lost or the application is shut down.
     */
    private void connectAndListen(Properties config) throws JMSException {
        String brokerUrl = config.getProperty("activemq.broker.url", "tcp://localhost:61616");
        String username = config.getProperty("activemq.username");
        String password = config.getProperty("activemq.password");
        if (username == null || password == null) {
            throw new java.lang.IllegalStateException(
                    "activemq.username and activemq.password must be configured (properties file or environment)");
        }

        String queueName = config.getProperty("activemq.queue.name", "nyx.jasper.queue");
        String uploadQueueName = config.getProperty("activemq.upload.queue.name", "JASPER_UPLOAD");
        String statusTopicName = config.getProperty("activemq.status.topic", "RPN_MODULE_INFO");
        int statusIntervalSeconds = Integer.parseInt(config.getProperty("status.interval.seconds", "5"));
        String moduleName = config.getProperty("module.name", "nyx_jasper_creator");
        int restApiPort = Integer.parseInt(config.getProperty("rest.api.port", "8080"));
        boolean restApiEnabled = Boolean.parseBoolean(config.getProperty("rest.api.enabled", "true"));

        logger.info("Connecting to ActiveMQ broker: {}", brokerUrl);
        logger.info("Listening to queue: {}", queueName);
        logger.info("Listening to upload queue: {}", uploadQueueName);
        logger.info("Publishing status to topic: {}", statusTopicName);

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL(brokerUrl);
        connectionFactory.setUserName(username);
        connectionFactory.setPassword(password);

        connection = connectionFactory.createConnection();
        connection.setExceptionListener(this::handleJmsException);
        connection.start();

        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        statusPublisher = new StatusPublisher(
                session, statusTopicName, statusIntervalSeconds, moduleName, Application.Version, statusMessage);
        statusMessage = statusPublisher.getStatusMessage();
        statusPublisher.setConnectionLostCallback(this::requestReconnect);
        statusPublisher.start();

        if (restApiServer == null && restApiEnabled && reportGeneratorManager != null) {
            try {
                restApiServer = new RestApiServer(restApiPort, reportGeneratorManager, statusMessage);
                restApiServer.start();
            } catch (Exception e) {
                logger.error("Failed to start REST API server. Continuing with ActiveMQ only.", e);
                statusMessage.setInternalerrors(statusMessage.getInternalerrors() + 1);
            }
        } else if (restApiEnabled && reportGeneratorManager == null) {
            logger.warn("REST API server disabled because ReportGeneratorManager failed to initialize");
        }

        Destination destination = session.createQueue(queueName);
        consumer = session.createConsumer(destination);
        consumer.setMessageListener(new QueueMessageListener(
                statusMessage, queueName, reportGeneratorManager));

        Destination uploadDestination = session.createQueue(uploadQueueName);
        uploadConsumer = session.createConsumer(uploadDestination);
        uploadConsumer.setMessageListener(new TemplateUploadListener(statusMessage));

        logger.info("ActiveMQ listener started successfully");
        logger.info("Waiting for messages...");

        synchronized (reconnectMonitor) {
            while (running && !reconnectRequested) {
                try {
                    reconnectMonitor.wait(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            reconnectRequested = false;
        }
    }

    /**
     * ActiveMQ connection exception listener: schedule a reconnect.
     */
    private void handleJmsException(JMSException exception) {
        logger.warn("JMS exception; scheduling reconnect: {}", exception.getMessage());
        requestReconnect();
    }

    /**
     * Wake the supervisor so it closes the current JMS objects and reconnects.
     */
    private void requestReconnect() {
        synchronized (reconnectMonitor) {
            reconnectRequested = true;
            reconnectMonitor.notifyAll();
        }
    }

    /**
     * Close the current JMS objects (but not the REST API or report manager).
     */
    private void closeJms() {
        if (statusPublisher != null) {
            statusPublisher.stop();
            statusPublisher = null;
        }
        if (uploadConsumer != null) {
            try {
                uploadConsumer.close();
            } catch (JMSException e) {
                logger.debug("Error closing upload consumer", e);
            }
            uploadConsumer = null;
        }
        if (consumer != null) {
            try {
                consumer.close();
            } catch (JMSException e) {
                logger.debug("Error closing consumer", e);
            }
            consumer = null;
        }
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                logger.debug("Error closing session", e);
            }
            session = null;
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                logger.debug("Error closing connection", e);
            }
            connection = null;
        }
        if (statusMessage != null) {
            statusMessage.setConnections(0);
        }
    }

    /**
     * Graceful shutdown.
     */
    public void close() {
        running = false;
        requestReconnect();

        if (restApiServer != null) {
            try {
                restApiServer.stop();
            } catch (Exception e) {
                logger.debug("Error stopping REST API server", e);
            }
            restApiServer = null;
        }

        closeJms();
        logger.info("Application stopped");
    }

    /**
     * Load configuration from environment variables or properties file
     * Environment variables take precedence over properties file
     */
    private Properties loadConfiguration() throws IOException {
        Properties properties = new Properties();

        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                logger.info("Configuration loaded from application.properties");
            } else {
                logger.warn("application.properties not found, using defaults and environment variables");
            }
        }

        overrideWithEnvironmentVariables(properties);
        return properties;
    }

    /**
     * Override properties with environment variables
     */
    private void overrideWithEnvironmentVariables(Properties properties) {
        String[] envVars = {
            "ACTIVEMQ_BROKER_URL",
            "ACTIVEMQ_USERNAME",
            "ACTIVEMQ_PASSWORD",
            "ACTIVEMQ_QUEUE_NAME",
            "ACTIVEMQ_UPLOAD_QUEUE_NAME",
            "ACTIVEMQ_STATUS_TOPIC",
            "STATUS_INTERVAL_SECONDS",
            "MODULE_NAME",
            "JASPER_OUTPUT_DIRECTORY",
            "REST_API_PORT",
            "REST_API_ENABLED"
        };

        String[] propertyKeys = {
            "activemq.broker.url",
            "activemq.username",
            "activemq.password",
            "activemq.queue.name",
            "activemq.upload.queue.name",
            "activemq.status.topic",
            "status.interval.seconds",
            "module.name",
            "jasper.output.directory",
            "rest.api.port",
            "rest.api.enabled"
        };

        for (int i = 0; i < envVars.length; i++) {
            String envValue = System.getenv(envVars[i]);
            if (envValue != null && !envValue.trim().isEmpty()) {
                properties.setProperty(propertyKeys[i], envValue);
                logger.info("Using environment variable {} for {}", envVars[i], propertyKeys[i]);
            }
        }
    }
}
