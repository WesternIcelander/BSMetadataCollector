package io.siggi.beatsaber.metadatacollector.socket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.siggi.simplewebsocket.SimpleWebsocket;
import io.siggi.simplewebsocket.WebSocketListener;
import io.siggi.simplewebsocket.WebSocketMessage;

import java.net.URI;

import static io.siggi.beatsaber.metadatacollector.Util.computeObsAuthenticationString;

public class OBSSocket extends WSSocket {

    public OBSSocket(URI uri, String password, Listener listener) {
        super(uri, new WebSocketListener() {
            @Override
            public void receivedMessage(SimpleWebsocket socket, WebSocketMessage message) {
                if (message.isText()) {
                    try {
                        JsonObject object = JsonParser.parseString(message.getText()).getAsJsonObject();
                        int opCode = object.get("op").getAsInt();
                        if (opCode == 0) {
                            JsonObject helloObject = object.getAsJsonObject("d");
                            JsonObject authObject = helloObject.getAsJsonObject("authentication");
                            String authenticationString = null;
                            if (authObject != null) {
                                String challenge = authObject.get("challenge").getAsString();
                                String salt = authObject.get("salt").getAsString();
                                authenticationString = computeObsAuthenticationString(password, challenge, salt);
                            }
                            JsonObject reply = new JsonObject();
                            JsonObject replyData = new JsonObject();
                            reply.add("d", replyData);
                            reply.addProperty("op", 1);
                            replyData.addProperty("rpcVersion", 1);
                            if (authenticationString != null) {
                                replyData.addProperty("authentication", authenticationString);
                            }
                            String replyString = reply.toString();
                            socket.send(WebSocketMessage.create(replyString));
                        } else if (opCode == 5) {
                            JsonObject eventObject = object.getAsJsonObject("d");
                            String eventType = eventObject.get("eventType").getAsString();
                            if (!eventType.equals("RecordStateChanged")) return;
                            JsonObject eventData = eventObject.getAsJsonObject("eventData");
                            String outputState = eventData.get("outputState").getAsString();
                            JsonElement outputPathElement = eventData.get("outputPath");
                            String outputPath = outputPathElement == null ? null : outputPathElement.getAsString();
                            if (outputState.equals("OBS_WEBSOCKET_OUTPUT_STARTED")) {
                                listener.recordingStarted(outputPath);
                            } else if (outputState.equals("OBS_WEBSOCKET_OUTPUT_STOPPED")) {
                                listener.recordingStopped(outputPath);
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }

            @Override
            public void socketClosed(SimpleWebsocket socket) {
            }
        });
    }

    public interface Listener {
        void recordingStarted(String filepath);
        void recordingStopped(String filepath);
    }
}
