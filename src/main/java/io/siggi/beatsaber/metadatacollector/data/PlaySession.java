package io.siggi.beatsaber.metadatacollector.data;

import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;

import java.util.List;

public class PlaySession {
    public long realStartTime;
    public long videoStartTime;
    public long length;
    public LevelInfo levelInfo;
    public List<LiveData> liveData;

    public PlaySession() {
    }

    public PlaySession(long realStartTime, long videoStartTime, long length, LevelInfo levelInfo, List<LiveData> liveData) {
        this.realStartTime = realStartTime;
        this.videoStartTime = videoStartTime;
        this.length = length;
        this.levelInfo = levelInfo;
        this.liveData = liveData;
    }
}
