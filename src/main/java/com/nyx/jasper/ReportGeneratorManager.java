package com.nyx.jasper;

import net.sf.jasperreports.engine.JRException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple JasperReportGenerators for different templates
 * Caches compiled reports for better performance
 */
public class ReportGeneratorManager {

    private static final Logger logger = LoggerFactory.getLogger(ReportGeneratorManager.class);
    
    private final String outputDirectory;
    private final Map<String, JasperReportGenerator> generatorCache;

    /**
     * Create a ReportGeneratorManager
     * 
     * @param outputDirectory Directory where generated reports will be saved
     * @throws IOException if unable to create output directory
     */
    public ReportGeneratorManager(String outputDirectory) throws IOException {
        this.outputDirectory = outputDirectory;
        this.generatorCache = new ConcurrentHashMap<>();
        
        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(outputDirectory);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            logger.info("Created output directory: {}", outputDirectory);
        }
    }

    /**
     * Generate a report from message data
     * 
     * @param messageData Message data containing template path and parameters
     * @return The path to the generated PDF file
     * @throws JRException if report generation fails
     */
    public String generateReport(MessageData messageData) throws JRException {
        String templatePath = messageData.getTemplatePath();
        
        if (templatePath == null || templatePath.isEmpty()) {
            throw new IllegalArgumentException("Template path is required in message data");
        }
        
        logger.info("Generating report with template: {}", templatePath);
        
        // Get or create generator for this template
        JasperReportGenerator generator = getOrCreateGenerator(templatePath);
        
        // Convert message data to string for default MESSAGE_DATA parameter
        String messageDataStr = messageData.toString();
        
        // Generate report with parameters and optional JSON datasource
        String jsonUrl = messageData.getJsonUrl();
        if (jsonUrl != null && !jsonUrl.trim().isEmpty()) {
            logger.info("JSON datasource URL provided: {}", jsonUrl);
        }
        
        return generator.generateReport(messageDataStr, messageData.getParameters(), jsonUrl);
    }

    /**
     * Get or create a JasperReportGenerator for the specified template
     * Generators are cached for performance
     * 
     * @param templatePath Path to the JRXML template
     * @return A JasperReportGenerator for the template
     * @throws JRException if template compilation fails
     */
    private JasperReportGenerator getOrCreateGenerator(String templatePath) throws JRException {
        return generatorCache.computeIfAbsent(templatePath, path -> {
            try {
                logger.info("Creating new JasperReportGenerator for template: {}", path);
                return new JasperReportGenerator(path, outputDirectory);
            } catch (JRException | IOException e) {
                logger.error("Failed to create JasperReportGenerator for template: {}", path, e);
                throw new RuntimeException("Failed to compile template: " + path, e);
            }
        });
    }

    /**
     * Reload a specific template (useful when template is updated)
     * 
     * @param templatePath Path to the JRXML template to reload
     * @throws JRException if template recompilation fails
     */
    public void reloadTemplate(String templatePath) throws JRException {
        logger.info("Reloading template: {}", templatePath);
        generatorCache.remove(templatePath);
        getOrCreateGenerator(templatePath);
        logger.info("Template reloaded successfully: {}", templatePath);
    }

    /**
     * Clear all cached generators
     */
    public void clearCache() {
        logger.info("Clearing all cached report generators");
        generatorCache.clear();
    }

    /**
     * Get the number of cached generators
     */
    public int getCacheSize() {
        return generatorCache.size();
    }

    /**
     * Get the output directory
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }
}
