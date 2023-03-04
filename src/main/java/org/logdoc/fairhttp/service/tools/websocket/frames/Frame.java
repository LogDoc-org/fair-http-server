package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public interface Frame {

    boolean isFin();

    boolean isRSV1();

    boolean isRSV2();

    boolean isRSV3();

    boolean getTransfereMasked();

    Opcode getOpcode();

    byte[] getPayloadData();

    void append(Frame nextframe);
}
