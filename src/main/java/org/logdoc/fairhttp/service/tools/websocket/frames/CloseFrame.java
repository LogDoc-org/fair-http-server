package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class CloseFrame extends ControlFrame {

    public static final int NORMAL = 1000;
    public static final int GOING_AWAY = 1001;
    public static final int PROTOCOL_ERROR = 1002;
    public static final int REFUSE = 1003;
    public static final int NOCODE = 1005;
    public static final int ABNORMAL_CLOSE = 1006;
    public static final int NO_UTF8 = 1007;
    public static final int POLICY_VALIDATION = 1008;
    public static final int TOOBIG = 1009;
    public static final int EXTENSION = 1010;
    public static final int UNEXPECTED_CONDITION = 1011;
    public static final int SERVICE_RESTART = 1012;
    public static final int TRY_AGAIN_LATER = 1013;
    public static final int BAD_GATEWAY = 1014;
    public static final int TLS_ERROR = 1015;
    public static final int NEVER_CONNECTED = -1;
    public static final int BUGGYCLOSE = -2;
    public static final int FLASHPOLICY = -3;

    private int code;

    private String reason;

    public CloseFrame() {
        super(Opcode.CLOSING);
        setReason("");
        setCode(CloseFrame.NORMAL);
    }

    public void setCode(int code) {
        this.code = code;
        if (code == CloseFrame.TLS_ERROR) {
            this.code = CloseFrame.NOCODE;
            this.reason = "";
        }

        updatePayload();
    }

    public void setReason(final String reason) {
        this.reason = reason == null ? "" : reason;
        updatePayload();
    }

    public int getCloseCode() {
        return code;
    }

    public String getMessage() {
        return reason;
    }

    @Override
    public String toString() {
        return super.toString() + "code: " + code;
    }

    @Override
    public boolean isValid() {
        if (!super.isValid() || (code == CloseFrame.NO_UTF8 && reason.isEmpty())
                || (code == CloseFrame.NOCODE && 0 < reason.length())
                || ((code > CloseFrame.TLS_ERROR && code < 3000)))
            return false;

        return code != CloseFrame.ABNORMAL_CLOSE && code != CloseFrame.TLS_ERROR
                && code != CloseFrame.NOCODE && code <= 4999 && code >= 1000 && code != 1004;
    }

    @Override
    public void setPayload(final byte[] payload) {
        reason = "";

        if (payload.length == 0)
            code = CloseFrame.NORMAL;
        else if (payload.length == 1)
            code = CloseFrame.PROTOCOL_ERROR;
        else {
            code =  ((payload[0] & 0xFF) << 8 | (payload[1] & 0xFF));

            try {
                reason = new String(Arrays.copyOfRange(payload, 2, payload.length), StandardCharsets.UTF_8);
            } catch (final IllegalArgumentException e) {
                code = CloseFrame.NO_UTF8;
                reason = null;
            }
        }
    }

    private void updatePayload() {
        final byte[] rsn = reason.getBytes(StandardCharsets.UTF_8);
        final byte[] out = new byte[rsn.length + 2];
        out[0] = (byte) ((code >>> 8) & 0xff);
        out[1] = (byte) ((code) & 0xff);
        System.arraycopy(rsn, 0, out, 2, rsn.length);

        super.setPayload(out);
    }

    @Override
    public byte[] getPayloadData() {
        if (code == NOCODE)
            return new byte[0];

        return super.getPayloadData();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        if (!super.equals(o))
            return false;


        final CloseFrame that = (CloseFrame) o;

        if (code != that.code)
            return false;

        return Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + code;
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        return result;
    }
}
