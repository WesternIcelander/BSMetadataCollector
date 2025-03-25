package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;
import io.siggi.tools.Deduplicator;
import io.siggi.tools.io.IO;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.siggi.beatsaber.metadatacollector.Util.gson;
import static io.siggi.beatsaber.metadatacollector.Util.readInt;
import static io.siggi.beatsaber.metadatacollector.Util.readLong;
import static io.siggi.beatsaber.metadatacollector.Util.readString;

public class CollectedMetadata {

    private static final byte[] BSMDC_HEADER = "BSMDC\n".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_SUPPORTED_VERSION = 1;

    public static CollectedMetadata read(InputStream in) throws IOException {
        CollectedMetadata data = new CollectedMetadata();

        Deduplicator<String> deduplicator = new Deduplicator<>(false);

        byte[] header = new byte[BSMDC_HEADER.length];
        IO.readFully(in, header);
        if (!Arrays.equals(header, BSMDC_HEADER)) {
            throw new IOException("Not BSMDC file!");
        }
        int fileVersion = readInt(in);
        if (fileVersion > MAX_SUPPORTED_VERSION) {
            throw new IOException("BSMDC file created by a newer version not supported.");
        }
        data.startTime = readLong(in);
        while (true) {
            int type = in.read();
            if (type == -1) break;
            long time = fileVersion >= 1 ? readLong(in) : 0L;
            if (type == 1) {
                LevelInfo levelInfo = gson.fromJson(readString(in), LevelInfo.class);
                levelInfo.TimeSinceRecordingStart = time == 0L ? (levelInfo.UnixTimestamp - data.startTime) : time;
                deduplicateStrings(deduplicator, levelInfo);
                data.levelInfos.add(levelInfo);
            } else if (type == 2) {
                LiveData liveData = gson.fromJson(readString(in), LiveData.class);
                liveData.TimeSinceRecordingStart = time == 0L ? (liveData.UnixTimestamp - data.startTime) : time;
                deduplicateStrings(deduplicator, liveData);
                data.liveData.add(liveData);
            }
        }

        return data;
    }

    private static void deduplicateStrings(Deduplicator<String> deduplicator, Object object) {
        Field[] fields = object.getClass().getFields();
        for (Field field : fields) {
            try {
                if (field.getType() == String.class) {
                    field.set(object, deduplicator.deduplicate((String) field.get(object)));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    public long startTime;
    public final List<LevelInfo> levelInfos = new ArrayList<>();
    public final List<LiveData> liveData = new ArrayList<>();
}
