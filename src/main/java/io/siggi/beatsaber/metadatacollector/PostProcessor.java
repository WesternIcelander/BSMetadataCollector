package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.data.Chapter;
import io.siggi.beatsaber.metadatacollector.data.PlaySession;
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
        String fileName = file.getName();
        int dotPos = fileName.lastIndexOf(".");
        String extension = fileName.substring(dotPos + 1);
        if (extension.equalsIgnoreCase("bsmdc")) {
            processBsmdc(file);
        } else if (extension.equalsIgnoreCase("json")) {
            processJson(file);
        }
    }

    private static void processBsmdc(File file) throws IOException {
        String fileName = file.getName();
        int dotPos = fileName.lastIndexOf(".");
        String baseName = fileName.substring(0, dotPos);

        File json = new File(file.getParentFile(), baseName + ".json");

        CollectedMetadata data;
        try (FileInputStream in = new FileInputStream(file)) {
            data = CollectedMetadata.read(in);
        }

        try (FileOutputStream out = new FileOutputStream(json)) {
            out.write(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
        }

        processJson(json);
    }

    private static void processJson(File file) throws IOException {
        String fileName = file.getName();
        int dotPos = fileName.lastIndexOf(".");
        String baseName = fileName.substring(0, dotPos);

        File parent = file.getParentFile();

        CollectedMetadata data;
        try (FileReader in = new FileReader(file)) {
            data = gson.fromJson(in, CollectedMetadata.class);
        }

        List<PlaySession> playSessions = findPlaySessions(data);

        List<Chapter> chapters = generateChapters(playSessions);
        try (FileOutputStream out = new FileOutputStream(new File(parent, baseName + "-chapters.txt"))) {
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

    private static List<PlaySession> findPlaySessions(CollectedMetadata data) {
        List<PlaySession> playSessions = new ArrayList<>();
        long currentTime = 0L;
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
                List<LiveData> liveData = getItemsInRange(data.liveData, currentTime, songEnd);
                LevelInfo levelInfo = lastLevelInfo != null ? lastLevelInfo : findItemBeforeTimestamp(data.levelInfos, songEnd);
                playSessions.add(new PlaySession(
                        firstItem.UnixTimestamp,
                        firstItem.TimeSinceRecordingStart,
                        songEnd - firstItem.TimeSinceRecordingStart,
                        levelInfo,
                        liveData
                ));
                currentTime = songEnd;
            } catch (Exception e) {
                break;
            }
        }
        return playSessions;
    }

    private static List<Chapter> generateChapters(List<PlaySession> playSessions) {
        ArrayList<Chapter> chapters = new ArrayList<>((playSessions.size() * 2) + 1);
        chapters.add(new Chapter("Pre-game", 0L));
        int intermissionCount = 0;
        for (PlaySession session : playSessions) {
            LevelInfo levelInfo = session.levelInfo;
            LiveData lastLiveData = session.liveData.get(session.liveData.size() - 1);
            StringBuilder chapterName = new StringBuilder();
            if (levelInfo.LevelFailed) {
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
            chapters.add(new Chapter(
                    chapterName.toString(),
                    session.videoStartTime
            ));
            intermissionCount += 1;
            chapters.add(new Chapter("Intermission " + intermissionCount, session.videoStartTime + session.length));
        }
        return chapters;
    }
}
