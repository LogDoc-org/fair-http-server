package org.logdoc.fairhttp.service.tools.websocket.frames;

import org.logdoc.fairhttp.service.tools.websocket.Opcode;

public class PingFrame extends ControlFrame {

  public PingFrame() {
    super(Opcode.PING);
  }
}
