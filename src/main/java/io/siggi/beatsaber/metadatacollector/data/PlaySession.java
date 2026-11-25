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
    /**
     * True if the level was failed. This may be true even if levelInfo.LevelFailed is false, as DataPuller doesn't
     * always report a fail, in which case it is determined from the player's health.
     */
    public boolean failed;

    public PlaySession() {
    }

    public PlaySession(long realStartTime, long videoStartTime, long length, LevelInfo levelInfo, List<LiveData> liveData) {
        this(realStartTime, videoStartTime, length, levelInfo, liveData, false, levelInfo != null && levelInfo.LevelFailed);
    }

    public PlaySession(long realStartTime, long videoStartTime, long length, LevelInfo levelInfo, List<LiveData> liveData, boolean restarted, boolean failed) {
        this.realStartTime = realStartTime;
        this.videoStartTime = videoStartTime;
        this.length = length;
        this.levelInfo = levelInfo;
        this.liveData = liveData;
        this.restarted = restarted;
        this.failed = failed;
    }
}
