package com.nyx.jasper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model for a single parameter item in array format
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParameterItem {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("value")
    private Object value;

    public ParameterItem() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ParameterItem{" +
                "name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", value=" + value +
                '}';
    }
}
