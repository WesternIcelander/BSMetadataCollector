package io.siggi.beatsaber.metadatacollector.bsmdcstream;

import java.nio.charset.StandardCharsets;

class Constants {
    static final byte[] BSMDC_HEADER = "BSMDC\n".getBytes(StandardCharsets.UTF_8);
    static final int MAX_SUPPORTED_VERSION = 1;
}
