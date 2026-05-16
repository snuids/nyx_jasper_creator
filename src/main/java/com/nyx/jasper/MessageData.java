package com.nyx.jasper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Message data model for incoming queue messages
 * Contains the JRXML template path and parameters for report generation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageData {
    
    @JsonProperty("template")
    private String templatePath;
    
    @JsonProperty("parameters")
    private Map<String, Object> parameters;
    
    @JsonProperty("outputName")
    private String outputName;
    
    @JsonProperty("jsonUrl")
    private String jsonUrl;

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

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
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

    @Override
    public String toString() {
        return "MessageData{" +
                "templatePath='" + templatePath + '\'' +
                ", parameters=" + parameters +
                ", outputName='" + outputName + '\'' +
                ", jsonUrl='" + jsonUrl + '\'' +
                '}';
    }
}
