package io.siggi.beatsaber.metadatacollector.data.datapuller;

public class LevelInfo implements Timestamped {
    public boolean LevelPaused;
    public boolean LevelFinished;
    public boolean LevelFailed;
    public boolean LevelQuit;

    public String Hash;
    public String SongName;
    public String SongSubName;
    public String SongAuthor;
    public String Mapper;
    public String BSRKey;
    public String CoverImage;
    public int Duration;

    public String MapType;
    public String Difficulty;
    public String CustomDifficultyLabel;
    public int BPM;
    public double NJS;

    public Modifiers Modifiers;
    public float ModifiersMultiplier;

    public boolean PracticeMode;
    public PracticeModeModifiers PracticeModeModifiers;

    public double PP;
    public double Star;

    public String GameVersion;
    public String PluginVersion;
    public boolean IsMultiplayer;

    public int PreviousRecord;
    public String PreviousBSR;

    public long UnixTimestamp;
    public long TimeSinceRecordingStart;

    public long getUnixTimestamp() {
        return UnixTimestamp;
    }
    public long getTimeSinceRecordingStart() {
        return TimeSinceRecordingStart;
    }
}
