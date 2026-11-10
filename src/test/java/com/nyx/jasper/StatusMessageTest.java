package com.nyx.jasper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The status published on `RPN_MODULE_INFO`: what the console reads to draw a module tile.
 *
 * The two maps are the traffic counters — one per queue listened to, one per topic published to —
 * and they are keyed by destination name, so the console can show where a module is busy.
 */
public class StatusMessageTest {

    @Test
    public void everyFieldSurvivesItsSetter() {
        StatusMessage status = new StatusMessage();

        status.setError("none");
        status.setType("status");
        status.setEventtype("heartbeat");
        status.setModule("NYX_Jasper_Creator");
        status.setVersion("1.0.22");
        status.setAlive(1);
        status.setErrors(2);
        status.setInternalerrors(3);
        status.setHeartbeaterrors(4);
        status.setMessages(5);
        status.setAmqclientversion("5.18.3");
        status.setStarttimets(1_700_000_000.5);
        status.setStarttime("2026-11-10 20:00:00");
        status.setConnections(6);
        status.setIcon("report");

        assertEquals("none", status.getError());
        assertEquals("status", status.getType());
        assertEquals("heartbeat", status.getEventtype());
        assertEquals("NYX_Jasper_Creator", status.getModule());
        assertEquals("1.0.22", status.getVersion());
        assertEquals(1, status.getAlive());
        assertEquals(2, status.getErrors());
        assertEquals(3, status.getInternalerrors());
        assertEquals(4, status.getHeartbeaterrors());
        assertEquals(5, status.getMessages());
        assertEquals("5.18.3", status.getAmqclientversion());
        assertEquals(1_700_000_000.5, status.getStarttimets(), 0.0001);
        assertEquals("2026-11-10 20:00:00", status.getStarttime());
        assertEquals(6, status.getConnections());
        assertEquals("report", status.getIcon());
    }

    @Test
    public void aNewStatusIsEmptyButForItsIcon() {
        StatusMessage status = new StatusMessage();

        assertTrue(status.getReceived().isEmpty());
        assertTrue(status.getSent().isEmpty());
        assertEquals("calendar", status.getIcon());
    }

    @Test
    public void theCountersAccumulatePerDestination() {
        StatusMessage status = new StatusMessage();

        status.incrementReceived("/queue/nyx.jasper.queue");
        status.incrementReceived("/queue/nyx.jasper.queue");
        status.incrementReceived("/queue/JASPER_UPLOAD");
        status.incrementSent("RPN_MODULE_INFO");
        status.incrementMessages();
        status.incrementMessages();

        assertEquals(2, status.getReceived().get("/queue/nyx.jasper.queue").intValue());
        assertEquals(1, status.getReceived().get("/queue/JASPER_UPLOAD").intValue());
        assertEquals(1, status.getSent().get("RPN_MODULE_INFO").intValue());
        assertEquals(2, status.getMessages());
        assertEquals(2, status.getReceived().size());
    }

    @Test
    public void theMapsCanBeReplaced() {
        StatusMessage status = new StatusMessage();
        java.util.Map<String, Integer> received = new java.util.HashMap<>();
        java.util.Map<String, Integer> sent = new java.util.HashMap<>();

        status.setReceived(received);
        status.setSent(sent);

        assertSame(received, status.getReceived());
        assertSame(sent, status.getSent());
    }

    @Test
    public void aParameterItemKeepsWhatTheEditorSends() {
        ParameterItem item = new ParameterItem();
        item.setName("range");
        item.setTitle("Période");
        item.setType("interval");
        item.setValue("2026-10-09");

        assertEquals("range", item.getName());
        assertEquals("Période", item.getTitle());
        assertEquals("interval", item.getType());
        assertEquals("2026-10-09", item.getValue());
        assertTrue(item.toString().contains("interval"));
        assertTrue(item.toString().contains("Période"));
    }
}
