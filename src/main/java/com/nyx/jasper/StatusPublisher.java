package com.nyx.jasper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes status messages to a topic at regular intervals.
 *
 * The publisher is recreated for every JMS connection, but it can reuse an
 * existing StatusMessage so counters and the application start time survive
 * a reconnect.
 */
public class StatusPublisher {

    private static final Logger logger = LoggerFactory.getLogger(StatusPublisher.class);

    private final Session session;
    private final MessageProducer producer;
    private final ObjectMapper objectMapper;
    private final StatusMessage statusMessage;
    private final Timer timer;
    private final AtomicBoolean running;

    private final String topicName;
    private final long intervalMillis;
    private final double startTimeTs;
    private final String startTime;

    private volatile Runnable connectionLostCallback;

    public StatusPublisher(Session session, String topicName, int intervalSeconds,
                           String moduleName, String version) throws JMSException {
        this(session, topicName, intervalSeconds, moduleName, version, null);
    }

    /**
     * Create a status publisher.
     *
     * @param existing if non-null, reuse this StatusMessage (counters, start time)
     *                 instead of creating a new one. This is how the status
     *                 survives an ActiveMQ reconnect.
     */
    public StatusPublisher(Session session, String topicName, int intervalSeconds,
                           String moduleName, String version, StatusMessage existing) throws JMSException {
        this.session = session;
        this.topicName = topicName;
        this.intervalMillis = intervalSeconds * 1000L;
        this.objectMapper = new ObjectMapper();
        this.timer = new Timer("StatusPublisher", true);
        this.running = new AtomicBoolean(false);

        if (existing == null) {
            this.startTimeTs = System.currentTimeMillis() / 1000.0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
            this.startTime = sdf.format(new Date());

            this.statusMessage = new StatusMessage();
            this.statusMessage.setError("OK");
            this.statusMessage.setType("lifesign");
            this.statusMessage.setEventtype("lifesign");
            this.statusMessage.setModule(moduleName);
            this.statusMessage.setVersion(version);
            this.statusMessage.setAlive(1);
            this.statusMessage.setErrors(0);
            this.statusMessage.setInternalerrors(0);
            this.statusMessage.setHeartbeaterrors(0);
            this.statusMessage.setMessages(0);
            this.statusMessage.setAmqclientversion("1.0.0");
            this.statusMessage.setStarttimets(startTimeTs);
            this.statusMessage.setStarttime(startTime);
        } else {
            this.startTimeTs = existing.getStarttimets();
            this.startTime = existing.getStarttime();
            this.statusMessage = existing;
            this.statusMessage.setModule(moduleName);
            this.statusMessage.setVersion(version);
            this.statusMessage.setError("OK");
            this.statusMessage.setAlive(1);
        }
        this.statusMessage.setConnections(1);

        Topic topic = session.createTopic(topicName);
        this.producer = session.createProducer(topic);
        this.producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        logger.info("StatusPublisher created for topic: {} (reconnect={})", topicName, existing != null);
    }

    /**
     * Set the callback invoked when the JMS session can no longer publish.
     */
    public void setConnectionLostCallback(Runnable callback) {
        this.connectionLostCallback = callback;
    }

    /**
     * Start publishing status messages.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    publishStatus();
                }
            }, 0, intervalMillis);

            logger.info("StatusPublisher started, publishing every {} seconds", intervalMillis / 1000);
        }
    }

    /**
     * Stop publishing status messages.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            timer.cancel();
            statusMessage.setConnections(0);
            try {
                if (producer != null) {
                    producer.close();
                }
            } catch (JMSException e) {
                logger.error("Error closing producer", e);
            }
            logger.info("StatusPublisher stopped");
        }
    }

    /**
     * Get the status message for external updates.
     */
    public StatusMessage getStatusMessage() {
        return statusMessage;
    }

    /**
     * Publish a status update to the topic.
     */
    private void publishStatus() {
        try {
            statusMessage.incrementSent("/topic/" + topicName);

            String jsonStatus = objectMapper.writeValueAsString(statusMessage);

            TextMessage message = session.createTextMessage(jsonStatus);
            producer.send(message);

            logger.debug("Status published to {}: {}", topicName, jsonStatus);

        } catch (Exception e) {
            logger.error("Error publishing status", e);
            statusMessage.setErrors(statusMessage.getErrors() + 1);

            // A closed session/producer is a connection loss; ask the
            // supervisor to recreate the JMS objects.
            if (e instanceof JMSException) {
                Runnable callback = connectionLostCallback;
                if (callback != null) {
                    try {
                        callback.run();
                    } catch (Exception callbackError) {
                        logger.debug("Error invoking connection-lost callback", callbackError);
                    }
                }
            }
        }
    }
}
