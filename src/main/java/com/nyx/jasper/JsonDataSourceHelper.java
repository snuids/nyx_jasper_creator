package com.nyx.jasper;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.json.data.JsonDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Helper class for creating JSON datasources from URLs
 */
public class JsonDataSourceHelper {

    private static final Logger logger = LoggerFactory.getLogger(JsonDataSourceHelper.class);
    private static final int TIMEOUT_MILLIS = 30000; // 30 seconds

    /**
     * Create a JsonDataSource from a URL
     * 
     * @param jsonUrl URL to fetch JSON data from
     * @return JsonDataSource for JasperReports
     * @throws JRException if unable to create datasource
     * @throws IOException if unable to fetch data from URL
     */
    public static JsonDataSource createFromUrl(String jsonUrl) throws JRException, IOException {
        if (jsonUrl == null || jsonUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON URL cannot be null or empty");
        }

        logger.info("Fetching JSON data from URL: {}", jsonUrl);
        
        String jsonData = fetchJsonFromUrl(jsonUrl);
        
        if (jsonData == null || jsonData.trim().isEmpty()) {
            throw new IOException("No data received from URL: " + jsonUrl);
        }
        
        logger.info("Successfully fetched JSON data ({} bytes)", jsonData.length());
        
        // Create JsonDataSource from the fetched data
        InputStream jsonStream = new ByteArrayInputStream(jsonData.getBytes(StandardCharsets.UTF_8));
        return new JsonDataSource(jsonStream);
    }

    /**
     * Fetch JSON data from a URL
     * 
     * @param urlString URL to fetch from
     * @return JSON data as string
     * @throws IOException if unable to fetch data
     */
    private static String fetchJsonFromUrl(String urlString) throws IOException {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = null;
        
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode + " for URL: " + urlString);
            }
            
            // Read the response
            try (InputStream inputStream = connection.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Check if a string is a valid HTTP/HTTPS URL
     * 
     * @param urlString String to check
     * @return true if valid URL, false otherwise
     */
    public static boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }
        
        try {
            URL url = URI.create(urlString).toURL();
            String protocol = url.getProtocol().toLowerCase();
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (Exception e) {
            return false;
        }
    }
}
