package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

import java.util.Arrays;

public abstract class AFrame implements Frame {
    private final Opcode optcode;

    private boolean fin;

    private byte[] unmaskedpayload;

    private boolean masked;

    private boolean rsv1;

    private boolean rsv2;

    private boolean rsv3;

    public abstract boolean isValid();

    public AFrame(final Opcode op) {
        optcode = op;
        fin = true;
        masked = false;
        rsv1 = false;
        rsv2 = false;
        rsv3 = false;
    }

    @Override
    public boolean isRSV1() {
        return rsv1;
    }

    @Override
    public boolean isRSV2() {
        return rsv2;
    }

    @Override
    public boolean isRSV3() {
        return rsv3;
    }

    @Override
    public boolean isFin() {
        return fin;
    }

    @Override
    public Opcode getOpcode() {
        return optcode;
    }

    @Override
    public boolean getTransfereMasked() {
        return masked;
    }

    @Override
    public byte[] getPayloadData() {
        return unmaskedpayload;
    }

    @Override
    public void append(final Frame frame) {
        final byte[] b = frame.getPayloadData();

        if (unmaskedpayload == null)
            unmaskedpayload = b;
         else {
            final byte[] tmp = new byte[unmaskedpayload.length + b.length];
            System.arraycopy(unmaskedpayload, 0, tmp, 0, unmaskedpayload.length);
            System.arraycopy(b, 0, tmp, unmaskedpayload.length, b.length);
            unmaskedpayload = tmp;
        }

        fin = frame.isFin();
    }

    @Override
    public String toString() {
        return "AFramedata{" +
                "optcode=" + optcode +
                ", fin=" + fin +
                ", unmaskedpayload=" + (unmaskedpayload == null ? "NIL" : unmaskedpayload.length + "b") +
                ", masked=" + masked +
                ", rsv1=" + rsv1 +
                ", rsv2=" + rsv2 +
                ", rsv3=" + rsv3 +
                '}';
    }

    public void setPayload(final byte[] payload) {
        this.unmaskedpayload = payload;
    }

    public void setFin(boolean fin) {
        this.fin = fin;
    }

    public void setRSV1(boolean rsv1) {
        this.rsv1 = rsv1;
    }

    public void setRSV2(boolean rsv2) {
        this.rsv2 = rsv2;
    }

    public void setRSV3(boolean rsv3) {
        this.rsv3 = rsv3;
    }

    public void setMasked(boolean masked) {
        this.masked = masked;
    }

    public static AFrame get(Opcode opcode) {
        if (opcode == null)
            throw new IllegalArgumentException("Supplied opcode cannot be null");

        switch (opcode) {
            case PING:
                return new PingFrame();
            case PONG:
                return new PongFrame();
            case TEXT:
                return new TextFrame();
            case BINARY:
                return new BinaryFrame();
            case CLOSING:
                return new CloseFrame();
            case CONTINUOUS:
                return new ContinuousFrame();
            default:
                throw new IllegalArgumentException("Supplied opcode is invalid");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        AFrame that = (AFrame) o;

        if (fin != that.fin)
            return false;

        if (masked != that.masked)
            return false;

        if (rsv1 != that.rsv1)
            return false;

        if (rsv2 != that.rsv2)
            return false;

        if (rsv3 != that.rsv3)
            return false;

        if (optcode != that.optcode)
            return false;

        return Arrays.equals(unmaskedpayload, that.unmaskedpayload);
    }

    @Override
    public int hashCode() {
        int result = (fin ? 1 : 0);
        result = 31 * result + optcode.hashCode();
        result = 31 * result + (unmaskedpayload != null ? Arrays.hashCode(unmaskedpayload) : 0);
        result = 31 * result + (masked ? 1 : 0);
        result = 31 * result + (rsv1 ? 1 : 0);
        result = 31 * result + (rsv2 ? 1 : 0);
        result = 31 * result + (rsv3 ? 1 : 0);
        return result;
    }
}
