package org.logdoc.fairhttp.service.tools.websocket.extension;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;
import org.logdoc.fairhttp.service.tools.websocket.frames.*;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class PMDeflateExtension extends CompressionExtension {
    private static final String EXTENSION_REGISTERED_NAME = "permessage-deflate"; // https://tools.ietf.org/html/rfc7692#section-9
    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";
    // private static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";
    // private static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";
    // private static final int serverMaxWindowBits = 1 << 15;
    // private static final int clientMaxWindowBits = 1 << 15;
    private static final byte[] TAIL_BYTES = {(byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF};
    private static final int BUFFER_SIZE = 1 << 10;

    private int threshold = 1024;

    private boolean serverNoContextTakeover = true;
    private boolean clientNoContextTakeover = false;

    private final Map<String, String> requestedParameters = new LinkedHashMap<>();

    private Inflater inflater = new Inflater(true);
    private Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);

    public Inflater getInflater() {
        return inflater;
    }

    public void setInflater(Inflater inflater) {
        this.inflater = inflater;
    }

    public Deflater getDeflater() {
        return deflater;
    }

    public void setDeflater(Deflater deflater) {
        this.deflater = deflater;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public boolean isServerNoContextTakeover() {
        return serverNoContextTakeover;
    }

    public void setServerNoContextTakeover(boolean serverNoContextTakeover) {
        this.serverNoContextTakeover = serverNoContextTakeover;
    }

    public boolean isClientNoContextTakeover() {
        return clientNoContextTakeover;
    }

    public void setClientNoContextTakeover(boolean clientNoContextTakeover) {
        this.clientNoContextTakeover = clientNoContextTakeover;
    }

    // https://tools.ietf.org/html/rfc7692#section-7.2.2
    @Override
    public void decodeFrame(final Frame inputFrame) throws ExtensionError {
        if (!(inputFrame instanceof DataFrame))
            return;

        if (!inputFrame.isRSV1() && inputFrame.getOpcode() != Opcode.CONTINUOUS)
            return;

        if (inputFrame.getOpcode() == Opcode.CONTINUOUS && inputFrame.isRSV1())
            throw new ExtensionError(CloseFrame.POLICY_VALIDATION, "RSV1 bit can only be set for the first frame.");

        try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            decompress(inputFrame.getPayloadData(), output);

            if (inflater.getRemaining() > 0) {
                inflater = new Inflater(true);
                decompress(inputFrame.getPayloadData(), output);
            }

            if (inputFrame.isFin()) {
                decompress(TAIL_BYTES, output);

                if (clientNoContextTakeover)
                    inflater = new Inflater(true);
            }

            ((AFrame) inputFrame).setPayload(output.toByteArray());
        } catch (final Exception e) {
            throw new ExtensionError(CloseFrame.POLICY_VALIDATION, e.getMessage());
        }
    }

    private void decompress(final byte[] data, final ByteArrayOutputStream outputBuffer) throws DataFormatException {
        inflater.setInput(data);
        byte[] buffer = new byte[BUFFER_SIZE];

        int bytesInflated;

        while ((bytesInflated = inflater.inflate(buffer)) > 0)
            outputBuffer.write(buffer, 0, bytesInflated);
    }

    @Override
    public void encodeFrame(final Frame inputFrame) throws ExtensionError {
        if (!(inputFrame instanceof DataFrame))
            return;

        byte[] payloadData = inputFrame.getPayloadData();
        if (payloadData.length < threshold)
            return;

        if (!(inputFrame instanceof ContinuousFrame))
            ((DataFrame) inputFrame).setRSV1(true);

        deflater.setInput(payloadData);
        try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesCompressed;

            while ((bytesCompressed = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)) > 0)
                output.write(buffer, 0, bytesCompressed);

            byte[] outputBytes = output.toByteArray();
            int outputLength = outputBytes.length;

            // https://tools.ietf.org/html/rfc7692#section-7.2.1
            if (inputFrame.isFin()) {
                if (endsWithTail(outputBytes))
                    outputLength -= TAIL_BYTES.length;

                if (serverNoContextTakeover) {
                    deflater.end();
                    deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                }
            }

            ((AFrame) inputFrame).setPayload(Arrays.copyOfRange(outputBytes, 0, outputLength));
        } catch (final Exception e) {
            throw new ExtensionError(CloseFrame.EXTENSION, e.getMessage());
        }
    }

    private static boolean endsWithTail(byte[] data) {
        if (data.length < 4)
            return false;

        for (int i = 0, length = data.length; i < TAIL_BYTES.length; i++)
            if (TAIL_BYTES[i] != data[length - TAIL_BYTES.length + i])
                return false;

        return true;
    }

    @Override
    public boolean acceptProvidedExtensionAsServer(String inputExtension) {
        String[] requestedExtensions = inputExtension.split(",");
        for (String extension : requestedExtensions) {
            ExtensionRequestData extensionData = ExtensionRequestData.parseExtensionRequest(extension);
            if (!EXTENSION_REGISTERED_NAME.equalsIgnoreCase(extensionData.getExtensionName())) {
                continue;
            }

            // Holds parameters that peer client has sent.
            Map<String, String> headers = extensionData.getExtensionParameters();
            requestedParameters.putAll(headers);
            if (requestedParameters.containsKey(CLIENT_NO_CONTEXT_TAKEOVER)) {
                clientNoContextTakeover = true;
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean acceptProvidedExtensionAsClient(String inputExtension) {
        final String[] requestedExtensions = inputExtension.split(",");
        for (final String extension : requestedExtensions) {
            if (!EXTENSION_REGISTERED_NAME.equalsIgnoreCase(ExtensionRequestData.parseExtensionRequest(extension).getExtensionName()))
                continue;

            return true;
        }

        return false;
    }

    @Override
    public String getProvidedExtensionAsClient() {
        requestedParameters.put(CLIENT_NO_CONTEXT_TAKEOVER, ExtensionRequestData.EMPTY_VALUE);
        requestedParameters.put(SERVER_NO_CONTEXT_TAKEOVER, ExtensionRequestData.EMPTY_VALUE);

        return EXTENSION_REGISTERED_NAME + "; " + SERVER_NO_CONTEXT_TAKEOVER + "; " + CLIENT_NO_CONTEXT_TAKEOVER;
    }

    @Override
    public String getProvidedExtensionAsServer() {
        return EXTENSION_REGISTERED_NAME + "; " + SERVER_NO_CONTEXT_TAKEOVER + (clientNoContextTakeover ? "; " + CLIENT_NO_CONTEXT_TAKEOVER : "");
    }

    @Override
    public IExtension copyInstance() {
        return new PMDeflateExtension();
    }

    @Override
    public boolean isFrameValid(final Frame frame) {
        if ((frame instanceof ContinuousFrame) && (frame.isRSV1() || frame.isRSV2() || frame.isRSV3()))
            return false;

        return super.isFrameValid(frame);
    }

    @Override
    public String toString() {
        return "PerMessageDeflateExtension";
    }
}
