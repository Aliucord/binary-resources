package com.google.devrel.gmscore.tools.apk.arsc;

import androidx.annotation.NonNull;

import java.nio.*;

/**
 * Auto-resizing byte buffer when writing.
 * Based on a gist <a href="https://gist.github.com/DudeMartin/5273469">under public domain</a>.
 */
@SuppressWarnings("unused")
public class GrowableByteBuffer {
    private ByteBuffer buffer;

    public GrowableByteBuffer(int capacity) {
        super();
        buffer = ByteBuffer.allocate(capacity);
    }

    private void ensureSpace(int needed) {
        float EXPAND_FACTOR = 1.5f;

        if (buffer.remaining() >= needed) {
            return;
        }

        int newCapacity = buffer.capacity();
        while (newCapacity < (buffer.capacity() + needed)) {
            newCapacity = (int) (newCapacity * EXPAND_FACTOR);
        }

        ByteBuffer expanded = ByteBuffer.allocate(newCapacity);
        expanded.order(buffer.order());
        expanded.position(buffer.position());

//        buffer.flip();
//        expanded.put(buffer);

        System.arraycopy(
                /* src = */ buffer.array(), 0,
                /* dst = */ expanded.array(), 0,
                /* length = */ buffer.array().length);

        buffer = expanded;
    }

    public GrowableByteBuffer order(ByteOrder order) {
        buffer.order(order);
        return this;
    }

    public byte get() {
        return buffer.get();
    }

    @NonNull
    public GrowableByteBuffer put(byte b) {
        ensureSpace(1);
        buffer.put(b);
        return this;
    }

    public byte get(int index) {
        return buffer.get(index);
    }

    @NonNull
    public GrowableByteBuffer put(int index, byte b) {
        ensureSpace(1);
        buffer.put(index, b);
        return this;
    }

    @NonNull
    public GrowableByteBuffer put(byte[] src) {
        ensureSpace(src.length);
        buffer.put(src);
        return this;
    }

    @NonNull
    public GrowableByteBuffer put(byte[] src, int offset, int length) {
        ensureSpace(length);
        buffer.put(src, offset, length);
        return this;
    }

    public char getChar() {
        return buffer.getChar();
    }

    @NonNull
    public GrowableByteBuffer putChar(char value) {
        ensureSpace(2);
        buffer.putChar(value);
        return this;
    }

    public char getChar(int index) {
        return buffer.getChar(index);
    }

    @NonNull
    public GrowableByteBuffer putChar(int index, char value) {
        ensureSpace(2);
        buffer.putChar(index, value);
        return this;
    }

    @NonNull
    public CharBuffer asCharBuffer() {
        return buffer.asCharBuffer();
    }

    public short getShort() {
        return buffer.getShort();
    }

    @NonNull
    public GrowableByteBuffer putShort(short value) {
        ensureSpace(2);
        buffer.putShort(value);
        return this;
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    @NonNull
    public GrowableByteBuffer putShort(int index, short value) {
        ensureSpace(2);
        buffer.putShort(index, value);
        return this;
    }

    @NonNull
    public ShortBuffer asShortBuffer() {
        return buffer.asShortBuffer();
    }

    public int getInt() {
        return buffer.getInt();
    }

    @NonNull
    public GrowableByteBuffer putInt(int value) {
        ensureSpace(4);
        buffer.putInt(value);
        return this;
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    @NonNull
    public GrowableByteBuffer putInt(int index, int value) {
        ensureSpace(4);
        buffer.putInt(index, value);
        return this;
    }

    @NonNull
    public IntBuffer asIntBuffer() {
        return buffer.asIntBuffer();
    }

    public long getLong() {
        return buffer.getLong();
    }

    @NonNull
    public GrowableByteBuffer putLong(long value) {
        ensureSpace(4);
        buffer.putLong(value);
        return this;
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    @NonNull
    public GrowableByteBuffer putLong(int index, long value) {
        ensureSpace(4);
        buffer.putLong(index, value);
        return this;
    }

    @NonNull
    public LongBuffer asLongBuffer() {
        return buffer.asLongBuffer();
    }

    public float getFloat() {
        return buffer.getFloat();
    }

    @NonNull
    public GrowableByteBuffer putFloat(float value) {
        ensureSpace(4);
        buffer.putFloat(value);
        return this;
    }

    public float getFloat(int index) {
        return buffer.getFloat(index);
    }

    @NonNull
    public GrowableByteBuffer putFloat(int index, float value) {
        ensureSpace(4);
        buffer.putFloat(index, value);
        return this;
    }

    @NonNull
    public FloatBuffer asFloatBuffer() {
        return buffer.asFloatBuffer();
    }

    public double getDouble() {
        return buffer.getDouble();
    }

    @NonNull
    public GrowableByteBuffer putDouble(double value) {
        ensureSpace(8);
        buffer.putDouble(value);
        return this;
    }

    public double getDouble(int index) {
        return buffer.getDouble(index);
    }

    @NonNull
    public GrowableByteBuffer putDouble(int index, double value) {
        ensureSpace(8);
        buffer.putDouble(index, value);
        return this;
    }

    @NonNull
    public DoubleBuffer asDoubleBuffer() {
        return buffer.asDoubleBuffer();
    }

    public int position() {
        return buffer.position();
    }

    public GrowableByteBuffer position(int newPosition) {
        buffer.position(newPosition);
        return this;
    }

    public byte[] array() {
        return buffer.array();
    }

    public int arrayOffset() {
        return buffer.arrayOffset();
    }
}
