package org.logdoc.fairhttp.service.tools.websocket.protocol;

public interface IProtocol {

  // https://www.rfc-editor.org/rfc/rfc6455
  String RFC_KEY_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
  String WS_VERSION = "13";

  boolean acceptProtocol(String inputProtocolHeader);

  String getProvidedProtocol();

  String toString();
}
