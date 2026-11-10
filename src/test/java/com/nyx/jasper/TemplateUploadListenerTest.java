package com.nyx.jasper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Templates uploaded from the console.
 *
 * The upload queue is how a report template reaches the worker without a rebuild: the body is the
 * `.jrxml` in base64, the headers carry the file name and the account that sent it. Two things
 * matter — the file lands under `jasperdef/<login>/`, and a name that tries to walk out of that
 * folder does not.
 */
public class TemplateUploadListenerTest {

    private static final String JRXML = "<jasperReport name=\"uploaded\"/>";

    private final StatusMessage status = new StatusMessage();
    private TemplateUploadListener listener;

    @Before
    public void createListener() throws Exception {
        listener = new TemplateUploadListener(status);
    }

    @After
    public void cleanUp() throws Exception {
        Path root = Paths.get("jasperdef");
        if (Files.exists(root)) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    /** A text upload: the file name, the user, the base64 body. */
    private TextMessage upload(String file, String userJson, String body) throws Exception {
        TextMessage message = mock(TextMessage.class);
        when(message.getStringProperty("file")).thenReturn(file);
        when(message.getStringProperty("user")).thenReturn(userJson);
        when(message.getText()).thenReturn(body);
        when(message.getJMSDestination()).thenReturn(mock(Destination.class));
        return message;
    }

    private String base64(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void anUploadIsWrittenUnderTheLoginOfTheSender() throws Exception {
        TextMessage message = upload("UserUsage.jrxml", "{\"login\":\"amarchand\"}", base64(JRXML));

        listener.onMessage(message);

        Path written = Paths.get("jasperdef", "amarchand", "UserUsage.jrxml");
        assertTrue("the template is on disk", Files.exists(written));
        assertEquals(JRXML, Files.readString(written));
        assertEquals(1, status.getMessages());
        assertEquals(1, status.getReceived().size());
        verify(message).acknowledge();
    }

    @Test
    public void aNameThatWalksOutOfTheFolderIsReducedToItsLastSegment() throws Exception {
        TextMessage message = upload("../../etc/evil.jrxml", "{\"login\":\"amarchand\"}", base64(JRXML));

        listener.onMessage(message);

        assertTrue("the file stays inside the sender's folder",
                Files.exists(Paths.get("jasperdef", "amarchand", "evil.jrxml")));
        assertFalse("nothing is written out of jasperdef", Files.exists(Paths.get("evil.jrxml")));
    }

    @Test
    public void theWhitespaceOfAWrappedBase64BodyIsIgnored() throws Exception {
        String wrapped = base64(JRXML).replaceAll("(.{8})", "$1\n");
        TextMessage message = upload("wrapped.jrxml", "{\"login\":\"amarchand\"}", wrapped);

        listener.onMessage(message);

        assertEquals(JRXML, Files.readString(Paths.get("jasperdef", "amarchand", "wrapped.jrxml")));
    }

    @Test
    public void aBytesMessageIsAcceptedToo() throws Exception {
        BytesMessage message = mock(BytesMessage.class);
        byte[] body = base64(JRXML).getBytes(StandardCharsets.UTF_8);
        when(message.getStringProperty("file")).thenReturn("Bytes.jrxml");
        when(message.getStringProperty("user")).thenReturn("{\"login\":\"snuids\"}");
        when(message.getBodyLength()).thenReturn((long) body.length);
        when(message.readBytes(any(byte[].class))).thenAnswer(invocation -> {
            byte[] target = invocation.getArgument(0);
            System.arraycopy(body, 0, target, 0, body.length);
            return body.length;
        });
        when(message.getJMSDestination()).thenReturn(mock(Destination.class));

        listener.onMessage(message);

        assertTrue(Files.exists(Paths.get("jasperdef", "snuids", "Bytes.jrxml")));
    }

    @Test
    public void aMessageWithoutAFileNameIsCountedAsAnInternalError() throws Exception {
        TextMessage message = upload(null, "{\"login\":\"amarchand\"}", base64(JRXML));

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
        assertEquals(0, status.getMessages());
        verify(message).acknowledge();
    }

    @Test
    public void aMessageWithoutAUserIsCountedAsAnInternalError() throws Exception {
        TextMessage message = upload("x.jrxml", null, base64(JRXML));

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
        assertEquals(0, status.getMessages());
        verify(message).acknowledge();
    }

    @Test
    public void aUserHeaderWithoutALoginIsCountedAsAnInternalError() throws Exception {
        TextMessage message = upload("x.jrxml", "{\"nom\":\"pas de login ici\"}", base64(JRXML));

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
    }

    @Test
    public void aUserHeaderThatIsNotJsonIsCountedAsAnInternalError() throws Exception {
        TextMessage message = upload("x.jrxml", "pas du json", base64(JRXML));

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
    }

    @Test
    public void aBase64BodyThatIsNotBase64LeavesNoFile() throws Exception {
        TextMessage message = upload("broken.jrxml", "{\"login\":\"amarchand\"}", "### pas du base64 ###");

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
        assertFalse(Files.exists(Paths.get("jasperdef", "amarchand", "broken.jrxml")));
    }

    @Test
    public void aBodyOfAnUnsupportedTypeIsRefused() throws Exception {
        Message message = mock(Message.class);
        when(message.getStringProperty("file")).thenReturn("x.jrxml");
        when(message.getStringProperty("user")).thenReturn("{\"login\":\"amarchand\"}");

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
        assertEquals(0, status.getMessages());
    }

    @Test
    public void anAcknowledgeThatFailsIsStillCountedOnce() throws Exception {
        TextMessage message = upload("ack.jrxml", "{\"login\":\"amarchand\"}", base64(JRXML));
        org.mockito.Mockito.doThrow(new javax.jms.JMSException("gone")).when(message).acknowledge();

        listener.onMessage(message);

        assertEquals(1, status.getInternalerrors());
    }
}
