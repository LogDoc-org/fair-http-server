package org.logdoc.fairhttp.service.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.tools.*;
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
import java.io.*;
import java.math.BigInteger;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.logdoc.fairhttp.service.api.Controller.ok;
import static org.logdoc.fairhttp.service.tools.HttpBinStreaming.getBoundary;
import static org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol.RFC_KEY_UUID;
import static org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol.WS_VERSION;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 06.02.2023 15:39
 * FairHttpService ☭ sweat and blood
 */
public class Http {
    private static final byte[] FEED = new byte[]{'\r', '\n'};
    private static final byte[] PROTO = "HTTP/1.1".getBytes(StandardCharsets.US_ASCII);

    public enum SameSite {
        STRICT("Strict"),
        LAX("Lax"),
        NONE("None");

        private final String value;

        SameSite(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    public static class Request {
        private static final Logger logger = LoggerFactory.getLogger(Request.class);

        final Map<String, String> headers, cookies;
        private final Map<String, String> query;
        byte[] preRead;
        InputStream is;
        int knownBodyLength;
        boolean chunked;
        MimeType contentType;
        String method, path, proto, ip;
        private String hardPath;
        private byte[] body;

        {
            headers = new HashMap<>(16) {
                @Override
                public String put(final String key, final String value) {
                    return super.put(key.toUpperCase(), value);
                }

                @Override
                public String get(final Object key) {
                    return super.get(String.valueOf(key).toUpperCase());
                }
            };
            cookies = new HashMap<>(8);
            query = new HashMap<>(8);
            knownBodyLength = -1;
        }

        @Override
        public String toString() {
            return method + " " + path;
        }

        public byte[] bodyAsBytes() {
            return body;
        }

        public Map<String, String> getHeaders() {
            return new HashMap<>(headers);
        }

        public JsonNode bodyAsJson() {
            if (contentTypeMismatch(MimeType.JSON))
                return null;

            sureBodyRead();

            try {
                return Json.parse(body);
            } catch (final Exception ignore) {
            }

            return null;
        }

        public boolean contentTypeMismatch(final MimeType expected) {
            if (this.contentType == null)
                return expected != null;

            try {
                return !this.contentType.match(expected);
            } catch (final Exception ignore) {
            }

            return true;
        }

        public Form bodyAsForm() {
            if (contentTypeMismatch(MimeType.FORM))
                return null;

            sureBodyRead();

            try {
                final String data = new String(body, StandardCharsets.UTF_8);

                final Form form = new Form();

                Arrays.stream(data.split(Pattern.quote("&")))
                        .map(pair -> pair.split(Pattern.quote("=")))
                        .forEach(kv -> {
                            final String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);

                            if (isEmpty(v)) {
                                if (!form.containsKey(kv[0]))
                                    form.put(kv[0], new ArrayList<>(2));

                                form.get(kv[0]).add(v);
                            }
                        });

                return form;
            } catch (final Exception e) {
                logger.error(e.getMessage(), e);
            }

            return null;
        }

        public MultiForm bodyAsMultipart() {
            if (contentTypeMismatch(MimeType.MULTIPART))
                return null;

            sureBodyRead();
            final MultiForm form = new MultiForm();

            try {
                final List<Pair<Integer, Integer>> positions = HttpBinStreaming.markParts(body, getBoundary(contentType));

                for (final Pair<Integer, Integer> partMark : positions) {
                    final AtomicReference<MimeType> cTypeHold = new AtomicReference<>(MimeType.TEXTPLAIN);
                    final Map<String, String> partHeaders = new HashMap<>(8) {
                        @Override
                        public String put(final String key, final String value) {
                            return super.put(key.toUpperCase(), value);
                        }

                        @Override
                        public String get(final Object key) {
                            return super.get(String.valueOf(key).toUpperCase());
                        }
                    };

                    try (final ByteArrayOutputStream tempo = new ByteArrayOutputStream(256)) {
                        final AtomicBoolean inHead = new AtomicBoolean(true);
                        final Consumer<Byte> headersConsumer = HttpBinStreaming.headersTicker(tempo,
                                len -> {},
                                chunked -> {},
                                cTypeHold::set,
                                partHeaders::put,
                                (n, v) -> {},
                                unused -> tempo.reset(),
                                unused -> {
                                    tempo.reset();
                                    inHead.set(false);
                                }
                        );
                        final Consumer<Byte> bodyConsumer = tempo::write;

                        for (int i = partMark.first; i < partMark.second; i++) {
                            if (inHead.get())
                                headersConsumer.accept(body[i]);
                            else
                                bodyConsumer.accept(body[i]);
                        }

                        final String cd;
                        if ((cd = partHeaders.get(Headers.ContentDisposition)) != null) {
                            String fileName = null, fieldName = null;

                            final String cdl = cd.trim().toLowerCase();
                            if (cdl.startsWith(Headers.FormData) || cdl.startsWith(Headers.Attachment)) {
                                try {
//                                    final MimeType mimed = new MimeType(cd);
                                    final ParameterParser parser = new ParameterParser();
                                    parser.setLowerCaseNames();

                                    final Map<String, String> parameters = parser.parse(cd, ';');

                                    fileName = parameters.get("filename");
                                    fieldName = parameters.get("name");
                                } catch (final Exception ignore) {}
                            }

                            if (isEmpty(fieldName))
                                continue;

                            if (!isEmpty(fileName))
                                form.fileData(fieldName, fileName, tempo.toByteArray(), cTypeHold.get());
                            else if (cTypeHold.get().getBaseType().startsWith("text/"))
                                form.textData(fieldName, tempo.toString(StandardCharsets.UTF_8));
                            else
                                form.binData(fieldName, tempo.toByteArray(), partHeaders);
                        }
                    } catch (final Exception e) {
                        logger.error("Cant process multi-part form part: " + e.getMessage(), e);
                    }
                }
            } catch (final Exception e) {
                logger.error(e.getMessage(), e);
            }

            return form;
        }

        public String cookieValue(final String cookieName) {
            return cookies.get(cookieName);
        }

        public String queryParam(final String paramName) {
            if (hardPath == null) {
                final int pos;

                if ((pos = path.indexOf('?')) > 0) {
                    hardPath = path.substring(0, pos);

                    if (!path.endsWith("?"))
                        Arrays.stream(path.substring(pos + 1).split(Pattern.quote("&")))
                                .map(pair -> pair.split(Pattern.quote("=")))
                                .forEach(kv -> query.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
                } else
                    hardPath = path;
            }

            return query.get(paramName);
        }

        public String path() {
            if (hardPath == null)
                queryParam(""); // to fill query & hard path

            return hardPath;
        }

        private void sureBodyRead() {
            if (body != null)
                return;

            try (final ByteArrayOutputStream os = new ByteArrayOutputStream(knownBodyLength > 0 ? knownBodyLength : 512 * 1024)) {
                sureBodyRead(os, preRead != null ? preRead.length : 0);

                body = os.toByteArray();
            } catch (final Exception e) {
                logger.error(e.getMessage(), e);
                body = new byte[0];
            }
        }

        private void sureBodyRead(final OutputStream os, final int size) throws Exception {
            if (body != null)
                return;

            if (preRead != null)
                os.write(preRead);
            preRead = null;

            if (knownBodyLength > 0 && size < knownBodyLength)
                readLengthIsTo(os, knownBodyLength - size);
            else if (chunked) {
                int chunkSize;

                do {
                    chunkSize = getChunkSize();

                    if (chunkSize > 0)
                        for (int i = 0; i < chunkSize; i++) os.write(is.read());
                } while (chunkSize > 0);
            } else
                readRestIsTo(os);
        }

        private int getChunkSize() throws IOException {
            int b;

            try (final ByteArrayOutputStream os = new ByteArrayOutputStream(8)) {
                do {
                    b = is.read();

                    if (Character.digit(b, 16) != -1)
                        os.write(b);
                } while (b != '\n');

                return Integer.parseInt(os.toString(StandardCharsets.US_ASCII), 16);
            }
        }

        private void readLengthIsTo(final OutputStream os, final int length) throws Exception {
            for (int i = 0; i < length; i++) os.write(is.read());
        }

        private void readRestIsTo(final OutputStream os) {
            final byte[] buf = new byte[16 * 1024];
            int read;

            try {
                do {
                    read = is.read(buf);
                    if (read > 0)
                        os.write(buf, 0, read);
                } while (read > 0);
            } catch (final Exception ignore) {
            } finally {
                try {
                    os.flush();
                } catch (final Exception ignore) {
                }
            }
        }

        public void skipBody() {
            try {
                sureBodyRead(OutputStream.nullOutputStream(), preRead == null ? 0 : preRead.length);
            } catch (final Exception ignore) {
            }
        }

        public String remoteAddress() {
            return ip;
        }

        public String header(final String header) {
            return headers.get(header);
        }

        public boolean hasHeader(final String header) {
            return !isEmpty(headers.get(header));
        }

        public Map<String, String> queryMap() {
            return query;
        }

        public Map<String, String> cookieMap() {
            return cookies;
        }
    }

    public static class Response {
        public static final Http.Response jsonSuccess = ok(Json.newObject().put("success", true));
        private static final Logger logger = LoggerFactory.getLogger(Response.class);
        private final Map<String, String> headers;
        private final Set<Cookie> cookies;
        private int code;
        private String message;
        private byte[] payload;
        private Consumer<OutputStream> promise;

        {
            headers = new HashMap<>(2);
            headers.put("Server", "FairHttp/1.0.0");
            headers.put("Connection", "keep-alive");

            cookies = new HashSet<>(2);
        }

        private Response() {
        }

        public Response(final int code, final String message) {
            this.code = code;
            this.message = message;
            setPayload(message.getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);
        }

        public static Response Ok() {
            return new Response(200, "OK");
        }

        public static Response Created() {
            return new Response(201, "Created");
        }

        public static Response NoContent() {
            return new Response(204, "No content");
        }

        public static Response NotFound() {
            return new Response(404, "Not found");
        }

        public static Response Forbidden() {
            return new Response(403, "Access forbidden");
        }

        public static Response ServerError() {
            return new Response(500, "Internal error");
        }

        public void setPromise(final Consumer<OutputStream> promise) {
            if (promise == null)
                return;

            this.promise = promise;
            this.payload = null;
        }

        public void setPayload(final byte[] payload, final MimeType contentType) {
            if (isEmpty(payload))
                return;

            if (contentType == null)
                throw new NullPointerException("Content-Type");

            this.payload = payload;

            header(Headers.ContentType, contentType.toString());
            header(Headers.ContentLength, String.valueOf(payload.length));
            promise = null;
        }

        public void header(final String name, final Object value) {
            if (isEmpty(name) || isEmpty(value))
                return;

            headers.put(name.trim(), notNull(value));
        }

        void writeTo(final OutputStream os) throws IOException {
            os.write(PROTO);
            os.write((" " + code + (isEmpty(message) ? "" : " " + message)).getBytes(StandardCharsets.US_ASCII));
            os.write(FEED);

            header("Date", LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));

            for (final Map.Entry<String, String> e : headers.entrySet())
                if (!isEmpty(e.getValue()) && !isEmpty(e.getKey())) {
                    os.write((e.getKey() + ": " + e.getValue()).getBytes(StandardCharsets.UTF_8));
                    os.write(FEED);
                }

            for (final Cookie c : cookies) {
                os.write((Headers.ResponseCookies + ": " + c).getBytes(StandardCharsets.UTF_8));
                os.write(FEED);
            }

            os.write(FEED);

            if (!isEmpty(payload) && !(this instanceof WebSocket))
                os.write(payload);
            else if (promise != null)
                promise.accept(os);

            os.flush();
        }

        @Override
        public String toString() {
            return code + (isEmpty(message) ? "" : " " + message) + (payload == null ? "" : " :: " + payload.length + " bytes");
        }

        public Response withCookie(final Cookie... cookies) {
            if (!isEmpty(cookies))
                for (final Cookie c : cookies)
                    if (c != null)
                        this.cookies.add(c);

            return this;
        }

        public Response withHeader(final String name, final String value) {
            if (!isEmpty(value) && !isEmpty(name))
                header(name.trim(), value.trim());

            return this;
        }

        public int size() {
            return payload == null ? -1 : payload.length;
        }
    }

