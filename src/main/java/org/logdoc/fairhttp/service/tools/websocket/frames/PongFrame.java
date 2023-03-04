package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public class PongFrame extends ControlFrame {

    public PongFrame() {
        super(Opcode.PONG);
    }

    public PongFrame(final PingFrame pingFrame) {
        super(Opcode.PONG);
        setPayload(pingFrame.getPayloadData());
    }
}
