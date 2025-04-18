package io.siggi.beatsaber.metadatacollector.bsmdcstream;

import io.siggi.tools.io.ConcatenatedInputStream;
import io.siggi.tools.io.IO;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import static io.siggi.beatsaber.metadatacollector.Util.readInt;
import static io.siggi.beatsaber.metadatacollector.Util.readLong;
import static io.siggi.beatsaber.metadatacollector.Util.readString;

public class BSMDCInStream implements Closeable {

    private final InputStream in;
    private final int fileVersion;
    private final long startTime;

    public BSMDCInStream(InputStream in) throws IOException {
        byte[] header = new byte[Constants.BSMDC_HEADER.length];
        IO.readFully(in, header);
        headerCheck:
        if (!Arrays.equals(header, Constants.BSMDC_HEADER)) {
            if (header[0] == (byte) 0x1f && header[1] == (byte) 0x8b) {
                in = new GZIPInputStream(new ConcatenatedInputStream(Arrays.asList(new ByteArrayInputStream(header), in).iterator()));
                header = new byte[Constants.BSMDC_HEADER.length];
                IO.readFully(in, header);
                if (Arrays.equals(header, Constants.BSMDC_HEADER)) {
                    break headerCheck;
                }
            }
            throw new IOException("Not BSMDC file!");
        }
        fileVersion = readInt(in);
        if (fileVersion > Constants.MAX_SUPPORTED_VERSION) {
            throw new IOException("BSMDC file created by a newer version not supported.");
        }
        startTime = readLong(in);
        this.in = in;
    }

    public int fileVersion() {
        return fileVersion;
    }

    public long startTime() {
        return startTime;
    }

    public BSMDCObject read() throws IOException {
        int type = in.read();
        if (type == -1) return null;
        long time = fileVersion >= 1 ? readLong(in) : 0L;
        String payload = readString(in);
        return new BSMDCObject(type, payload, time);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