    public static abstract class WebSocket extends Response implements Consumer<Byte> {
        private static final Logger logger = LoggerFactory.getLogger(WebSocket.class);

        private IExtension extension;
        private IProtocol protocol;
        private OutputStream os;
        private ObjectMapper om;
        private DocumentBuilder xb;
        private Transformer tr;
        private int frameStage, payloadlength;
        private AFrame frame;
        private Frame incompleteframe;
        private Opcode optcode;
        private boolean mask;
        private Drive drive;
        private byte[] payload, maskkey;
        private Consumer<Void> closeListener;

        public WebSocket() {
            super(101, "Websocket Connection Upgrade");

            header(Headers.Upgrade, "websocket");
            header(Headers.Connection, Headers.Upgrade);
        }

        public WebSocket(final IExtension extension) {
            this(extension, null);
        }

        public WebSocket(final IExtension extension, final IProtocol protocol) {
            this();
            this.extension = extension;
            this.protocol = protocol;
        }

        @Override
        public final void accept(final Byte b0) {
            if (b0 == null || b0 == -1)
                return;

            final byte b = b0;

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
            } else if (curop == Opcode.PING) {
                onPing();
            } else if (curop == Opcode.PONG)
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

        void prepare(final Request request, final OutputStream os, final Consumer<Void> closeListener) throws NoSuchAlgorithmException {
            if (!WS_VERSION.equals(request.header(Headers.SecWebsocketVersion)))
                throw new IllegalStateException("Wrong websocket version: " + request.header(Headers.SecWebsocketVersion) + ", expected: " + WS_VERSION);

            final String id = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA1").digest((request.header(Headers.SecWebsocketKey) + RFC_KEY_UUID).getBytes()));
            header(Headers.SecWebsocketAccept, id);
            if (extension != null && extension.acceptProvidedExtensionAsServer(request.header(Headers.SecWebsocketExtensions)))
                header(Headers.SecWebsocketExtensions, extension.getProvidedExtensionAsServer());
            else if (extension != null)
                throw new IllegalStateException("Cant accept requested extenstion(s): " + request.header(Headers.SecWebsocketExtensions));

            if (protocol != null && protocol.acceptProtocol(request.header(Headers.SecWebsocketProtocols)))
                header(Headers.SecWebsocketProtocols, protocol.getProvidedProtocol());
            else if (protocol != null)
                throw new IllegalStateException("Cant accept requested protocol(s): " + request.header(Headers.SecWebsocketProtocols));

            this.os = os;
            this.closeListener = closeListener;

            if (extension == null)
                extension = new DefaultExtension();
        }

