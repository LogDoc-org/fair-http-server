package org.logdoc.fairhttp.service.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.tools.websocket.Opcode;
import org.logdoc.fairhttp.service.tools.websocket.extension.DefaultExtension;
import org.logdoc.fairhttp.service.tools.websocket.extension.IExtension;
import org.logdoc.fairhttp.service.tools.websocket.frames.*;
import org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.function.Consumer;

import static org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol.RFC_KEY_UUID;
import static org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol.WS_VERSION;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.07.2023 17:30
 * fair-http-server ☭ sweat and blood
 */
public abstract class WebSocket extends Response implements Consumer<Byte> {
    private static final Logger logger = LoggerFactory.getLogger(WebSocket.class);

    private IExtension extension;
    private IProtocol protocol;
    private ObjectMapper om;
    private DocumentBuilder xb;
    private Transformer tr;
    private int frameStage, payloadlength;
    private AFrame frame;
    private Frame incompleteframe;
    private Opcode optcode;
    private boolean mask;
    private Http.Drive drive;
    private byte[] payload, maskkey;

    private Consumer<byte[]> writeConsumer;

    public WebSocket(final Request request) {
        super(101, "Websocket Connection Upgrade");

        header(Headers.Upgrade, "websocket");
        header(Headers.Connection, Headers.Upgrade);

        prepare(request);
    }

    public WebSocket(final Request request, final IExtension extension) {
        this(request, extension, null);
    }

    public WebSocket(final Request request, final IExtension extension, final IProtocol protocol) {
        super(101, "Websocket Connection Upgrade");

        header(Headers.Upgrade, "websocket");
        header(Headers.Connection, Headers.Upgrade);

        this.extension = extension;
        this.protocol = protocol;

        prepare(request);
    }

