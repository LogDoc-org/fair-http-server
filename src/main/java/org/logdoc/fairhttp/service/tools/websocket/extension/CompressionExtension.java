package org.logdoc.fairhttp.service.tools.websocket.extension;


import org.logdoc.fairhttp.service.tools.websocket.frames.ControlFrame;
import org.logdoc.fairhttp.service.tools.websocket.frames.DataFrame;
import org.logdoc.fairhttp.service.tools.websocket.frames.Frame;

public abstract class CompressionExtension extends DefaultExtension {

    @Override
    public boolean isFrameValid(Frame inputFrame) {
        return ((!(inputFrame instanceof DataFrame)) || (!inputFrame.isRSV2() && !inputFrame.isRSV3())) && ((!(inputFrame instanceof ControlFrame)) || (!inputFrame.isRSV1() && !inputFrame.isRSV2() && !inputFrame.isRSV3()));
    }
}
