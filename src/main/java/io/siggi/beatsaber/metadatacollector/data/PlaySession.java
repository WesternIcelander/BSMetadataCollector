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
    /**
     * True if this play session was ended by restarting the level (e.g. from the pause menu),
     * in which case the next play session begins immediately after this one.
     */
    public boolean restarted;

    public PlaySession() {
    }

    public PlaySession(long realStartTime, long videoStartTime, long length, LevelInfo levelInfo, List<LiveData> liveData) {
        this(realStartTime, videoStartTime, length, levelInfo, liveData, false);
    }

    public PlaySession(long realStartTime, long videoStartTime, long length, LevelInfo levelInfo, List<LiveData> liveData, boolean restarted) {
        this.realStartTime = realStartTime;
        this.videoStartTime = videoStartTime;
        this.length = length;
        this.levelInfo = levelInfo;
        this.liveData = liveData;
        this.restarted = restarted;
    }
}
