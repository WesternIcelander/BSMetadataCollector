package io.siggi.beatsaber.metadatacollector.bsmdcstream;

public class BSMDCObject {
    public BSMDCObject(int type, String payload, long timeOffset) {
        this.type = type;
        this.payload = payload;
        this.timeOffset = timeOffset;
    }

    private final int type;
    private final String payload;
    private final long timeOffset;

    public int type() {
        return type;
    }

    public String payload() {
        return payload;
    }

    public long timeOffset() {
        return timeOffset;
    }

    public BSMDCObject setTimeOffset(long timeOffset) {
        return new BSMDCObject(type, payload, timeOffset);
    }

    public BSMDCObject adjustTimeOffset(long adjustment) {
        return setTimeOffset(timeOffset + adjustment);
    }
}
