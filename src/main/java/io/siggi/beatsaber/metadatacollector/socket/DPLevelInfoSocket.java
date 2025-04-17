package io.siggi.beatsaber.metadatacollector.socket;

import io.siggi.beatsaber.metadatacollector.Util;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.simplewebsocket.SimpleWebsocket;
import io.siggi.simplewebsocket.WebSocketListener;
import io.siggi.simplewebsocket.WebSocketMessage;

import java.net.URI;

public class DPLevelInfoSocket extends WSSocket {

    public DPLevelInfoSocket(URI uri, Listener listener) {
        super(uri, new WebSocketListener() {
            @Override
            public void receivedMessage(SimpleWebsocket socket, WebSocketMessage message) {
                if (!message.isText()) return;
                LevelInfo levelInfo = Util.gson.fromJson(message.getText(), LevelInfo.class);
                listener.levelInfoReceived(message.getText(), levelInfo);
            }

            @Override
            public void socketClosed(SimpleWebsocket socket) {
            }
        });
    }

    @FunctionalInterface
    public interface Listener {
        void levelInfoReceived(String rawJson, LevelInfo levelInfo);
    }
}
