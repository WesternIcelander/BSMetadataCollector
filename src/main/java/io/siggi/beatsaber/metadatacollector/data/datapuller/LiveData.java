package io.siggi.beatsaber.metadatacollector.data.datapuller;

public class LiveData implements Timestamped {
    public int Score;
    public int ScoreWithMultipliers;

    public int MaxScore;
    public int MaxScoreWithMultipliers;

    public String Rank;
    public boolean FullCombo;
    public int NotesSpawned;
    public int Combo;
    public int Misses;
    public double Accuracy;
    public SBlockHitScore BlockHitScore;
    public double PlayerHealth;
    public ColorType ColorType;
    public int TimeElapsed;
    public ELiveDataEventTriggers EventTrigger;

    public long UnixTimestamp;
    public long TimeSinceRecordingStart;

    public long getUnixTimestamp() {
        return UnixTimestamp;
    }
    public long getTimeSinceRecordingStart() {
        return TimeSinceRecordingStart;
    }
}
