package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.tools.websocket.Opcode;
import org.logdoc.fairhttp.service.tools.websocket.extension.DefaultExtension;
import org.logdoc.fairhttp.service.tools.websocket.extension.IExtension;
import org.logdoc.fairhttp.service.tools.websocket.frames.*;
import org.logdoc.helpers.Texts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.07.2023 17:30
 * fair-http-server ☭ sweat and blood
 */
public final class WebSocket extends Response {
    final Consumer<ErrorRef> readErrorConsumer, writeErrorConsumer;
    private final IExtension extension;
    private final Consumer<String> textConsumer;
    private final Consumer<byte[]> binaryConsumer;
    private final Consumer<WebSocket> pingConsumer, pongConsumer;
    private final Consumer<CloseReason> closeConsumer;
    private final boolean readEnabled, writeEnabled;
    private final long readTimeoutMs;
    private int frameStage, payloadlength;
    private AFrame frame;
    private Frame incompleteframe;
    private Opcode optcode;
    private boolean mask, closed;
    private Drive drive;
    private byte[] payload, maskkey;
    private OutputStream os;
    private InetSocketAddress remote;

    WebSocket(final IExtension extension, final Consumer<String> textConsumer, final Consumer<byte[]> binaryConsumer, final Consumer<WebSocket> pingConsumer, final Consumer<WebSocket> pongConsumer, final Consumer<CloseReason> closeConsumer, final Consumer<ErrorRef> readErrorConsumer, final Consumer<ErrorRef> writeErrorConsumer, final boolean readEnabled, final boolean writeEnabled, final long readTimeoutMs) {
        super(101, "Websocket Connection Upgrade");
        this.extension = extension;
        this.textConsumer = textConsumer;
        this.binaryConsumer = binaryConsumer;
        this.pingConsumer = pingConsumer;
        this.pongConsumer = pongConsumer;
        this.closeConsumer = closeConsumer;
        this.readErrorConsumer = readErrorConsumer;
        this.writeErrorConsumer = writeErrorConsumer;
        this.readEnabled = readEnabled;
        this.writeEnabled = writeEnabled;
        this.readTimeoutMs = readTimeoutMs;
    }

    public InetSocketAddress remote() {
        return remote;
    }

    public String wsId() {
        return header(Headers.SecWebsocketAccept);
    }

