package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;

import java.util.ArrayList;
import java.util.List;

public class CollectedMetadata {
    public long startTime;
    public final List<LevelInfo> levelInfos = new ArrayList<>();
    public final List<LiveData> liveData = new ArrayList<>();
}
