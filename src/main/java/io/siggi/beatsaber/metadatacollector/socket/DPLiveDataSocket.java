package io.siggi.beatsaber.metadatacollector.socket;

import io.siggi.beatsaber.metadatacollector.Util;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;
import io.siggi.simplewebsocket.SimpleWebsocket;
import io.siggi.simplewebsocket.WebSocketListener;
import io.siggi.simplewebsocket.WebSocketMessage;

import java.net.URI;

public class DPLiveDataSocket extends WSSocket {

    public DPLiveDataSocket(URI uri, Listener listener) {
        super(uri, new WebSocketListener() {
            @Override
            public void receivedMessage(SimpleWebsocket socket, WebSocketMessage message) {
                if (!message.isText()) return;
                LiveData liveData = Util.gson.fromJson(message.getText(), LiveData.class);
                listener.liveDataReceived(liveData);
            }

            @Override
            public void socketClosed(SimpleWebsocket socket) {
            }
        });
    }

    @FunctionalInterface
    public interface Listener {
        void liveDataReceived(LiveData liveData);
    }
}