    private void prepare(final Request request) {
        if (!WS_VERSION.equals(request.header(Headers.SecWebsocketVersion)))
            throw new IllegalStateException("Wrong websocket version: " + request.header(Headers.SecWebsocketVersion) + ", expected: " + WS_VERSION);

        final String id;
        try {
            id = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA1").digest((request.header(Headers.SecWebsocketKey) + RFC_KEY_UUID).getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        header(Headers.SecWebsocketAccept, id);
        if (extension != null && extension.acceptProvidedExtensionAsServer(request.header(Headers.SecWebsocketExtensions)))
            header(Headers.SecWebsocketExtensions, extension.getProvidedExtensionAsServer());
        else if (extension != null)
            throw new IllegalStateException("Cant accept requested extenstion(s): " + request.header(Headers.SecWebsocketExtensions));

        if (protocol != null && protocol.acceptProtocol(request.header(Headers.SecWebsocketProtocols)))
            header(Headers.SecWebsocketProtocols, protocol.getProvidedProtocol());
        else if (protocol != null)
            throw new IllegalStateException("Cant accept requested protocol(s): " + request.header(Headers.SecWebsocketProtocols));

        if (extension == null)
            extension = new DefaultExtension();
    }

    @Override
    public final void accept(final Byte b) {
        if (b == null || b == -1)
            return;

        switch (frameStage++) {
            case -1:
                frameStage = -1;
                drive.accept(b);
                break;
            case 0:
                optcode = toOpcode((byte) (b & 15));
                frame = AFrame.get(optcode);

                frame.setFin(b >> 8 != 0);
                frame.setRSV1((b & 0x40) != 0);
                frame.setRSV2((b & 0x20) != 0);
                frame.setRSV3((b & 0x10) != 0);
                break;
            case 1:
                mask = (b & -128) != 0;
                payloadlength = (byte) (b & ~(byte) 128);

                if (payloadlength > 125 && optcode == Opcode.PING || optcode == Opcode.PONG || optcode == Opcode.CLOSING)
                    throw new IllegalArgumentException("more than 125 octets");

                if (payloadlength > 125) {

                    if (payloadlength == 126) {
                        drive = new Http.Drive(2, bytes -> {
                            frameStage = 2;
                            final byte[] sizebytes = new byte[3];
                            sizebytes[1] = bytes[0];
                            sizebytes[2] = bytes[1];
                            payloadlength = new BigInteger(sizebytes).intValue();
                        });
                    } else {
                        drive = new Http.Drive(8, bytes -> {
                            frameStage = 2;
                            payloadlength = (int) new BigInteger(bytes).longValue();
                        });
                    }

                    frameStage = -1;
                }

                break;
            case 2:
                payload = new byte[payloadlength];
                frameStage = -1;
                if (mask) {
                    drive = new Http.Drive(4, bytes -> {
                        maskkey = bytes;

                        drive = new Http.Drive(payloadlength, bb -> {
                            frameStage = 3;
                            for (int i = 0; i < payloadlength; i++)
                                payload[i] = (byte) (bb[i] ^ maskkey[i % 4]);
                        });
                    });
                } else
                    drive = new Http.Drive(payloadlength, bytes -> {
                        frameStage = 3;
                        payload = bytes;
                    });
                break;
            case 3:
                frameStage = 0;

                frame.setPayload(payload);

                IExtension ext = null;

                if (frame.getOpcode() != Opcode.CONTINUOUS && (frame.isRSV1() || frame.isRSV2() || frame.isRSV3()))
                    ext = extension;

                if (ext == null)
                    ext = new DefaultExtension();

                if (ext.isFrameValid(frame))
                    try {
                        ext.decodeFrame(frame);

                        if (frame.isValid())
                            process(frame);
                        else
                            logger.error("Invalid frame catched: " + frame);
                    } catch (final Exception e) {
                        logger.error("Frame processing error: " + frame + " :: " + e.getMessage(), e);
                    }
                else
                    logger.error("Extension cant decode frame: " + frame);

                break;
        }
    }

    private void process(final Frame frame) {
        final Opcode curop = frame.getOpcode();

        if (curop == Opcode.CLOSING) {
            int code = CloseFrame.NOCODE;
            String reason = "";

            if (frame instanceof CloseFrame) {
                code = ((CloseFrame) frame).getCloseCode();
                reason = ((CloseFrame) frame).getMessage();
            }

            close(code, reason, true);
        } else if (curop == Opcode.PING)
            onPing();
        else if (curop == Opcode.PONG)
            onPong();
        else if (!frame.isFin() || curop == Opcode.CONTINUOUS)
            processFrameContinuousAndNonFin(frame, curop);
        else if (incompleteframe != null)
            throw new IllegalStateException("Continuous frame sequence not completed.");
        else
            frameReady(frame);
    }

    private void frameReady(final Frame frame) {
        final byte[] data = frame.getPayloadData();

        if (frame.getOpcode() == Opcode.TEXT) {
            final String text = new String(data, StandardCharsets.UTF_8).trim();

            if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                if (om == null)
                    om = new ObjectMapper();

                try {
                    onJson(om.readTree(text));
                    return;
                } catch (final Exception ignore) {
                }
            }

            if (text.toLowerCase().startsWith("<") && text.endsWith(">")) {
                if (xb == null)
                    try {
                        xb = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    } catch (final Exception ignore) {
                    }

                if (xb != null)
                    try {
                        onXml(xb.parse(new InputSource(new ByteArrayInputStream(data))));

                        return;
                    } catch (final Exception ignore) {
                    }
            }

            onText(text);
        } else if (frame.getOpcode() == Opcode.BINARY)
            onBytes(data);
    }

    private void processFrameContinuousAndNonFin(final Frame frame, final Opcode curop) {
        if (curop != Opcode.CONTINUOUS) {
            incompleteframe = frame;
        } else if (frame.isFin()) {
            if (incompleteframe == null)
                throw new IllegalStateException("Continuous frame sequence was not started.");

            incompleteframe.append(frame);

            ((AFrame) incompleteframe).isValid();

            frameReady(incompleteframe);

            incompleteframe = null;
        } else if (incompleteframe == null)
            throw new IllegalStateException("Continuous frame sequence was not started.");

        if (curop == Opcode.CONTINUOUS && incompleteframe != null)
            incompleteframe.append(frame);
    }

    private Opcode toOpcode(final byte opcode) {
        switch (opcode) {
            case 0:
                return Opcode.CONTINUOUS;
            case 1:
                return Opcode.TEXT;
            case 2:
                return Opcode.BINARY;
            case 8:
                return Opcode.CLOSING;
            case 9:
                return Opcode.PING;
            case 10:
                return Opcode.PONG;
            default:
                throw new IllegalArgumentException("Unknown opcode " + (short) opcode);
        }
    }

    public final void close() {
        close(CloseFrame.NORMAL, null, false);
    }

    public final void close(final int code, final String reason) {
        close(code, reason, false);
    }

    private void close(final int code, final String reason, final boolean remote) {
        if (!remote)
            try {sendFrame(new CloseFrame(code, reason));} catch (final Exception ignore) {}

        try {onClose(code, reason, remote);} catch (final Exception ignore) {}
    }

