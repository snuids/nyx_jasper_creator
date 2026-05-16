package com.nyx.jasper;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit test for QueueMessageListener
 */
public class QueueMessageListenerTest {

    @Test
    public void testListenerCreation() {
        StatusMessage statusMessage = new StatusMessage();
        QueueMessageListener listener = new QueueMessageListener(statusMessage, "test.queue", null);
        assertNotNull("Listener should be created", listener);
    }
}
