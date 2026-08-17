package org.telegram.messenger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** PrimeGram E2E: tiny length-prefixed byte-array read/write helpers shared by
 *  {@link PrimeE2EIdentity} and {@link PrimeE2EVault}'s binary serialization - both
 *  seal fixed-shape internal state (never a cross-version wire format, nothing else
 *  ever reads these bytes), so a hand-rolled fixed layout is enough; no need for
 *  JSON/Protobuf here. */
final class PrimeE2EIo {

    private static final int MAX_FIELD_BYTES = 8192;

    private PrimeE2EIo() {
    }

    static byte[] readBytes(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > MAX_FIELD_BYTES) {
            throw new IOException("PrimeE2E: bad length prefix " + len);
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    static void writeBytes(DataOutputStream out, byte[] data) throws IOException {
        if (data == null) {
            out.writeInt(0);
            return;
        }
        out.writeInt(data.length);
        out.write(data);
    }
}
