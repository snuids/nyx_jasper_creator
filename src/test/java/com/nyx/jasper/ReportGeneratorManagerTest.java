package com.nyx.jasper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The cache in front of the generators.
 *
 * Every message names its template, and the worker keeps one compiled generator per template — so
 * the two things that matter are that the same template is not recompiled for every message, and
 * that a template which does not compile fails loudly instead of producing an empty report.
 */
public class ReportGeneratorManagerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String template;

    private String copyTemplate(String name) throws Exception {
        File target = new File(folder.getRoot(), name);
        try (InputStream in = getClass().getResourceAsStream("/simple.jrxml")) {
            Files.copy(in, target.toPath());
        }
        return target.getAbsolutePath();
    }

    @Test
    public void theOutputDirectoryIsCreatedWhenItIsMissing() throws Exception {
        File missing = new File(folder.getRoot(), "reports/pdfs");
        ReportGeneratorManager manager = new ReportGeneratorManager(missing.getAbsolutePath());

        assertTrue(missing.isDirectory());
        assertTrue(manager.getOutputDirectory().endsWith("reports/pdfs"));
        assertEquals(0, manager.getCacheSize());
    }

    @Test
    public void aTemplateIsCompiledOnceAndReused() throws Exception {
        template = copyTemplate("simple.jrxml");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());

        manager.generateReport(message(template, "first"));
        manager.generateReport(message(template, "second"));

        assertEquals(1, manager.getCacheSize());
    }

    @Test
    public void twoTemplatesAreTwoGenerators() throws Exception {
        template = copyTemplate("simple.jrxml");
        String other = copyTemplate("simple2.jrxml");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());

        String first = manager.generateReport(message(template, "first"));
        String second = manager.generateReport(message(other, "second"));

        assertEquals(2, manager.getCacheSize());
        assertTrue(new File(first).exists());
        assertTrue(new File(second).exists());
    }

    @Test
    public void aMessageWithoutATemplateIsRefused() throws Exception {
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());
        MessageData message = new MessageData();

        try {
            manager.generateReport(message);
            fail("a message without a template must not be generated");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Template path"));
        }
    }

    @Test
    public void aTemplateThatDoesNotCompileIsReported() throws Exception {
        File broken = folder.newFile("broken.jrxml");
        Files.writeString(broken.toPath(), "<jasperReport></jasperReport>");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());

        try {
            manager.generateReport(message(broken.getAbsolutePath(), "x"));
            fail("an unreadable template must not be generated");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to compile template"));
        }
    }

    @Test
    public void clearingTheCacheForgetsEveryGenerator() throws Exception {
        template = copyTemplate("simple.jrxml");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());
        manager.generateReport(message(template, "first"));

        manager.clearCache();

        assertEquals(0, manager.getCacheSize());
    }

    @Test
    public void reloadingATemplatePutsItBackCompiled() throws Exception {
        template = copyTemplate("simple.jrxml");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());
        manager.generateReport(message(template, "first"));

        manager.reloadTemplate(template);

        assertEquals(1, manager.getCacheSize());
        assertTrue(new File(manager.generateReport(message(template, "after"))).exists());
    }

    @Test
    public void theJsonUrlAndTheOutputNameArePassedOn() throws Exception {
        template = copyTemplate("simple.jrxml");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());
        MessageData message = message(template, "with-json");
        message.setJsonUrl("https://energy.example/api?token=e414bde7");

        String path = manager.generateReport(message);

        assertNotNull(path);
        assertTrue("the custom name is used", path.endsWith("with-json.pdf"));
    }

    @Test
    public void theJdbcPathIsChosenWhenADatabaseUrlIsGiven() throws Exception {
        template = copyTemplate("simple.jrxml");
        ReportGeneratorManager manager = new ReportGeneratorManager(folder.getRoot().getAbsolutePath());
        MessageData message = message(template, "jdbc");
        message.setJdbcDriver("org.h2.Driver");
        message.setJdbcUrl("jdbc:h2:mem:manager");
        message.setJdbcLogin("sa");
        message.setJdbcPassword("");

        String path = manager.generateReport(message);

        assertTrue(new File(path).exists());
    }

    private MessageData message(String templatePath, String outputName) {
        MessageData message = new MessageData();
        message.setTemplatePath(templatePath);
        message.setOutputName(outputName);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("TITLE", "Test");
        message.setParameters(parameters);
        return message;
    }

}