    public abstract void onJson(JsonNode json);

    public abstract void onXml(Document xml);

    public abstract void onText(String text);

    public abstract void onBytes(byte[] bytes);

    public abstract void onPing();

    public abstract void onPong();

    public abstract void onClose(int code, String reason, boolean remote);

    public void ping() {
        sendFrame(new PingFrame());
    }

    public void send(final JsonNode message) {
        if (message == null)
            throw new NullPointerException("Message");

        if (om == null)
            om = new ObjectMapper();

        try {
            final TextFrame frame = new TextFrame();
            frame.setPayload(message.toString().getBytes(StandardCharsets.UTF_8));
            frame.setMasked(true);

            sendFrame(frame);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void send(final Document message) {
        if (message == null)
            throw new NullPointerException("Message");

        if (tr == null)
            try {
                tr = TransformerFactory.newInstance().newTransformer();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }

        try (final ByteArrayOutputStream buf = new ByteArrayOutputStream(1024 * 16)) {
            tr.transform(new DOMSource(message), new StreamResult(buf));

            buf.flush();

            final TextFrame frame = new TextFrame();
            frame.setPayload(buf.toByteArray());
            frame.setMasked(true);

            sendFrame(frame);
        } catch (final TransformerException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void send(final String message) {
        if (message == null)
            throw new NullPointerException("Message");

        final TextFrame frame = new TextFrame();
        frame.setPayload(message.getBytes(StandardCharsets.UTF_8));
        frame.setMasked(true);

        sendFrame(frame);
    }

    public void send(final byte[] message) {
        if (message == null)
            throw new NullPointerException("Message");

        final BinaryFrame frame = new BinaryFrame();
        frame.setMasked(true);
        frame.setPayload(message);

        sendFrame(frame);
    }

    protected synchronized void sendFrame(final AFrame framedata) {
        if (framedata == null)
            throw new NullPointerException("Frame");

        if (!framedata.isValid())
            throw new IllegalStateException("Invalid frame");

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 4)) {
            extension.encodeFrame(framedata);

            final byte[] mes = framedata.getPayloadData();
            final int sizebytes = getSizeBytes(mes);
            final byte optcode = fromOpcode(framedata.getOpcode());
            byte one = (byte) (framedata.isFin() ? -128 : 0);
            one |= optcode;
            if (framedata.isRSV1()) one |= getRSVByte(1);
            if (framedata.isRSV2()) one |= getRSVByte(2);
            if (framedata.isRSV3()) one |= getRSVByte(3);
            os.write(one);

            final byte[] payloadlengthbytes = toByteArray(mes.length, sizebytes);

            if (sizebytes == 1) {
                os.write(payloadlengthbytes[0]);
            } else if (sizebytes == 2) {
                os.write((byte) 126);
                os.write(payloadlengthbytes);
            } else if (sizebytes == 8) {
                os.write((byte) 127);
                os.write(payloadlengthbytes);
            } else
                throw new IllegalStateException("Size representation not supported/specified");

            os.write(mes);
            os.flush();

            writeConsumer.accept(os.toByteArray());
        } catch (final IOException e) {
            close(CloseFrame.ABNORMAL_CLOSE, e.getMessage(), false);
            throw new IllegalStateException(e);
        } catch (final IllegalStateException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int getSizeBytes(final byte[] mes) {
        if (mes.length <= 125)
            return 1;

        if (mes.length <= 65535)
            return 2;

        return 8;
    }

    private byte getRSVByte(int rsv) {
        switch (rsv) {
            case 1: // 0100 0000
                return 0x40;
            case 2: // 0010 0000
                return 0x20;
            case 3: // 0001 0000
                return 0x10;
            default:
                return 0;
        }
    }

    private byte[] toByteArray(long val, int bytecount) {
        byte[] buffer = new byte[bytecount];
        int highest = 8 * bytecount - 8;
        for (int i = 0; i < bytecount; i++) {
            buffer[i] = (byte) (val >>> (highest - 8 * i));
        }
        return buffer;
    }

    private byte fromOpcode(final Opcode opcode) {
        switch (opcode) {
            case CONTINUOUS:
                return 0;
            case TEXT:
                return 1;
            case BINARY:
                return 2;
            case CLOSING:
                return 8;
            case PING:
                return 9;
            case PONG:
                return 10;
            default:
                throw new IllegalArgumentException("Don't know how to handle " + opcode);
        }
    }

    void setWriteHandler(final Consumer<byte[]> writeConsumer) {
        this.writeConsumer = writeConsumer;
    }
}
