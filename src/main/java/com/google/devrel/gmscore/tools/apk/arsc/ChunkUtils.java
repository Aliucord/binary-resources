package com.google.devrel.gmscore.tools.apk.arsc;

public class ChunkUtils {
    /**
     * The byte boundary to pad chunks on.
     */
    public static final int PAD_BOUNDARY = 4;

    /**
     * Pads {@code buffer} until {@code currentLength} is on a 4-byte boundary.
     *
     * @param buffer The writable {@link GrowableByteBuffer} that will be padded.
     * @param currentLength The current length, in bytes, to pad based on. This usually is the size of the buffer.
     * @return The new currentLength.
     */
    public static int writePad(GrowableByteBuffer buffer, int currentLength) {
        while (currentLength % PAD_BOUNDARY != 0) {
            buffer.put((byte) 0);
            ++currentLength;
        }
        return currentLength;
    }
}
