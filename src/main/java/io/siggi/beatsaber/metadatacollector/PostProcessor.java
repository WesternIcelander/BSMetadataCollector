package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.bsmdcstream.BSMDCInStream;
import io.siggi.beatsaber.metadatacollector.data.Chapter;
import io.siggi.beatsaber.metadatacollector.data.PlaySession;
import io.siggi.beatsaber.metadatacollector.data.datapuller.ELiveDataEventTriggers;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;
import io.siggi.beatsaber.metadatacollector.data.datapuller.Timestamped;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static io.siggi.beatsaber.metadatacollector.Util.gson;
import static io.siggi.beatsaber.metadatacollector.Util.timecode;

public class PostProcessor {

    public static void process(File file) throws IOException {
        file = file.getAbsoluteFile();
        File parent = file.getParentFile();
        String fileName = file.getName();
        int dotPos = fileName.lastIndexOf(".");
        String baseName = fileName.substring(0, dotPos);
        String extension = fileName.substring(dotPos + 1);
        CollectedMetadata data;
        if (extension.equalsIgnoreCase("bsmdc")) {
            try (BSMDCInStream in = new BSMDCInStream(new FileInputStream(file))) {
                data = CollectedMetadata.read(in);
            }
        } else if (extension.equalsIgnoreCase("json")) {
            try (FileReader in = new FileReader(file)) {
                data = gson.fromJson(in, CollectedMetadata.class);
            }
        } else {
            throw new IllegalArgumentException("Unsupported file extension: " + extension);
        }

        if (extension.equals("bsmdc")) {
            writeJson(data, new File(parent, baseName + ".json"));
        }
        writeChapters(data, new File(parent, baseName + "-chapters.txt"));
    }

