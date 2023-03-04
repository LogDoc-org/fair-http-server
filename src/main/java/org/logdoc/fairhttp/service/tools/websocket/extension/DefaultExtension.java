package org.logdoc.fairhttp.service.tools.websocket.extension;

import org.logdoc.fairhttp.service.tools.websocket.frames.Frame;

public class DefaultExtension implements IExtension {

    @Override
    public void decodeFrame(Frame inputFrame) throws ExtensionError {
    }

    @Override
    public void encodeFrame(Frame inputFrame) throws ExtensionError {
    }

    @Override
    public boolean acceptProvidedExtensionAsServer(String inputExtension) {
        return true;
    }

    @Override
    public boolean acceptProvidedExtensionAsClient(String inputExtension) {
        return true;
    }

    @Override
    public boolean isFrameValid(Frame inputFrame) {
        return !inputFrame.isRSV1() && !inputFrame.isRSV2() && !inputFrame.isRSV3();
    }

    @Override
    public String getProvidedExtensionAsClient() {
        return "";
    }

    @Override
    public String getProvidedExtensionAsServer() {
        return "";
    }

    @Override
    public IExtension copyInstance() {
        return new DefaultExtension();
    }

    public void reset() {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass();
    }
}
