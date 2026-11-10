package com.nyx.jasper;

import net.sf.jasperreports.engine.JRException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Filling a template and writing the PDF.
 *
 * A real template (`simple.jrxml`, compiled by JasperReports itself) and a real SQL one filled
 * through H2: the point of this class is the three ways it can produce the wrong file — a template
 * it cannot compile, an output name it must normalise, and a datasource it cannot reach but must
 * not fail on.
 */
public class JasperReportGeneratorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String template;

    @Before
    public void copyTemplate() throws Exception {
        template = copy("/simple.jrxml", "simple.jrxml");
    }

    private String copy(String resource, String name) throws Exception {
        File target = new File(folder.getRoot(), name);
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            Files.copy(in, target.toPath());
        }
        return target.getAbsolutePath();
    }

    @Test
    public void theDefaultNameIsReport() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        String path = generator.generateReport("{\"template\":\"simple.jrxml\"}");

        assertEquals(Paths.get(folder.getRoot().getAbsolutePath(), "report.pdf").toString(), path);
        assertTrue("the pdf is written", new File(path).length() > 0);
    }

    @Test
    public void aCustomNameLosesItsPdfSuffixSoItIsNotDoubled() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        String path = generator.generateReport("message", null, null, "balance.PDF");

        assertTrue(path.endsWith("balance.pdf"));
        assertFalse("no `balance.pdf.pdf`", path.endsWith("balance.pdf.pdf"));
        assertTrue(new File(path).exists());
    }

    @Test
    public void theExtraParametersReachTheTemplate() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("TITLE", "Consommation");

        String path = generator.generateReport("message", parameters);

        assertTrue(new File(path).exists());
        assertEquals("the template compiles once and the generator keeps it",
                template, generator.getJrxmlPath());
    }

    @Test
    public void anOutputNameOfSpacesFallsBackToReport() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        String path = generator.generateReport("message", null, null, "   ");

        assertTrue(path.endsWith("report.pdf"));
    }

    @Test
    public void aDatasourceThatCannotBeReachedStillProducesTheReport() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        // 127.0.0.1:1 a le mérite d'être refusé tout de suite
        String path = generator.generateReport("message", null, "http://127.0.0.1:1/data", "fallback");

        assertTrue("the fallback is an empty datasource, not a failure", new File(path).exists());
    }

    @Test
    public void somethingThatIsNotAnUrlIsIgnored() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        String path = generator.generateReport("message", null, "pas une url", "ignored");

        assertTrue(new File(path).exists());
    }

    @Test
    public void aTemplateThatDoesNotExistIsRefused() {
        try {
            new JasperReportGenerator(new File(folder.getRoot(), "absent.jrxml").getAbsolutePath(),
                    folder.getRoot().getAbsolutePath());
            fail("a missing template must not produce a generator");
        } catch (Exception e) {
            assertTrue("expected a Jasper or IO failure, got " + e, e instanceof JRException || e instanceof java.io.IOException);
        }
    }

    @Test
    public void aTemplateThatIsNotValidXmlIsRefused() throws Exception {
        File broken = folder.newFile("broken.jrxml");
        Files.writeString(broken.toPath(), "<jasperReport><this is not a report</jasperReport>");

        try {
            new JasperReportGenerator(broken.getAbsolutePath(), folder.getRoot().getAbsolutePath());
            fail("an invalid template must not produce a generator");
        } catch (Exception e) {
            // JasperReports 7 wraps the parse failure itself (an IllegalArgumentException out of the
            // Jackson report loader); what matters is that no generator comes out of it
            assertNotNull("the failure carries a message", e.getMessage());
        }
    }

    @Test
    public void reloadingRecompilesTheTemplate() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());
        assertTrue(new File(generator.generateReport("before")).exists());

        generator.reloadTemplate();

        assertTrue(new File(generator.generateReport("after")).exists());
        assertEquals(folder.getRoot().getAbsolutePath(), generator.getOutputDirectory());
    }

    @Test
    public void theJdbcPathFillsTheReportFromTheDatabase() throws Exception {
        String sqlTemplate = copy("/sql.jrxml", "sql.jrxml");
        JasperReportGenerator generator = new JasperReportGenerator(sqlTemplate, folder.getRoot().getAbsolutePath());
        String url = "jdbc:h2:mem:jasper;DB_CLOSE_DELAY=-1";
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("CREATE TABLE site (name VARCHAR(40))");
            connection.createStatement().execute("INSERT INTO site VALUES ('BXL'), ('LLN')");
        }

        String path = generator.generateReportWithJdbc("message", null, "org.h2.Driver", url, "sa", "", "sites");

        assertTrue(path.endsWith("sites.pdf"));
        assertTrue(new File(path).length() > 0);
    }

    @Test
    public void aDriverThatIsNotOnThePathIsReported() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        try {
            generator.generateReportWithJdbc("message", null, "com.acme.NoSuchDriver", "jdbc:acme:x", "u", "p", "x");
            fail("an unknown driver must not produce a report");
        } catch (JRException e) {
            assertTrue(e.getMessage().contains("driver not found"));
        }
    }

    @Test
    public void aDatabaseThatRefusesTheConnectionIsReported() throws Exception {
        JasperReportGenerator generator = new JasperReportGenerator(template, folder.getRoot().getAbsolutePath());

        try {
            generator.generateReportWithJdbc("message", null, "org.h2.Driver",
                    "jdbc:h2:tcp://127.0.0.1:1/nope", "sa", "", "x");
            fail("an unreachable database must not produce a report");
        } catch (JRException e) {
            assertTrue(e.getMessage().contains("Failed to connect"));
        }
    }
}
