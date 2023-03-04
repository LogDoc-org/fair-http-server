package org.logdoc.fairhttp.service.tools.websocket.frames;


import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public abstract class DataFrame extends AFrame {

    public DataFrame(final Opcode opcode) {
        super(opcode);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