    private void nextByte(final byte b) {
        if (closed || b == -1)
            return;

        switch (frameStage++) {
            case -1:
                frameStage = -1;
                drive.accept(b);
                break;
            case 0:
                optcode = toOpcode((byte) (b & 15));
                if (optcode == null) {
                    readErrorConsumer.accept(error("Unknown opcode " + (short) (b & 15)));
                    return;
                }

                frame = AFrame.get(optcode);

                frame.setFin(b >> 8 != 0);
                frame.setRSV1((b & 0x40) != 0);
                frame.setRSV2((b & 0x20) != 0);
                frame.setRSV3((b & 0x10) != 0);
                break;
            case 1:
                mask = (b & -128) != 0;
                payloadlength = (byte) (b & ~(byte) 128);

                if (payloadlength > 125) {
                    if (optcode == Opcode.PING || optcode == Opcode.PONG || optcode == Opcode.CLOSING) {
                        readErrorConsumer.accept(error("more than 125 octets payload"));
                        return;
                    }

                    if (payloadlength == 126) {
                        drive = new Drive(2, bytes -> {
                            frameStage = 2;
                            final byte[] sizebytes = new byte[3];
                            sizebytes[1] = bytes[0];
                            sizebytes[2] = bytes[1];
                            payloadlength = new BigInteger(sizebytes).intValue();
                        });
                    } else {
                        drive = new Drive(8, bytes -> {
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
                    drive = new Drive(4, bytes -> {
                        maskkey = bytes;

                        drive = new Drive(payloadlength, bb -> {
                            frameStage = 3;
                            for (int i = 0; i < payloadlength; i++)
                                payload[i] = (byte) (bb[i] ^ maskkey[i % 4]);
                        });
                    });
                } else
                    drive = new Drive(payloadlength, bytes -> {
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
                            readErrorConsumer.accept(error("Invalid frame catched: " + frame));
                    } catch (final Exception e) {
                        readErrorConsumer.accept(error("Frame processing error: " + frame + " :: " + e.getMessage(), e));
                    }
                else
                    readErrorConsumer.accept(error("Extension cant decode frame: " + frame));

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
            pingConsumer.accept(this);
        else if (curop == Opcode.PONG)
            pongConsumer.accept(this);
        else if (!frame.isFin() || curop == Opcode.CONTINUOUS)
            processFrameContinuousAndNonFin(frame, curop);
        else if (incompleteframe != null)
            readErrorConsumer.accept(error("Continuous frame sequence not completed."));
        else
            contentReady(frame);
    }

    private void contentReady(final Frame frame) {

        final byte[] data = frame.getPayloadData();

        if (frame.getOpcode() == Opcode.TEXT)
            textConsumer.accept(new String(data, StandardCharsets.UTF_8));
        else if (frame.getOpcode() == Opcode.BINARY)
            binaryConsumer.accept(data);
    }

    private void processFrameContinuousAndNonFin(final Frame frame, final Opcode curop) {
        if (curop != Opcode.CONTINUOUS) {
            incompleteframe = frame;
        } else if (frame.isFin()) {
            if (incompleteframe == null) {
                readErrorConsumer.accept(error("Continuous frame sequence was not started."));
                return;
            }

            incompleteframe.append(frame);

            ((AFrame) incompleteframe).isValid();

            contentReady(incompleteframe);

            incompleteframe = null;
        } else if (incompleteframe == null) {
            readErrorConsumer.accept(error("Continuous frame sequence was not started."));
            return;
        }

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
                return null;
        }
    }

    public void close() {
        close(CloseFrame.NORMAL, null, false);
    }

    public void close(final int code, final String reason) {
        close(code, reason, false);
    }

    private void close(final int code, final String reason, final boolean remote) {
        if (closed) return;

        synchronized (this) {
            closed = true;
        }

        if (!remote)
            try {sendFrame(new CloseFrame(code, reason));} catch (final Exception ignore) {}

        if (os != null)
            try {os.close();} catch (final Exception ignore) {}

        closeConsumer.accept(new CloseReason(code, reason, remote));
    }

    public void ping() {
        sendFrame(new PingFrame());
    }

    public void pong() {
        sendFrame(new PongFrame());
    }

    public void send(final String message) {
        if (message == null) {
            writeErrorConsumer.accept(error("Message is null"));
            return;
        }

        final TextFrame frame = new TextFrame();
        frame.setMasked(true);
        frame.setPayload(message.getBytes(StandardCharsets.UTF_8));

        sendFrame(frame);
    }

    public void send(final byte[] message) {
        if (message == null) {
            writeErrorConsumer.accept(error("Message is null"));
            return;
        }

        final BinaryFrame frame = new BinaryFrame();
        frame.setMasked(true);
        frame.setPayload(message);

        sendFrame(frame);
    }

    private synchronized void sendFrame(final AFrame framedata) {
        if (!writeEnabled) {
            writeErrorConsumer.accept(error("Websocket is read-only"));
            return;
        }

        if (closed) {
            writeErrorConsumer.accept(error("Websocket is closed"));
            return;
        }

        if (framedata == null) {
            writeErrorConsumer.accept(error("Frame is null"));
            return;
        }

        if (!framedata.isValid()) {
            writeErrorConsumer.accept(error("Invalid frame"));
            return;
        }

        try {
            extension.encodeFrame(framedata);

            final byte[] mes = framedata.getPayloadData();
            final int sizebytes = getSizeBytes(mes);
            final byte optcode = fromOpcode(framedata.getOpcode());
            if (optcode == -1) {
                writeErrorConsumer.accept(error("Don't know how to handle " + framedata.getOpcode()));
                return;
            }
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
            } else {
                writeErrorConsumer.accept(error("Size representation not supported/specified"));
                return;
            }

            os.write(mes);
            os.flush();
        } catch (final Exception e) {
            writeErrorConsumer.accept(error(Texts.notNull(e.getMessage()), e));
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
                return -1;
        }
    }

    private ErrorRef error(final String error) {
        return error(error, null);
    }

    private ErrorRef error(final String error, final Throwable cause) {
        return new ErrorRef(this, error, cause);
    }

    void spinOff(final Socket socket) {
        try {
            os = socket.getOutputStream();
            remote = (InetSocketAddress) socket.getRemoteSocketAddress();
            os.write(WebSocket.this.asBytes());

            if (readEnabled) {
                socket.setSoTimeout((int) readTimeoutMs);

                CompletableFuture.runAsync(() -> {
                    try {
                        final InputStream is = socket.getInputStream();

                        do {
                            nextByte((byte) is.read());
                        } while (!socket.isClosed());

                        if (!socket.isClosed())
                            close(CloseFrame.GOING_AWAY, "Timed out", true);
                    } catch (final Exception e) {
                        readErrorConsumer.accept(error("Critical socket error", e));
                        close(CloseFrame.BUGGYCLOSE, e.getMessage());
                    }
                });
            }
        } catch (final IOException e) {
            readErrorConsumer.accept(error("Critical socket error", e));
            close(CloseFrame.BUGGYCLOSE, e.getMessage());
        }
    }

    private static class Drive implements Consumer<Byte> {
        private final ByteArrayOutputStream buf;
        private final Consumer<byte[]> finisher;
        private final int size;

        Drive(final int size, final Consumer<byte[]> finisher) {
            buf = new ByteArrayOutputStream(size);
            this.size = size;
            this.finisher = finisher;
        }

        @Override
        public void accept(final Byte b) {
            buf.write(b);

            if (buf.size() == size) {
                finisher.accept(buf.toByteArray());
                buf.reset();
            }
        }
    }

    public static class CloseReason {
        public final int code;
        public final String reason;
        public final boolean remotelyInited;

        private CloseReason(final int code, final String reason, final boolean remotelyInited) {
            this.code = code;
            this.reason = reason;
            this.remotelyInited = remotelyInited;
        }

        @Override
        public String toString() {
            return "CloseReason{" +
                    "code=" + code +
                    ", reason='" + reason + '\'' +
                    ", remotelyInited=" + remotelyInited +
                    '}';
        }
    }

    public static class ErrorRef {
        public final WebSocket ws;
        public final String error;
        public final Throwable cause;

        private ErrorRef(final WebSocket ws, final String error, final Throwable cause) {
            this.ws = ws;
            this.error = error;
            this.cause = cause;
        }
    }
}
