package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.data.datapuller.LevelInfo;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;
import io.siggi.beatsaber.metadatacollector.socket.DPLevelInfoSocket;
import io.siggi.beatsaber.metadatacollector.socket.DPLiveDataSocket;
import io.siggi.beatsaber.metadatacollector.socket.OBSSocket;
import io.siggi.beatsaber.metadatacollector.ui.BSMetadataCollectorUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static io.siggi.beatsaber.metadatacollector.Util.gson;
import static io.siggi.beatsaber.metadatacollector.Util.writeInt;
import static io.siggi.beatsaber.metadatacollector.Util.writeLong;
import static io.siggi.beatsaber.metadatacollector.Util.writeString;

public class MetadataCollector implements DPLevelInfoSocket.Listener, DPLiveDataSocket.Listener, OBSSocket.Listener {

    private static final byte[] BSMDC_HEADER = "BSMDC\n".getBytes(StandardCharsets.UTF_8);
    private static final int FILE_VERSION = 0;

    private final Config config;
    private final OBSSocket obsSocket;
    private final DPLevelInfoSocket levelInfoSocket;
    private final DPLiveDataSocket liveDataSocket;

    private boolean started = false;
    private boolean stopped = false;

    private final Object lock = new Object();
    private FileOutputStream out;
    private boolean gameplayWasDetected = false;
    private File currentOutput;

    private final BSMetadataCollectorUI ui = new BSMetadataCollectorUI();
    private final JFrame frame;

    public MetadataCollector(Config config) throws URISyntaxException {
        this.config = config;
        this.obsSocket = new OBSSocket(new URI(config.obsEndpoint + "/"), config.obsPassword, this);
        this.levelInfoSocket = new DPLevelInfoSocket(new URI(config.dataPullerEndpoint + "/BSDataPuller/MapData"), this);
        this.liveDataSocket = new DPLiveDataSocket(new URI(config.dataPullerEndpoint + "/BSDataPuller/LiveData"), this);

        ui.statusTextLabel.setText("Not Recording");
        ui.songTextLabel.setText("-");
        frame = new JFrame("BSMetadataCollector");
        frame.setContentPane(ui.panel);
        frame.pack();
        Dimension size = frame.getSize();
        size.width = 400;
        frame.setSize(size);
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    public void start() {
        if (started) return;
        started = true;
        obsSocket.start();
        levelInfoSocket.start();
        liveDataSocket.start();
    }

    public void stop() {
        if (!started || stopped) return;
        stopped = true;
        obsSocket.stop();
        levelInfoSocket.stop();
        liveDataSocket.stop();
        synchronized (lock) {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
                out = null;
            }
        }
    }

    private boolean warnedRecording = false;
    private LevelInfo lastLevelInfo = null;

    private boolean matches(LevelInfo a, LevelInfo b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return Objects.equals(a.SongName, b.SongName)
                && Objects.equals(a.SongAuthor, b.SongAuthor)
                && Objects.equals(a.SongSubName, b.SongSubName)
                && Objects.equals(a.Mapper, b.Mapper)
                && Objects.equals(a.Hash, b.Hash);
    }

    @Override
    public void levelInfoReceived(LevelInfo levelInfo) {
        synchronized (lock) {
            if (out == null) {
                if (levelInfo.SongName != null && !levelInfo.SongName.isEmpty() && !warnedRecording) {
                    warnedRecording = true;
                    System.out.println("Warning: Received level start event from game, but no recording start event from OBS!");
                    System.out.println("You must start the OBS recording *after* starting BSMetadataCollector.");
                }
                return;
            }
            try {
                out.write(1);
                writeString(out, gson.toJson(levelInfo));
                if (!matches(lastLevelInfo, levelInfo) && !levelInfo.SongName.isEmpty()) {
                    gameplayWasDetected = true;
                    lastLevelInfo = levelInfo;
                    System.out.println("Now playing: " + levelInfo.SongName + " - " + levelInfo.SongAuthor + " [" + levelInfo.Mapper + "]");
                    ui.statusTextLabel.setText("Playing");
                    ui.songTextLabel.setText(levelInfo.SongName + " - " + levelInfo.SongAuthor + " [" + levelInfo.Mapper + "]");
                } else if (levelInfo.LevelFailed && !levelInfo.Modifiers.NoFailOn0Energy) {
                    ui.statusTextLabel.setText("Level Failed");
                } else if (levelInfo.LevelQuit) {
                    ui.statusTextLabel.setText("Level Quit");
                } else if (levelInfo.LevelFinished) {
                    ui.statusTextLabel.setText("Level Finished");
                }
            } catch (Exception e) {
                e.printStackTrace();
                closeOut();
            }
        }
    }

    @Override
    public void liveDataReceived(LiveData liveData) {
        synchronized (lock) {
            if (out == null) return;
            try {
                out.write(2);
                writeString(out, gson.toJson(liveData));
            } catch (Exception e) {
                e.printStackTrace();
                closeOut();
            }
        }
    }

    @Override
    public void recordingStarted(String filepath) {
        synchronized (lock) {
            if (out != null) return;
            String fileName = filepath.replace("\\", "/");
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            String baseName = fileName.substring(0, fileName.lastIndexOf("."));
            File target = null;
            try {
                File f = new File(filepath).getAbsoluteFile();
                File parent = f.getParentFile();
                if (parent.exists())
                    target = new File(parent, baseName + ".bsmdc");
            } catch (Exception ignored) {
            }
            if (target == null) {
                File dataDirectory = new File("data").getAbsoluteFile();
                if (!dataDirectory.exists()) dataDirectory.mkdirs();
                target = new File(dataDirectory, baseName + ".bsmdc");
            }
            this.currentOutput = target;
            try {
                out = new FileOutputStream(target);
                out.write(BSMDC_HEADER);
                writeInt(out, FILE_VERSION);
                writeLong(out, System.currentTimeMillis());
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("OBS recording started");
            ui.statusTextLabel.setText("Recording");
        }
    }

    @Override
    public void recordingStopped(String filepath) {
        synchronized (lock) {
            if (out == null) return;
            try {
                out.close();
            } catch (Exception ignored) {
            }
            lastLevelInfo = null;
            System.out.println("OBS recording ended");
            ui.statusTextLabel.setText("Recording Stopped");
            ui.songTextLabel.setText("-");
            out = null;
            if (!gameplayWasDetected) {
                System.out.println("Deleting metadata file as no levels were played.");
                ui.statusTextLabel.setText("Recording Stopped, no levels were played");
                currentOutput.delete();
                return;
            }
            gameplayWasDetected = false;
            try {
                PostProcessor.process(currentOutput);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void closeOut() {
        try {
            if (out != null)
                out.close();
        } catch (Exception ignored) {
        }
        out = null;
    }
}