        public final void close(final int code, final String reason) {
            close(code, reason, false);
        }

        private void close(final int code, final String reason, final boolean remote) {
            try {
                onClose(code, reason, remote);
            } catch (final Exception ignore) {
            }
            try {
                os.close();
            } catch (final Exception ignore) {
            }
            try {
                closeListener.accept(null);
            } catch (final Exception ignore) {
            }
        }

        public abstract void onJson(JsonNode json);

        public abstract void onXml(Document xml);

        public abstract void onText(String text);

        public abstract void onBytes(byte[] bytes);

        public abstract void onPing();

        public abstract void onPong();

        public abstract void onClose(int code, String reason, boolean remote);

        public void close() {
            try {
                sendFrame(new CloseFrame());
            } catch (final Exception ignore) {
            }

            close(CloseFrame.NORMAL, null, false);
        }

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

            try {
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
    }

    private abstract static class Adapter extends WebSocket {
        @Override
        public void onPing() {
            sendFrame(new PongFrame());
        }

        @Override
        public void onPong() {
        }

        @Override
        public void onClose(final int code, final String reason, final boolean remote) {
        }
    }

    public abstract static class JsonSocket extends Adapter {
        @Override
        public final void send(final Document message) {
            throw new IllegalStateException("Cant send anything but JSON");
        }

