package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public abstract class ControlFrame extends AFrame {

    public ControlFrame(final Opcode opcode) {
        super(opcode);
    }

    @Override
    public boolean isValid() {
        return isFin() && !isRSV1() && !isRSV2() && !isRSV3();
    }
}
