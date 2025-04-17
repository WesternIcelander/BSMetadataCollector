package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.bsmdcstream.BSMDCInStream;
import io.siggi.beatsaber.metadatacollector.bsmdcstream.BSMDCObject;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;
import io.siggi.tools.Deduplicator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static io.siggi.beatsaber.metadatacollector.Util.gson;

public class CollectedMetadata {

    public static CollectedMetadata read(InputStream in) throws IOException {
        return read(new BSMDCInStream(in));
    }

    public static CollectedMetadata read(BSMDCInStream in) throws IOException {
        CollectedMetadata data = new CollectedMetadata();

        Deduplicator<String> deduplicator = new Deduplicator<>(false);

        int fileVersion = in.fileVersion();
        data.startTime = in.startTime();

        BSMDCObject object;
        while ((object = in.read()) != null) {
            switch (object.type()) {
                case 1: {
                    LevelInfo levelInfo = gson.fromJson(object.payload(), LevelInfo.class);
                    levelInfo.TimeSinceRecordingStart = fileVersion >= 1L ? object.timeOffset() : (levelInfo.UnixTimestamp - data.startTime);
                    deduplicateStrings(deduplicator, levelInfo);
                    data.levelInfos.add(levelInfo);
                }
                break;
                case 2: {
                    LiveData liveData = gson.fromJson(object.payload(), LiveData.class);
                    liveData.TimeSinceRecordingStart = fileVersion >= 1L ? object.timeOffset() : (liveData.UnixTimestamp - data.startTime);
                    deduplicateStrings(deduplicator, liveData);
                    data.liveData.add(liveData);
                }
                break;
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
