package com.nyx.jasper;

import net.sf.jasperreports.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JasperReports report generator
 * Loads JRXML templates and generates PDF reports
 */
public class JasperReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(JasperReportGenerator.class);
    
    private final String jrxmlPath;
    private final String outputDirectory;
    private JasperReport jasperReport;

    /**
     * Create a JasperReportGenerator
     * 
     * @param jrxmlPath Path to the JRXML template file
     * @param outputDirectory Directory where generated reports will be saved
     * @throws JRException if unable to compile the JRXML template
     */
    public JasperReportGenerator(String jrxmlPath, String outputDirectory) throws JRException, IOException {
        this.jrxmlPath = jrxmlPath;
        this.outputDirectory = outputDirectory;
        
        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(outputDirectory);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            logger.info("Created output directory: {}", outputDirectory);
        }
        
        // Load and compile the JRXML template
        compileTemplate();
    }

    /**
     * Compile the JRXML template into a JasperReport
     */
    private void compileTemplate() throws JRException, IOException {
        logger.info("Compiling JRXML template: {}", jrxmlPath);
        
        File jrxmlFile = new File(jrxmlPath);
        if (!jrxmlFile.exists()) {
            // Try to load from classpath
            InputStream jrxmlStream = getClass().getClassLoader().getResourceAsStream(jrxmlPath);
            if (jrxmlStream != null) {
                logger.info("Loading JRXML from classpath: {}", jrxmlPath);
                jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            } else {
                throw new FileNotFoundException("JRXML template not found: " + jrxmlPath);
            }
        } else {
            jasperReport = JasperCompileManager.compileReport(jrxmlPath);
        }
        
        logger.info("JRXML template compiled successfully");
    }

    /**
     * Generate a PDF report from the message data
     * 
     * @param messageData The message data to use in the report
     * @return The path to the generated PDF file
     * @throws JRException if report generation fails
     */
    public String generateReport(String messageData) throws JRException {
        return generateReport(messageData, null, null, null);
    }

    /**
     * Generate a PDF report with custom parameters
     * 
     * @param messageData The message data to use in the report
     * @param additionalParams Additional parameters for the report
     * @return The path to the generated PDF file
     * @throws JRException if report generation fails
     */
    public String generateReport(String messageData, Map<String, Object> additionalParams) throws JRException {
        return generateReport(messageData, additionalParams, null, null);
    }

    /**
     * Generate a PDF report with custom parameters and optional JSON datasource
     * 
     * @param messageData The message data to use in the report
     * @param additionalParams Additional parameters for the report
     * @param jsonUrl URL to fetch JSON data for datasource (optional)
     * @param outputName Custom output name for the PDF file (optional)
     * @return The path to the generated PDF file
     * @throws JRException if report generation fails
     */
    public String generateReport(String messageData, Map<String, Object> additionalParams, String jsonUrl, String outputName) throws JRException {
        logger.info("Generating report from message data");
        
        // Prepare parameters
        Map<String, Object> parameters = new HashMap<>();
        if (additionalParams != null) {
            parameters.putAll(additionalParams);
        }
        
        // Add message data to parameters
        parameters.put("MESSAGE_DATA", messageData);
        parameters.put("GENERATION_TIME", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        
        // Create datasource
        JRDataSource dataSource;
        if (jsonUrl != null && !jsonUrl.trim().isEmpty() && JsonDataSourceHelper.isValidUrl(jsonUrl)) {
            try {
                logger.info("Creating JSON datasource from URL: {}", jsonUrl);
                dataSource = JsonDataSourceHelper.createFromUrl(jsonUrl);
            } catch (IOException e) {
                logger.error("Failed to fetch JSON from URL: {}. Using empty datasource.", jsonUrl, e);
                dataSource = new JREmptyDataSource();
            }
        } else {
            // Create an empty data source
            dataSource = new JREmptyDataSource();
        }
        
        // Fill the report
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        
        // Generate output filename with timestamp
        //String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String baseName = (outputName != null && !outputName.trim().isEmpty()) ? outputName.trim() : "report";
        
        // Remove .pdf extension if already present in outputName
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        String outputFileName = String.format("%s.pdf", baseName);
        String outputPath = Paths.get(outputDirectory, outputFileName).toString();
        
        // Export to PDF
        JasperExportManager.exportReportToPdfFile(jasperPrint, outputPath);
        
        logger.info("Report generated successfully: {}", outputPath);
        return outputPath;
    }

    /**
     * Reload and recompile the JRXML template
     * Useful for updating templates without restarting the application
     */
    public void reloadTemplate() throws JRException, IOException {
        logger.info("Reloading JRXML template");
        compileTemplate();
    }

    /**
     * Get the JRXML template path
     */
    public String getJrxmlPath() {
        return jrxmlPath;
    }

    /**
     * Get the output directory
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }
}
