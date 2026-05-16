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
 * Publishes status messages to a topic at regular intervals
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

    /**
     * Create a status publisher
     * 
     * @param session JMS session
     * @param topicName Topic name to publish to
     * @param intervalSeconds Interval in seconds between status updates
     * @param moduleName Module name
     * @param version Module version
     * @throws JMSException if unable to create producer
     */
    public StatusPublisher(Session session, String topicName, int intervalSeconds, 
                          String moduleName, String version) throws JMSException {
        this.session = session;
        this.topicName = topicName;
        this.intervalMillis = intervalSeconds * 1000L;
        this.objectMapper = new ObjectMapper();
        this.timer = new Timer("StatusPublisher", true);
        this.running = new AtomicBoolean(false);
        
        // Record start time
        this.startTimeTs = System.currentTimeMillis() / 1000.0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        this.startTime = sdf.format(new Date());
        
        // Create topic and producer
        Topic topic = session.createTopic(topicName);
        this.producer = session.createProducer(topic);
        this.producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        
        // Initialize status message
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
        this.statusMessage.setConnections(1);
        
        logger.info("StatusPublisher created for topic: {}", topicName);
    }

    /**
     * Start publishing status messages
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
     * Stop publishing status messages
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            timer.cancel();
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
     * Get the status message for external updates
     */
    public StatusMessage getStatusMessage() {
        return statusMessage;
    }

    /**
     * Publish a status update to the topic
     */
    private void publishStatus() {
        try {
            // Update the sent counter for this topic
            statusMessage.incrementSent("/topic/" + topicName);
            
            // Convert status to JSON
            String jsonStatus = objectMapper.writeValueAsString(statusMessage);
            
            // Create and send message
            TextMessage message = session.createTextMessage(jsonStatus);
            producer.send(message);
            
            logger.debug("Status published to {}: {}", topicName, jsonStatus);
            
        } catch (Exception e) {
            logger.error("Error publishing status", e);
            statusMessage.setErrors(statusMessage.getErrors() + 1);
        }
    }
}