    private static void writeJson(CollectedMetadata data, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeChapters(CollectedMetadata data, File file) throws IOException {
        List<PlaySession> playSessions = findPlaySessions(data);

        List<Chapter> chapters = generateChapters(playSessions);
        try (FileOutputStream out = new FileOutputStream(file)) {
            int i = 0;
            for (Chapter chapter : chapters) {
                String chapterId = "CHAPTER" + (i < 10 ? "0" : "") + i;
                String timeLine = chapterId + "=" + timecode(chapter.time);
                String nameLine = chapterId + "NAME=" + chapter.name;
                out.write((timeLine + "\n" + nameLine + "\n").getBytes(StandardCharsets.UTF_8));
                i++;
            }
        }
    }

    private static <T extends Timestamped> T findItemAfterTimestamp(List<T> items, long notBefore, Predicate<T> checker) {
        for (T t : items) {
            if (t.getTimeSinceRecordingStart() < notBefore) continue;
            if (!checker.test(t)) continue;
            return t;
        }
        return null;
    }

    private static <T extends Timestamped> T findItemBeforeTimestamp(List<T> items, long notAfter) {
        T prevItem = null;
        for (T t : items) {
            if (t.getTimeSinceRecordingStart() > notAfter) return prevItem;
            prevItem = t;
        }
        return prevItem;
    }

    private static <T extends Timestamped> List<T> getItemsInRange(List<T> items, long notBefore, long notAfter) {
        List<T> newList = new ArrayList<>();
        for (T t : items) {
            if (t.getTimeSinceRecordingStart() < notBefore) continue;
            if (t.getTimeSinceRecordingStart() > notAfter) break;
            newList.add(t);
        }
        return newList;
    }

    private static <T extends Timestamped> T findLastItemInPlaySession(List<T> items, long startingFrom, long gap) {
        T prevItem = null;
        long time = startingFrom;
        for (T t : items) {
            if (t.getTimeSinceRecordingStart() < startingFrom) continue;
            if (t.getTimeSinceRecordingStart() - time > gap) {
                return prevItem;
            }
            time = t.getTimeSinceRecordingStart();
            prevItem = t;
        }
        return prevItem;
    }

    /**
     * Find the first LiveData after the start of a play session that indicates a new attempt of a level has begun,
     * which happens when the level is restarted from the pause menu. A restart does not set any of the end flags in
     * LevelInfo, but DataPuller resets LiveData just like it does at the start of a level: the first LiveData of a new
     * attempt has an EventTrigger of Unknown, and its counters (TimeElapsed, NotesSpawned) go back to 0.
     */
    private static LiveData findLevelRestart(List<LiveData> items, long sessionStart, long notAfter) {
        LiveData prevItem = null;
        for (LiveData t : items) {
            if (t.TimeSinceRecordingStart <= sessionStart) continue;
            if (t.TimeSinceRecordingStart > notAfter) break;
            // the first LiveData of the play session is the marker for this session's own start
            if (prevItem != null && (t.EventTrigger == ELiveDataEventTriggers.Unknown
                    || t.TimeElapsed < prevItem.TimeElapsed
                    || t.NotesSpawned < prevItem.NotesSpawned)) {
                return t;
            }
            prevItem = t;
        }
        return null;
    }

    /**
     * Find when a restart happened. When the restart button is clicked, the LevelInfo goes from paused to unpaused
     * a few milliseconds before the LiveData restart marker is received, so use that LevelInfo if one is present.
     */
    private static long findRestartTime(List<LevelInfo> levelInfos, long sessionStart, long restartMarkerTime) {
        LevelInfo unpause = null;
        boolean paused = false;
        for (LevelInfo t : levelInfos) {
            if (t.TimeSinceRecordingStart <= sessionStart) continue;
            if (t.TimeSinceRecordingStart > restartMarkerTime) break;
            if (t.LevelPaused) {
                paused = true;
                unpause = null;
            } else if (paused && unpause == null) {
                unpause = t;
            }
        }
        return unpause != null ? unpause.TimeSinceRecordingStart : restartMarkerTime;
    }

    public static List<PlaySession> findPlaySessions(CollectedMetadata data) {
        List<PlaySession> playSessions = new ArrayList<>();
        long currentTime = 0L;
        if (!data.liveData.isEmpty()) {
            currentTime = Math.min(currentTime, data.liveData.get(0).TimeSinceRecordingStart);
        }
        if (!data.levelInfos.isEmpty()) {
            currentTime = Math.min(currentTime, data.levelInfos.get(0).TimeSinceRecordingStart);
        }
        currentTime -= 1L;
        while (true) {
            try {
                LevelInfo firstItem = findItemAfterTimestamp(
                        data.levelInfos,
                        currentTime,
                        item -> !item.LevelPaused && !item.LevelFinished && !item.LevelFailed && !item.LevelQuit && item.SongName != null && !item.SongName.isEmpty()
                );
                if (firstItem == null) break;
                currentTime = firstItem.TimeSinceRecordingStart;
                LevelInfo lastLevelInfo = findItemAfterTimestamp(
                        data.levelInfos,
                        currentTime,
                        item -> item.LevelFinished || (item.LevelFailed && !item.Modifiers.NoFailOn0Energy) || item.LevelQuit
                        || (item.SongName == null || item.SongName.isEmpty()) // null or empty may mean the game crashed and restarted
                );
                long songEnd;
                if (lastLevelInfo == null) {
                    LiveData lastLiveData = findLastItemInPlaySession(data.liveData, currentTime, 2500L);
                    songEnd = lastLiveData.TimeSinceRecordingStart;
                } else if (lastLevelInfo.SongName == null || lastLevelInfo.SongName.isEmpty()) {
                    // likely game crashed and restarted
                    LiveData lastLiveData = findItemBeforeTimestamp(data.liveData, lastLevelInfo.TimeSinceRecordingStart);
                    songEnd = lastLiveData.TimeSinceRecordingStart;
                    lastLevelInfo = findItemBeforeTimestamp(data.levelInfos, lastLevelInfo.TimeSinceRecordingStart);
                } else {
                    songEnd = lastLevelInfo.TimeSinceRecordingStart;
                }
                boolean restarted = false;
                LiveData restartMarker = findLevelRestart(data.liveData, currentTime, songEnd);
                if (restartMarker != null) {
                    restarted = true;
                    long restartTime = findRestartTime(data.levelInfos, currentTime, restartMarker.TimeSinceRecordingStart);
                    songEnd = restartTime - 1L;
                    lastLevelInfo = findItemBeforeTimestamp(data.levelInfos, songEnd);
                }
                List<LiveData> liveData = getItemsInRange(data.liveData, currentTime, songEnd);
                LevelInfo levelInfo = lastLevelInfo != null ? lastLevelInfo : findItemBeforeTimestamp(data.levelInfos, songEnd);
                playSessions.add(new PlaySession(
                        firstItem.UnixTimestamp,
                        firstItem.TimeSinceRecordingStart,
                        songEnd - firstItem.TimeSinceRecordingStart,
                        levelInfo,
                        liveData,
                        restarted
                ));
                currentTime = songEnd;
            } catch (Exception e) {
                break;
            }
        }
        return playSessions;
    }

    public static List<Chapter> generateChapters(List<PlaySession> playSessions) {
        ArrayList<Chapter> chapters = new ArrayList<>((playSessions.size() * 2) + 1);
        chapters.add(new Chapter("Pre-game", 0L));
        int intermissionCount = 0;
        for (PlaySession session : playSessions) {
            LevelInfo levelInfo = session.levelInfo;
            LiveData lastLiveData = session.liveData.get(session.liveData.size() - 1);
            StringBuilder chapterName = new StringBuilder();
            if (session.restarted) {
                chapterName.append("(restart) ");
            } else if (levelInfo.LevelFailed) {
                chapterName.append("(fail) ");
            } else if (levelInfo.LevelQuit) {
                chapterName.append("(quit) ");
            }
            chapterName.append(levelInfo.SongName)
                    .append(" - ").append(levelInfo.SongAuthor)
                    .append(" [").append(levelInfo.Mapper)
                    .append("]");
            if (levelInfo.BSRKey != null) {
                chapterName.append(" - ").append(levelInfo.BSRKey);
            }
            chapterName.append(" - ");
            if (levelInfo.CustomDifficultyLabel != null && !levelInfo.CustomDifficultyLabel.isEmpty()) {
                chapterName.append(levelInfo.CustomDifficultyLabel);
            } else {
                chapterName.append(levelInfo.Difficulty);
            }
            chapterName.append(" ");
            chapterName.append(levelInfo.MapType);
            chapterName.append(" - ");
            chapterName.append(lastLiveData.Rank);
            chapterName.append(" ");
            chapterName.append(Math.round(lastLiveData.Accuracy * 100.0) / 100.0);
            chapterName.append("% ");
            chapterName.append(lastLiveData.Score);
            long chapterTime = session.videoStartTime;
            if (chapterTime <= 0L) {
                intermissionCount = 0;
                chapters.clear();
                chapterTime = 0L;
            }
            chapters.add(new Chapter(
                    chapterName.toString(),
                    chapterTime
            ));
            // a restarted level is immediately followed by the next attempt, so there's no intermission
            if (!session.restarted) {
                intermissionCount += 1;
                chapters.add(new Chapter("Intermission " + intermissionCount, session.videoStartTime + session.length));
            }
        }
        return chapters;
    }
}
