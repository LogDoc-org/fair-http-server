package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

import static org.logdoc.fairhttp.service.tools.Strings.isValidUTF8;

public class TextFrame extends DataFrame {

    public TextFrame() {
        super(Opcode.TEXT);
    }

    @Override
    public boolean isValid() {
        return super.isValid() && isValidUTF8(getPayloadData());
    }
}
