package io.siggi.beatsaber.metadatacollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.siggi.beatsaber.metadatacollector.data.datapuller.ColorType;
import io.siggi.beatsaber.metadatacollector.data.datapuller.ELiveDataEventTriggers;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Util {
    public static final Gson gson;
    public static final Gson gsonPretty;

    static {
        GsonBuilder builder = new GsonBuilder();

        builder.registerTypeAdapter(ColorType.class, new TypeAdapter<ColorType>() {
            @Override
            public ColorType read(JsonReader reader) throws IOException {
                if (reader.peek() != JsonToken.NUMBER) {
                    reader.skipValue();
                    return null;
                }
                int value = reader.nextInt();
                switch (value) {
                    case 0:
                        return ColorType.ColorA;
                    case 1:
                        return ColorType.ColorB;
                    default:
                        return ColorType.None;
                }
            }

            @Override
            public void write(JsonWriter writer, ColorType colorType) throws IOException {
                if (colorType == null) {
                    writer.value(-1);
                    return;
                }
                switch (colorType) {
                    case ColorA:
                        writer.value(0);
                        return;
                    case ColorB:
                        writer.value(1);
                        return;
                    default:
                        writer.value(-1);
                        return;
                }
            }
        });

        builder.registerTypeAdapter(ELiveDataEventTriggers.class, new TypeAdapter<ELiveDataEventTriggers>() {
            @Override
            public ELiveDataEventTriggers read(JsonReader reader) throws IOException {
                if (reader.peek() != JsonToken.NUMBER) {
                    reader.skipValue();
                    return null;
                }
                int value = reader.nextInt();
                switch (value) {
                    case 1:
                        return ELiveDataEventTriggers.TimerElapsed;
                    case 2:
                        return ELiveDataEventTriggers.NoteMissed;
                    case 3:
                        return ELiveDataEventTriggers.EnergyChange;
                    case 4:
                        return ELiveDataEventTriggers.ScoreChange;
                    default:
                        return ELiveDataEventTriggers.Unknown;
                }
            }

            @Override
            public void write(JsonWriter writer, ELiveDataEventTriggers eventTrigger) throws IOException {
                if (eventTrigger == null) {
                    writer.value(0);
                    return;
                }
                switch (eventTrigger) {
                    case TimerElapsed:
                        writer.value(1);
                        return;
                    case NoteMissed:
                        writer.value(2);
                        return;
                    case EnergyChange:
                        writer.value(3);
                        return;
                    case ScoreChange:
                        writer.value(4);
                        return;
                    default:
                        writer.value(0);
                        return;
                }
            }
        });

        gson = builder.create();
        builder.setPrettyPrinting();
        gsonPretty = builder.create();
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha256(byte[] data) {
        return sha256().digest(data);
    }

    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    public static String computeObsAuthenticationString(String password, String challenge, String salt) {
        MessageDigest sha256 = sha256();

        String secretString = password + salt;
        byte[] secretHash = sha256.digest(secretString.getBytes(StandardCharsets.UTF_8));
        String encodedSecret = base64Encode(secretHash);

        String resultString = encodedSecret + challenge;
        byte[] resultHash = sha256.digest(resultString.getBytes(StandardCharsets.UTF_8));

        return base64Encode(resultHash);
    }

    public static int read(InputStream in) throws IOException {
        int value = in.read();
        if (value == -1) throw new EOFException();
        return value;
    }

    public static int readInt(InputStream in) throws IOException {
        return (read(in) << 24)
                | (read(in) << 16)
                | (read(in) << 8)
                | read(in);
    }

    public static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 8) & 0xff);
        out.write(value & 0xff);
    }

    public static long readLong(InputStream in) throws IOException {
        return (((long) read(in)) << 56)
                | (((long) read(in)) << 48)
                | (((long) read(in)) << 40)
                | (((long) read(in)) << 32)
                | (((long) read(in)) << 24)
                | (((long) read(in)) << 16)
                | (((long) read(in)) << 8)
                | ((long) read(in));
    }

    public static void writeLong(OutputStream out, long value) throws IOException {
        out.write((int) ((value >> 56) & 0xff));
        out.write((int) ((value >> 48) & 0xff));
        out.write((int) ((value >> 40) & 0xff));
        out.write((int) ((value >> 32) & 0xff));
        out.write((int) ((value >> 24) & 0xff));
        out.write((int) ((value >> 16) & 0xff));
        out.write((int) ((value >> 8) & 0xff));
        out.write((int) (value & 0xff));
    }

    public static String readString(InputStream in) throws IOException {
        int length = readInt(in);
        byte[] data = new byte[length];
        int amountRead = 0;
        while (amountRead < length) {
            int count = in.read(data, amountRead, length - amountRead);
            if (count == -1) throw new EOFException();
            amountRead += count;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void writeString(OutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        writeInt(out, data.length);
        out.write(data);
    }

    public static String timecode(long time) {
        long ms = time % 1000L;
        time /= 1000L;
        long sec = time % 60L;
        time /= 60L;
        long min = time % 60L;
        time /= 60L;
        long hr = time;

        StringBuilder output = new StringBuilder(Long.toString(ms));
        while (output.length() < 3) output.insert(0, "0");
        output.insert(0, ".");
        output.insert(0, sec);
        if (sec < 10) output.insert(0, "0");
        output.insert(0, ":");
        output.insert(0, min);
        if (min < 10) output.insert(0, "0");
        output.insert(0, ":");
        output.insert(0, hr);
        if (hr < 10) output.insert(0, "0");
        return output.toString();
    }
}
