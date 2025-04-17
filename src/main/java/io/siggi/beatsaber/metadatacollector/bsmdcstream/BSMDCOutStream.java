package io.siggi.beatsaber.metadatacollector.bsmdcstream;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

import static io.siggi.beatsaber.metadatacollector.Util.writeInt;
import static io.siggi.beatsaber.metadatacollector.Util.writeLong;
import static io.siggi.beatsaber.metadatacollector.Util.writeString;

public class BSMDCOutStream implements Closeable {

    private final OutputStream out;
    private final int fileVersion;
    private final long startTime;

    public BSMDCOutStream(OutputStream out, long startTime) throws IOException {
        this(out, startTime, Constants.MAX_SUPPORTED_VERSION);
    }

    public BSMDCOutStream(OutputStream out, long startTime, int fileVersion) throws IOException {
        this.out = out;
        this.startTime = startTime;
        this.fileVersion = fileVersion;

        out.write(Constants.BSMDC_HEADER);
        writeInt(out, fileVersion);
        writeLong(out, startTime);
    }

    public void write(BSMDCObject object) throws IOException {
        out.write(object.type());
        if (fileVersion >= 1) writeLong(out, object.timeOffset());
        writeString(out, object.payload());
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
