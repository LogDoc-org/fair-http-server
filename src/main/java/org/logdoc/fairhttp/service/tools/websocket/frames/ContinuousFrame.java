package org.logdoc.fairhttp.service.tools.websocket.frames;


import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public class ContinuousFrame extends DataFrame {

    public ContinuousFrame() {
        super(Opcode.CONTINUOUS);
    }
}
