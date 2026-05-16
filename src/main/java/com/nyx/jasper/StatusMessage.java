package com.nyx.jasper;

import java.util.HashMap;
import java.util.Map;

/**
 * Status message model representing the module health status
 */
public class StatusMessage {
    
    private String error;
    private String type;
    private String eventtype;
    private String module;
    private String version;
    private int alive;
    private int errors;
    private int internalerrors;
    private int heartbeaterrors;
    private int messages;
    private Map<String, Integer> received;
    private Map<String, Integer> sent;
    private String amqclientversion;
    private double starttimets;
    private String starttime;
    private int connections;
    private String icon;

    public StatusMessage() {
        this.received = new HashMap<>();
        this.sent = new HashMap<>();
        this.icon = "calendar";
    }

    // Getters and Setters
    
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEventtype() {
        return eventtype;
    }

    public void setEventtype(String eventtype) {
        this.eventtype = eventtype;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getAlive() {
        return alive;
    }

    public void setAlive(int alive) {
        this.alive = alive;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public int getInternalerrors() {
        return internalerrors;
    }

    public void setInternalerrors(int internalerrors) {
        this.internalerrors = internalerrors;
    }

    public int getHeartbeaterrors() {
        return heartbeaterrors;
    }

    public void setHeartbeaterrors(int heartbeaterrors) {
        this.heartbeaterrors = heartbeaterrors;
    }

    public int getMessages() {
        return messages;
    }

    public void setMessages(int messages) {
        this.messages = messages;
    }

    public Map<String, Integer> getReceived() {
        return received;
    }

    public void setReceived(Map<String, Integer> received) {
        this.received = received;
    }

    public Map<String, Integer> getSent() {
        return sent;
    }

    public void setSent(Map<String, Integer> sent) {
        this.sent = sent;
    }

    public String getAmqclientversion() {
        return amqclientversion;
    }

    public void setAmqclientversion(String amqclientversion) {
        this.amqclientversion = amqclientversion;
    }

    public double getStarttimets() {
        return starttimets;
    }

    public void setStarttimets(double starttimets) {
        this.starttimets = starttimets;
    }

    public String getStarttime() {
        return starttime;
    }

    public void setStarttime(String starttime) {
        this.starttime = starttime;
    }

    public int getConnections() {
        return connections;
    }

    public void setConnections(int connections) {
        this.connections = connections;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * Increment the sent counter for a specific topic
     */
    public void incrementSent(String topic) {
        sent.merge(topic, 1, Integer::sum);
    }

    /**
     * Increment the received counter for a specific topic/queue
     */
    public void incrementReceived(String destination) {
        received.merge(destination, 1, Integer::sum);
    }

    /**
     * Increment the messages counter
     */
    public void incrementMessages() {
        this.messages++;
    }
}
