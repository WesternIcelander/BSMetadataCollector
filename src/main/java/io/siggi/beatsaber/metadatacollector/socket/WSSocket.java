package io.siggi.beatsaber.metadatacollector.socket;

import io.siggi.simplewebsocket.SimpleWebsocket;
import io.siggi.simplewebsocket.WebSocketListener;
import io.siggi.simplewebsocket.WebSocketMessage;

import java.net.URI;

public abstract class WSSocket {

    protected WSSocket(URI uri, WebSocketListener wsListener) {
        this.uri = uri;
        this.wsListener = wsListener;
    }

    private SimpleWebsocket socket;

    private boolean started = false;
    private boolean stopped = false;

    private final URI uri;
    private final WebSocketListener wsListener;
    private final WebSocketListener closeListener = new WebSocketListener() {
        @Override
        public void receivedMessage(SimpleWebsocket simpleWebsocket, WebSocketMessage webSocketMessage) {
        }

        @Override
        public void socketClosed(SimpleWebsocket simpleWebsocket) {
            if (!stopped) {
                connect();
            }
        }
    };

    public void start() {
        if (started) {
            return;
        }
        started = true;
        if (socket == null) connect();
    }

    public void stop() {
        if (!started || stopped) {
            return;
        }
        stopped = true;
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private void connect() {
        try {
            socket = SimpleWebsocket.connect(uri);
        } catch (Exception e) {
            delayedReconnect();
            return;
        }
        socket.addListener(wsListener);
        socket.addListener(closeListener);
        socket.useNonBlockingMode(5000L);
    }

    private void delayedReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(1000L);
            } catch (Exception ignored) {
            }
            if (stopped) return;
            connect();
        }).start();
    }
}
