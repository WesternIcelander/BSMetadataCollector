package io.siggi.beatsaber.metadatacollector;

import io.siggi.beatsaber.metadatacollector.bsmdcstream.BSMDCInStream;
import io.siggi.beatsaber.metadatacollector.data.datapuller.LiveData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static io.siggi.beatsaber.metadatacollector.Util.gson;
import static io.siggi.beatsaber.metadatacollector.Util.gsonPretty;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0) {
            boolean printScore = false;
            for (String arg : args) {
                if (arg.equals("-p")) {
                    printScore = true;
                    continue;
                }
                try {
                    if (printScore) {
                        CollectedMetadata data;
                        try (BSMDCInStream in = new BSMDCInStream(new FileInputStream(arg))) {
                            data = CollectedMetadata.read(in);
                        }
                        int lastScore = 0;
                        for (LiveData data1 : data.liveData) {
                            if (lastScore == data1.Score) continue;
                            lastScore = data1.Score;
                            System.out.println((data1.TimeSinceRecordingStart / 1000L) + ": " + data1.Score);
                        }
                    } else {
                        PostProcessor.process(new File(arg));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        File configFile = new File("config.json");
        if (!configFile.exists()) {
            try (FileOutputStream out = new FileOutputStream(configFile)) {
                out.write(gsonPretty.toJson(new Config()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("A new config file has been created at:");
            System.out.println("    " + configFile.getAbsolutePath());
            System.out.println();
            System.out.println("Add your OBS websocket password to the file.");
            System.out.println("Don't touch the other settings unless you know what you're doing.");
            System.out.println();
            System.out.println("After you edit the file, then run MetadataCollector again.");
            System.out.println();
            return;
        }
        Config config;
        try (FileReader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, Config.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        while (config.dataPullerEndpoint.endsWith("/"))
            config.obsEndpoint = config.obsEndpoint.substring(0, config.obsEndpoint.length() - 1);
        while (config.dataPullerEndpoint.endsWith("/"))
            config.dataPullerEndpoint = config.dataPullerEndpoint.substring(0, config.dataPullerEndpoint.length() - 1);

        MetadataCollector metadataCollector;
        try {
            metadataCollector = new MetadataCollector(config);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        metadataCollector.start();
    }
}
