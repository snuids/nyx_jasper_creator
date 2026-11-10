package com.nyx.jasper;

import org.junit.After;
import org.junit.Test;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sign of life `RPN_MODULE_INFO` carries.
 *
 * A module that stops publishing looks dead in the console, so the two behaviours that matter are
 * that a tick actually reaches the topic, and that a dead connection raises the error counter and
 * asks the supervisor to reconnect instead of failing silently.
 */
public class StatusPublisherTest {

    private final Session session = mock(Session.class);
    private final MessageProducer producer = mock(MessageProducer.class);
    private StatusPublisher publisher;

    private void wire() throws JMSException {
        when(session.createTopic(anyString())).thenReturn(mock(Topic.class));
        when(session.createProducer(any(Topic.class))).thenReturn(producer);
        when(session.createTextMessage(anyString())).thenReturn(mock(TextMessage.class));
    }

    @After
    public void stopPublisher() {
        if (publisher != null) {
            publisher.stop();
        }
    }

    @Test
    public void aNewPublisherDescribesTheModuleItRuns() throws Exception {
        wire();
        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 5, "NYX_Jasper_Creator", "1.0.22");

        StatusMessage status = publisher.getStatusMessage();

        assertNotNull(status);
        assertEquals("OK", status.getError());
        assertEquals("lifesign", status.getType());
        assertEquals("lifesign", status.getEventtype());
        assertEquals("NYX_Jasper_Creator", status.getModule());
        assertEquals("1.0.22", status.getVersion());
        assertEquals(1, status.getAlive());
        assertEquals(1, status.getConnections());
        assertEquals(0, status.getMessages());
        assertTrue(status.getStarttimets() > 0);
        assertNotNull(status.getStarttime());
        verify(producer).setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    }

    @Test
    public void aReconnectKeepsTheCountersAndTheStartTime() throws Exception {
        wire();
        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 5, "NYX_Jasper_Creator", "1.0.22");
        StatusMessage existing = publisher.getStatusMessage();
        existing.incrementMessages();
        existing.incrementMessages();
        double startedAt = existing.getStarttimets();

        StatusPublisher reconnected = new StatusPublisher(session, "RPN_MODULE_INFO", 5,
                "NYX_Jasper_Creator", "1.0.23", existing);

        assertTrue("the same status object survives", reconnected.getStatusMessage() == existing);
        assertEquals(2, existing.getMessages());
        assertEquals(startedAt, existing.getStarttimets(), 0.0001);
        assertEquals("1.0.23", existing.getVersion());
        assertEquals(1, existing.getAlive());
        reconnected.stop();
    }

    @Test
    public void aTickReachesTheTopic() throws Exception {
        wire();
        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(invocation -> {
            sent.countDown();
            return null;
        }).when(producer).send(any(TextMessage.class));

        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 1, "NYX_Jasper_Creator", "1.0.22");
        publisher.start();

        assertTrue("a status is published without waiting a full interval", sent.await(5, TimeUnit.SECONDS));
        assertTrue(publisher.getStatusMessage().getSent().containsKey("/topic/RPN_MODULE_INFO"));
        assertEquals(0, publisher.getStatusMessage().getErrors());
    }

    @Test
    public void startingTwiceSchedulesOnePublisher() throws Exception {
        wire();
        AtomicInteger sends = new AtomicInteger();
        doAnswer(invocation -> {
            sends.incrementAndGet();
            return null;
        }).when(producer).send(any(TextMessage.class));

        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 60, "NYX_Jasper_Creator", "1.0.22");
        publisher.start();
        publisher.start();

        Thread.sleep(500);
        assertEquals("the second start is a no-op", 1, sends.get());
    }

    @Test
    public void aDeadConnectionRaisesTheErrorAndAsksForAReconnect() throws Exception {
        wire();
        doThrow(new JMSException("producer closed")).when(producer).send(any(TextMessage.class));
        AtomicInteger reconnects = new AtomicInteger();
        CountDownLatch lost = new CountDownLatch(1);

        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 1, "NYX_Jasper_Creator", "1.0.22");
        publisher.setConnectionLostCallback(() -> {
            reconnects.incrementAndGet();
            lost.countDown();
        });
        publisher.start();

        assertTrue("the callback is asked to reconnect", lost.await(5, TimeUnit.SECONDS));
        assertTrue(publisher.getStatusMessage().getErrors() >= 1);
        assertEquals(1, reconnects.get());
    }

    @Test
    public void aCallbackThatFailsDoesNotStopThePublisher() throws Exception {
        wire();
        doThrow(new JMSException("producer closed")).when(producer).send(any(TextMessage.class));
        CountDownLatch called = new CountDownLatch(1);

        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 1, "NYX_Jasper_Creator", "1.0.22");
        publisher.setConnectionLostCallback(() -> {
            called.countDown();
            throw new IllegalStateException("reconnect failed too");
        });
        publisher.start();

        assertTrue(called.await(5, TimeUnit.SECONDS));
        assertTrue(publisher.getStatusMessage().getErrors() >= 1);
    }

    @Test
    public void stoppingClosesTheProducerAndDropsTheConnectionCount() throws Exception {
        wire();
        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 1, "NYX_Jasper_Creator", "1.0.22");
        publisher.start();

        publisher.stop();

        assertEquals(0, publisher.getStatusMessage().getConnections());
        verify(producer).close();
        publisher = null;
    }

    @Test
    public void stoppingTwiceIsHarmless() throws Exception {
        wire();
        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 1, "NYX_Jasper_Creator", "1.0.22");
        publisher.start();
        publisher.stop();

        publisher.stop();

        assertEquals(0, publisher.getStatusMessage().getConnections());
        publisher = null;
    }

    @Test
    public void aProducerThatRefusesToCloseStillStops() throws Exception {
        wire();
        doThrow(new JMSException("already closed")).when(producer).close();
        publisher = new StatusPublisher(session, "RPN_MODULE_INFO", 1, "NYX_Jasper_Creator", "1.0.22");
        publisher.start();

        publisher.stop();

        assertEquals(0, publisher.getStatusMessage().getConnections());
        publisher = null;
    }
}