        @Override
        public final void send(final String message) {
            throw new IllegalStateException("Cant send anything but JSON");
        }

        @Override
        public final void send(final byte[] message) {
            throw new IllegalStateException("Cant send anything but JSON");
        }

        @Override
        public final void onXml(final Document ignore) {
        }

        @Override
        public final void onText(final String ignore) {
        }

        @Override
        public final void onBytes(final byte[] ignore) {
        }
    }

    public static class Cookie {
        private static final String tspecials = ",; ";  // deliberately includes space

        private final String name;  // NAME= ... "$Name" style is reserved
        private final long whenCreated;

        private String value;       // value of NAME

        private String domain;      // Domain=VALUE ... domain that sees cookie
        private long maxAge = -1;  // Max-Age=VALUE ... cookies auto-expire
        private String path;        // Path=VALUE ... URLs that see the cookie
        private String portlist;    // Port[="portlist"] ... the port cookie may be returned to
        private boolean secure;     // Secure ... e.g. use SSL
        private boolean httpOnly;   // HttpOnly ... i.e. not accessible to scripts
        private int version = 1;    // Version=1 ... RFC 2965 style
        private SameSite sameSite;

        public Cookie(final String name, final String value) {
            if (isEmpty(name) || !isToken(name) || name.charAt(0) == '$')
                throw new IllegalArgumentException("Illegal cookie name");

            this.name = name.trim();
            this.value = value;
            secure = false;

            whenCreated = System.currentTimeMillis();
            portlist = null;
        }

