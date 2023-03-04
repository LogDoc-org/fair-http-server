package org.logdoc.fairhttp.service.tools.websocket.frames;


import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public class BinaryFrame extends DataFrame {

    public BinaryFrame() {
        super(Opcode.BINARY);
    }
}
