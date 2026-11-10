package com.nyx.jasper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The shape of what the API puts on the queue.
 *
 * Two parameter formats reach the worker: an object from the console's simple forms, and an array of
 * `{name, value, type}` from the report editor. Both end up in the same `Map`, and an `interval`
 * becomes four parameters — the two raw bounds and their `Date` forms, which is what a JRXML
 * `$P{…_startDT}` expects.
 */
public class MessageDataTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void anObjectOfParametersIsTakenAsItIs() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t.jrxml\",\"parameters\":{\"a\":1,\"b\":\"x\"},"
                + "\"outputName\":\"out\",\"jsonUrl\":\"https://x/?flat=true\"}", MessageData.class);

        assertEquals("t.jrxml", data.getTemplatePath());
        assertEquals(2, data.getParameters().size());
        assertEquals(1, data.getParameters().get("a"));
        assertEquals("x", data.getParameters().get("b"));
        assertEquals("out", data.getOutputName());
        assertEquals("https://x/?flat=true", data.getJsonUrl());
    }

    @Test
    public void anArrayOfParametersIsConverted() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t.jrxml\",\"parameters\":["
                + "{\"name\":\"flat\",\"value\":true},"
                + "{\"name\":\"site\",\"value\":\"BXL\"}]}", MessageData.class);

        assertEquals(Boolean.TRUE, data.getParameters().get("flat"));
        assertEquals("BXL", data.getParameters().get("site"));
        assertEquals(2, data.getParameters().size());
    }

    @Test
    public void anIntervalBecomesItsBoundsAndTheirDates() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t\",\"parameters\":[{\"name\":\"range\","
                + "\"type\":\"interval\",\"value\":[\"2026-10-09T23:00:00.000Z\",\"2026-10-10T23:00:00.000Z\"]}]}",
                MessageData.class);

        Map<String, Object> parameters = data.getParameters();
        assertEquals("2026-10-09T23:00:00.000Z", parameters.get("range_start"));
        assertEquals("2026-10-10T23:00:00.000Z", parameters.get("range_end"));
        assertTrue(parameters.get("range_startDT") instanceof Date);
        assertEquals(1791586800000L, ((Date) parameters.get("range_startDT")).getTime());
        assertNotNull(parameters.get("range_endDT"));
        assertFalse("the interval itself is not passed as one parameter", parameters.containsKey("range"));
    }

    @Test
    public void anIntervalWithAnUnreadableBoundKeepsA_nullDate() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t\",\"parameters\":[{\"name\":\"range\","
                + "\"type\":\"interval\",\"value\":[\"yesterday\",null]}]}", MessageData.class);

        assertEquals("yesterday", data.getParameters().get("range_start"));
        assertNull(data.getParameters().get("range_startDT"));
        assertNull(data.getParameters().get("range_end"));
        assertNull(data.getParameters().get("range_endDT"));
    }

    @Test
    public void anArrayItemWithoutANameIsIgnored() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t\",\"parameters\":["
                + "{\"value\":\"orphan\"},{\"name\":\"kept\",\"value\":1}]}", MessageData.class);

        assertEquals(1, data.getParameters().size());
        assertEquals(1, data.getParameters().get("kept"));
    }

    @Test
    public void anAbsentParameterBlockIsAnEmptyOne() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t\"}", MessageData.class);
        assertTrue(data.getParameters().isEmpty());
    }

    @Test
    public void aParameterBlockThatIsNeitherObjectNorArrayIsAnEmptyOne() {
        MessageData data = new MessageData();
        data.setParametersFromJson("not a list nor a map");
        assertTrue(data.getParameters().isEmpty());

        data.setParametersFromJson(null);
        assertTrue(data.getParameters().isEmpty());
    }

    @Test
    public void theJdbcFieldsAreRead() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t\",\"jdbc_driver\":\"org.h2.Driver\","
                + "\"jdbc_url\":\"jdbc:h2:mem:x\",\"jdbc_login\":\"sa\",\"jdbc_password\":\"secret\"}",
                MessageData.class);

        assertEquals("org.h2.Driver", data.getJdbcDriver());
        assertEquals("jdbc:h2:mem:x", data.getJdbcUrl());
        assertEquals("sa", data.getJdbcLogin());
        assertEquals("secret", data.getJdbcPassword());
    }

    @Test
    public void theSettersAreTheSameAsTheJsonNames() {
        MessageData data = new MessageData();
        data.setTemplatePath("t");
        data.setOutputName("o");
        data.setJsonUrl("u");
        data.setJdbcDriver("d");
        data.setJdbcUrl("j");
        data.setJdbcLogin("l");
        data.setJdbcPassword("p");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("k", "v");
        data.setParameters(parameters);

        assertSameMap(parameters, data.getParameters());
        assertEquals("d", data.getJdbcDriver());
        assertEquals("j", data.getJdbcUrl());
        assertEquals("l", data.getJdbcLogin());
        assertEquals("p", data.getJdbcPassword());
    }

    @Test
    public void theDescriptionKeepsThePasswordAndTheTokenOut() {
        MessageData data = new MessageData();
        data.setTemplatePath("t.jrxml");
        data.setJdbcPassword("hunter2");
        data.setJsonUrl("https://energy.example/api?token=e414bde7&flat=true");

        String description = data.toString();

        assertFalse("the JDBC password is masked", description.contains("hunter2"));
        assertFalse("the session token is masked", description.contains("e414bde7"));
        assertTrue(description.contains("token=***"));
        assertTrue("the rest of the url stays readable", description.contains("flat=true"));
        assertTrue(description.contains("t.jrxml"));
    }

    @Test
    public void aListOfIntervalValuesOfOneBoundLeavesTheEndEmpty() throws Exception {
        MessageData data = mapper.readValue("{\"template\":\"t\",\"parameters\":[{\"name\":\"range\","
                + "\"type\":\"interval\",\"value\":[\"2026-10-09T23:00:00.000Z\"]}]}", MessageData.class);

        List<String> keys = Arrays.asList("range_start", "range_end", "range_startDT", "range_endDT");
        assertTrue(data.getParameters().keySet().containsAll(keys));
        assertNull(data.getParameters().get("range_end"));
        assertNull(data.getParameters().get("range_endDT"));
    }

    private static void assertSameMap(Map<String, Object> expected, Map<String, Object> actual) {
        assertEquals(expected, actual);
    }
}
