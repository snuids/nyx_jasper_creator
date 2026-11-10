package com.nyx.jasper;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Test;
import org.mockito.MockedConstruction;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The worker's own lifecycle.
 *
 * `start()` is a supervisor: connect, wait, close, retry. Two behaviours are worth pinning — it
 * comes back after a broker that refuses the connection instead of dying, and `close()` really
 * stops it (this is what the shutdown hook calls in the container). The ActiveMQ factory is
 * constructed inside `connectAndListen`, so it is intercepted at construction rather than injected.
 */
public class ApplicationTest {

    private Application application;

    private Session session(Connection connection) throws JMSException {
        Session session = mock(Session.class);
        when(connection.createSession(anyBoolean(), anyInt())).thenReturn(session);
        when(session.createTopic(anyString())).thenReturn(mock(Topic.class));
        when(session.createProducer(any(Topic.class))).thenReturn(mock(MessageProducer.class));
        when(session.createTextMessage(anyString())).thenReturn(mock(TextMessage.class));
        when(session.createQueue(anyString())).thenReturn(mock(Queue.class));
        when(session.createConsumer(any(Destination.class))).thenReturn(mock(MessageConsumer.class));
        return session;
    }

    /**
     * `start()` blocks until the application is closed, and Mockito only intercepts the
     * constructions its own thread makes — so the supervisor runs on this thread and a second
     * thread presses the shutdown hook.
     */
    private void startAndCloseAfter(long delayMillis) throws Exception {
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            application.close();
        }, "closer");
        closer.setDaemon(true);
        closer.start();

        application.start();
        closer.join(10_000);
        assertFalse("start() returns once the application is closed", closer.isAlive());
    }

    @Test
    public void theWorkerConnectsConsumesAndStopsWhenClosed() throws Exception {
        Connection connection = mock(Connection.class);
        Session session = session(connection);
        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (mock, context) -> when(mock.createConnection()).thenReturn(connection))) {

            application = new Application();
            startAndCloseAfter(1500);

            verify(connection).start();
            verify(connection).setExceptionListener(any());
            verify(session, atLeastOnce()).createConsumer(any(Destination.class));
            assertEquals("one factory was built", 1, factories.constructed().size());

            verify(connection, atLeastOnce()).close();
            verify(session, atLeastOnce()).close();
        }
    }

    @Test
    public void aBrokerThatRefusesTheConnectionIsRetriedInsteadOfKillingTheWorker() throws Exception {
        try (MockedConstruction<ActiveMQConnectionFactory> factories = mockConstruction(
                ActiveMQConnectionFactory.class,
                (mock, context) -> when(mock.createConnection())
                        .thenThrow(new JMSException("connection refused")))) {

            application = new Application();
            startAndCloseAfter(1200);

            assertEquals("it did try to connect", 1, factories.constructed().size());
        }
    }

    @Test
    public void closingAnApplicationThatNeverStartedIsHarmless() {
        application = new Application();

        application.close();
        application.close();

        assertNotNull(application);
    }

    @Test
    public void theVersionIsTheOneTheContainerAnnounces() {
        assertNotNull(Application.Version);
        assertTrue("a version the image can tag: " + Application.Version,
                Application.Version.matches("\\d+\\.\\d+\\.\\d+"));
    }
}
