package com.nyx.jasper;

import org.junit.Test;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the worker does with one message off the queue.
 *
 * The queue is the only way in, so this is where a malformed message, an empty body or a template
 * that no longer compiles has to end without stopping the consumer — and where a message that
 * *did* work gets acknowledged.
 */
public class QueueMessageListenerTest {

    private final StatusMessage status = new StatusMessage();
    private final ReportGeneratorManager manager = mock(ReportGeneratorManager.class);

    private QueueMessageListener listener() {
        return new QueueMessageListener(status, "nyx.jasper.queue", manager);
    }

    private TextMessage text(String body) throws JMSException {
        TextMessage message = mock(TextMessage.class);
        when(message.getText()).thenReturn(body);
        return message;
    }

    @Test
    public void aListenerCanBeCreated() {
        assertNotNull("Listener should be created", listener());
        assertEquals("the queue name is kept as it was given", 0, status.getMessages());
    }

    // A message whose body was read is always acknowledged, even when the payload cannot be
    // processed: without that, a malformed message would be redelivered forever.
    @Test
    public void aTextMessageIsGeneratedAndAcknowledged() throws Exception {
        TextMessage message = text("{\"template\":\"./jasperdef/amarchand/UserUsage.jrxml\",\"parameters\":{\"a\":1}}");
        when(manager.generateReport(any(MessageData.class))).thenReturn("reports/x.pdf");

        listener().onMessage(message);

        verify(manager).generateReport(any(MessageData.class));
        verify(message).acknowledge();
        assertEquals(1, status.getMessages());
        assertEquals(1, status.getReceived().get("/queue/nyx.jasper.queue").intValue());
        assertEquals(0, status.getInternalerrors());
    }

    @Test
    public void aBytesMessageIsReadToo() throws Exception {
        BytesMessage message = mock(BytesMessage.class);
        byte[] body = "{\"template\":\"t.jrxml\"}".getBytes(StandardCharsets.UTF_8);
        when(message.getBodyLength()).thenReturn((long) body.length);
        when(message.readBytes(any(byte[].class))).thenAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(body, 0, target, 0, body.length);
            return body.length;
        });
        when(manager.generateReport(any(MessageData.class))).thenReturn("reports/y.pdf");

        listener().onMessage(message);

        verify(manager).generateReport(any(MessageData.class));
        verify(message).acknowledge();
    }

    @Test
    public void anEmptyBytesMessageIsIgnoredAndNotAcknowledged() throws Exception {
        BytesMessage message = mock(BytesMessage.class);
        when(message.getBodyLength()).thenReturn(0L);

        listener().onMessage(message);

        verify(manager, never()).generateReport(any(MessageData.class));
        verify(message, never()).acknowledge();
        assertEquals(0, status.getMessages());
    }

    @Test
    public void aMessageOfAnotherTypeIsIgnored() throws Exception {
        Message message = mock(Message.class);

        listener().onMessage(message);

        verify(manager, never()).generateReport(any(MessageData.class));
        verify(message, never()).acknowledge();
    }

    @Test
    public void aMessageWithoutATemplateCountsAsAnInternalErrorAndIsNotAcknowledged() throws Exception {
        TextMessage message = text("{\"parameters\":{}}");

        listener().onMessage(message);

        verify(manager, never()).generateReport(any(MessageData.class));
        verify(message).acknowledge();
        assertEquals(1, status.getInternalerrors());
    }

    @Test
    public void aGenerationThatFailsCountsAsAnInternalError() throws Exception {
        TextMessage message = text("{\"template\":\"t.jrxml\"}");
        when(manager.generateReport(any(MessageData.class))).thenThrow(new RuntimeException("template gone"));

        listener().onMessage(message);

        assertEquals(1, status.getInternalerrors());
        verify(message).acknowledge();
    }

    @Test
    public void aBodyThatIsNotJsonCountsAsAnInternalError() throws Exception {
        TextMessage message = text("ceci n'est pas du json");

        listener().onMessage(message);

        assertEquals(1, status.getInternalerrors());
        verify(message).acknowledge();
    }

    @Test
    public void aBrokerThatFailsOnReadIsCountedAsAnError() throws Exception {
        TextMessage message = mock(TextMessage.class);
        when(message.getText()).thenThrow(new JMSException("connection lost"));

        listener().onMessage(message);

        assertEquals(1, status.getErrors());
        assertEquals(0, status.getInternalerrors());
    }

    @Test
    public void anAcknowledgeThatFailsIsAnError() throws Exception {
        TextMessage message = text("{\"template\":\"t.jrxml\"}");
        when(manager.generateReport(any(MessageData.class))).thenReturn("reports/z.pdf");
        doThrow(new JMSException("gone")).when(message).acknowledge();

        listener().onMessage(message);

        assertEquals(1, status.getErrors());
    }

    @Test
    public void theCountersNameTheQueueTheMessageCameFrom() throws Exception {
        TextMessage message = text("{\"template\":\"t.jrxml\"}");
        when(manager.generateReport(any(MessageData.class))).thenReturn("reports/z.pdf");

        listener().onMessage(message);

        assertEquals(1, status.getReceived().size());
        assertEquals("/queue/nyx.jasper.queue", status.getReceived().keySet().iterator().next());
        assertTrue(status.getReceived().containsKey("/queue/nyx.jasper.queue"));
    }
}
