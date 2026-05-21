package com.nyx.jasper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message data model for incoming queue messages
 * Contains the JRXML template path and parameters for report generation
 * Supports both object and array formats for parameters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageData {
    
    @JsonProperty("template")
    private String templatePath;
    
    private Map<String, Object> parameters;
    
    @JsonProperty("outputName")
    private String outputName;
    
    @JsonProperty("jsonUrl")
    private String jsonUrl;
    
    @JsonProperty("jdbc_driver")
    private String jdbcDriver;
    
    @JsonProperty("jdbc_url")
    private String jdbcUrl;
    
    @JsonProperty("jdbc_login")
    private String jdbcLogin;
    
    @JsonProperty("jdbc_password")
    private String jdbcPassword;

    public MessageData() {
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Set parameters - handles both object and array formats
     * Object format: {"param1": "value1", "param2": "value2"}
     * Array format: [{"name": "param1", "value": "value1"}, {"name": "param2", "value": "value2"}]
     */
    @SuppressWarnings("unchecked")
    @JsonSetter("parameters")
    public void setParametersFromJson(Object parametersInput) {
        if (parametersInput == null) {
            this.parameters = new HashMap<>();
            return;
        }
        
        // If it's already a Map, use it directly
        if (parametersInput instanceof Map) {
            this.parameters = (Map<String, Object>) parametersInput;
        }
        // If it's a List, convert from array format
        else if (parametersInput instanceof List) {
            List<?> list = (List<?>) parametersInput;
            this.parameters = new HashMap<>();
            
            for (Object item : list) {
                if (item instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    Object name = itemMap.get("name");
                    Object value = itemMap.get("value");
                    Object type = itemMap.get("type");

                    if (name != null && "interval".equals(type) && value instanceof List) {
                        List<?> interval = (List<?>) value;
                        Object startVal = interval.size() > 0 ? interval.get(0) : null;
                        Object endVal   = interval.size() > 1 ? interval.get(1) : null;
                        this.parameters.put(name + "_start", startVal);
                        this.parameters.put(name + "_end",   endVal);
                        this.parameters.put(name + "_startDT", toDate(startVal));
                        this.parameters.put(name + "_endDT",   toDate(endVal));
                    } else if (name != null) {
                        this.parameters.put(name.toString(), value);
                    }
                }
            }
        } else {
            this.parameters = new HashMap<>();
        }
    }
    
    /**
     * Set parameters from a Map (for programmatic use)
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    private Date toDate(Object value) {
        if (value == null) return null;
        try {
            return Date.from(Instant.parse(value.toString()));
        } catch (Exception e) {
            return null;
        }
    }

    public String getOutputName() {
        return outputName;
    }

    public void setOutputName(String outputName) {
        this.outputName = outputName;
    }

    public String getJsonUrl() {
        return jsonUrl;
    }

    public void setJsonUrl(String jsonUrl) {
        this.jsonUrl = jsonUrl;
    }

    public String getJdbcDriver() {
        return jdbcDriver;
    }

    public void setJdbcDriver(String jdbcDriver) {
        this.jdbcDriver = jdbcDriver;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getJdbcLogin() {
        return jdbcLogin;
    }

    public void setJdbcLogin(String jdbcLogin) {
        this.jdbcLogin = jdbcLogin;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public void setJdbcPassword(String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
    }

    @Override
    public String toString() {
        return "MessageData{" +
                "templatePath='" + templatePath + '\'' +
                ", parameters=" + parameters +
                ", outputName='" + outputName + '\'' +
                ", jsonUrl='" + jsonUrl + '\'' +
                ", jdbcDriver='" + jdbcDriver + '\'' +
                ", jdbcUrl='" + jdbcUrl + '\'' +
                ", jdbcLogin='" + jdbcLogin + '\'' +
                ", jdbcPassword='***'" +
                '}';
    }
}