        public Cookie(
                String name,
                String value,
                Integer maxAge,
                String path,
                String domain,
                boolean secure,
                boolean httpOnly,
                SameSite sameSite) {
            this.name = name;
            this.value = value;
            this.maxAge = maxAge;
            this.path = path;
            this.domain = domain;
            this.secure = secure;
            this.httpOnly = httpOnly;
            this.sameSite = sameSite;
            whenCreated = System.currentTimeMillis();
        }

        public static CookieBuilder builder(String name, String value) {
            return new CookieBuilder(name, value);
        }

        public String getName() {
            return name;
        }

        public long getWhenCreated() {
            return whenCreated;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(final String domain) {
            this.domain = domain;
        }

        public long getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(final long maxAge) {
            this.maxAge = maxAge;
        }

        public String getPath() {
            return path;
        }

        public void setPath(final String path) {
            this.path = path;
        }

        public String getPortlist() {
            return portlist;
        }

        public void setPortlist(final String portlist) {
            this.portlist = portlist;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(final boolean secure) {
            this.secure = secure;
        }

        public boolean isHttpOnly() {
            return httpOnly;
        }

        public void setHttpOnly(final boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public int getVersion() {
            return version;
        }

        public void setVersion(final int version) {
            this.version = version;
        }

        private boolean isToken(final String value) {
            int len = value.length();

            for (int i = 0; i < len; i++) {
                char c = value.charAt(i);

                if (c < 0x20 || c >= 0x7f || tspecials.indexOf(c) != -1)
                    return false;
            }
            return true;
        }

        public SameSite getSameSite() {
            return sameSite;
        }

        public void setSameSite(final SameSite sameSite) {
            this.sameSite = sameSite;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append(getName()).append("=\"").append(getValue()).append('"');
            if (getPath() != null)
                sb.append("; Path=\"").append(getPath()).append('"');
            if (getDomain() != null)
                sb.append("; Domain=\"").append(getDomain()).append('"');
            if (getPortlist() != null)
                sb.append("; Port=\"").append(getPortlist()).append('"');
            if (getMaxAge() > -1)
                sb.append("; Expires=").append(LocalDateTime.now().plus(getMaxAge(), ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
            if (isSecure())
                sb.append("; Secure");
            if (isHttpOnly())
                sb.append("; HttpOnly");
            if (getSameSite() != null)
                sb.append("; SameSite=\"").append(getSameSite().value()).append('"');

            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof HttpCookie))
                return false;
            HttpCookie other = (HttpCookie) obj;

            return equalsIgnoreCase(getName(), other.getName()) &&
                    equalsIgnoreCase(getDomain(), other.getDomain()) &&
                    Objects.equals(getPath(), other.getPath());
        }

        @Override
        public int hashCode() {
            int h1 = name.toLowerCase().hashCode();
            int h2 = (domain != null) ? domain.toLowerCase().hashCode() : 0;
            int h3 = (path != null) ? path.hashCode() : 0;

            return h1 + h2 + h3;
        }

        private boolean equalsIgnoreCase(String s, String t) {
            if (s == t) return true;
            if ((s != null) && (t != null)) {
                return s.equalsIgnoreCase(t);
            }
            return false;
        }
    }

    public static class CookieBuilder {

        private String name;
        private String value;
        private Integer maxAge;
        private String path = "/";
        private String domain;
        private boolean secure = false;
        private boolean httpOnly = true;
        private SameSite sameSite;

        private CookieBuilder(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public CookieBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public CookieBuilder withValue(String value) {
            this.value = value;
            return this;
        }

        public CookieBuilder withMaxAge(Duration maxAge) {
            this.maxAge = (int) maxAge.getSeconds();
            return this;
        }

        public CookieBuilder withPath(String path) {
            this.path = path;
            return this;
        }

        public CookieBuilder withDomain(String domain) {
            this.domain = domain;
            return this;
        }

        public CookieBuilder withSecure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public CookieBuilder withHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        public CookieBuilder withSameSite(SameSite sameSite) {
            this.sameSite = sameSite;
            return this;
        }

        public Cookie build() {
            return new Cookie(
                    this.name,
                    this.value,
                    this.maxAge,
                    this.path,
                    this.domain,
                    this.secure,
                    this.httpOnly,
                    this.sameSite);
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
}
