/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devrel.gmscore.tools.apk.arsc;

import android.os.Build;

import androidx.collection.*;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a string pool structure.
 * <p>
 * This does not read all the strings at initialization. Instead,
 * only the string offsets are read and if {@link StringPoolChunk#getString(int)}
 * is called only then is it decoded into a {@link String}. Otherwise, the bytes will
 * be directly copied back when this chunk is written.
 */
public final class StringPoolChunk extends Chunk {

    // These are the defined flags for the "flags" field of ResourceStringPoolHeader
    private static final int SORTED_FLAG = 1 << 0;
    private static final int UTF8_FLAG = 1 << 8;

    /**
     * The offset from the start of the header that the stylesStart field is at.
     */
    private static final int STYLE_START_OFFSET = 24;

    /**
     * Flags.
     */
    private final int flags;

    /**
     * Index from header of the string data.
     */
    private final int stringsStart;

    /**
     * Index from header of the style data.
     */
    private final int stylesStart;

    /**
     * Number of strings in the original buffer. This is not necessarily the number of strings
     * returned by {@link StringPoolChunk#getStringCount()}.
     */
    private final int stringCount;

    /**
     * Number of styles in the original buffer. This is not necessarily the number of styles in
     * {@code styles}.
     */
    private final int styleCount;

    /**
     * Extra strings added on after {@link StringPoolChunk#stringOffsets}, in a sequential order.
     */
    private final MutableObjectList<String> newStrings = new MutableObjectList<>();

    /**
     * These styles have a 1:1 relationship with the strings. For example, styles.get(3) refers to
     * the string at location {@code getString(3)} There are never more styles than strings (though there
     * may be less). Inside of that are all of the styles referenced by that string.
     */
    private final MutableObjectList<StringPoolStyle> styles = new MutableObjectList<>();

    /**
     * The strings offsets ordered as they appear in the arsc file, referencing the
     * data in the {@link StringPoolChunk#srcBuffer}.
     * e.g. strings.get(1234) gets the 1235th
     */
    private int[] stringOffsets;

    /**
     * The original source buffer used to lazy load strings as
     * defined by {@link StringPoolChunk#stringOffsets}.
     */
    private ByteBuffer srcBuffer;
    /**
     * The chunk offset populated when writing this chunk.
     */
    private int writtenChunkOffset;

    StringPoolChunk(ByteBuffer buffer, @Nullable Chunk parent) {
        super(buffer, parent);
        stringCount = buffer.getInt();
        styleCount = buffer.getInt();
        flags = buffer.getInt();
        stringsStart = buffer.getInt();
        stylesStart = buffer.getInt();
    }

    @Override
    protected void init(ByteBuffer buffer) {
        if (srcBuffer != null) {
            throw new IllegalStateException("StringPoolChunk already initialized!");
        }

        super.init(buffer);

        srcBuffer = buffer;
        stringOffsets = readStringOffsets(buffer, getOriginalOffset() + stringsStart, stringCount);
        styles.addAll(readStyles(buffer, getOriginalOffset() + stylesStart, styleCount));
    }

    /**
     * Returns the 0-based index of the first occurrence of the given string, or -1 if the string is
     * not in the pool. This runs in O(n) time.
     *
     * @param string The string to check the pool for.
     * @return Index of the string, or -1 if not found.
     */
    public int indexOf(String string) {
        byte[] bytes = srcBuffer.array();
        byte[] encodedString = BinaryResourceString.encodeString(string, getStringType());

        for (int i = 0; i < stringOffsets.length; i++) {
            if (bytes.length < stringOffsets[i] + encodedString.length) continue;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // noinspection Since15
                if (Arrays.equals(
                        bytes, stringOffsets[i], stringOffsets[i] + encodedString.length,
                        encodedString, 0, encodedString.length)) {
                    return i;
                }
            } else {
                if (ByteBuffer.wrap(bytes, stringOffsets[i], encodedString.length)
                        .equals(ByteBuffer.wrap(encodedString))) {
                    return i;
                }
            }
        }

        int newStringsIdx = newStrings.indexOf(string);
        if (newStringsIdx >= 0) {
            return newStringsIdx + stringOffsets.length;
        }

        return -1;
    }

    /**
     * Returns a string at the given (0-based) index.
     *
     * @param index The (0-based) index of the string to return.
     * @throws IndexOutOfBoundsException If the index is out of range (index < 0 || index >= size()).
     */
    public String getString(int index) {
        if (index < stringOffsets.length) {
            return BinaryResourceString.decodeString(srcBuffer, stringOffsets[index], getStringType());
        } else {
            return newStrings.get(index - stringOffsets.length);
        }
    }

    /**
     * Adds a string to the unwritten strings list for later.
     *
     * @param string The new string to add to the end of the pool. Note that when this pool is
     * written, this string will not be deduped if it already exists in the pool.
     * @return The index of the string in the pool.
     */
    public int addString(String string) {
        return addString(string, false);
    }

    /**
     * Adds a string to the unwritten strings list for later.
     *
     * @param string The new string to add to the end of the pool.
     * @param deduplicate If true, then an equal existing string will be searched for in the pool.
     * if found, then the existing string index will be returned and no action
     * will occur.
     * @return The index of the string in the pool.
     */
    public int addString(String string, boolean deduplicate) {
        int existingIndex;
        if (deduplicate && (existingIndex = indexOf(string)) >= 0) {
            return existingIndex;
        } else {
            newStrings.add(string);
            return stringOffsets.length + newStrings.getSize() - 1;
        }
    }

    /**
     * Returns the number of strings in this pool.
     */
    public int getStringCount() {
        return stringOffsets.length + newStrings.getSize();
    }

    /**
     * Returns a style at the given (0-based) index.
     *
     * @param index The (0-based) index of the style to return.
     * @throws IndexOutOfBoundsException If the index is out of range (index < 0 || index >= size()).
     */
    public StringPoolStyle getStyle(int index) {
        return styles.get(index);
    }

    /**
     * Returns the number of styles in this pool.
     */
    public int getStyleCount() {
        return styles.getSize();
    }

    /**
     * Returns the type of strings in this pool.
     */
    public BinaryResourceString.Type getStringType() {
        return isUTF8() ? BinaryResourceString.Type.UTF8 : BinaryResourceString.Type.UTF16;
    }

    @Override
    protected Type getType() {
        return Chunk.Type.STRING_POOL;
    }

    /**
     * Returns the number of bytes needed for offsets based on {@code strings} and {@code styles}.
     */
    private int getOffsetSize() {
        return (getStringCount() + styles.getSize()) * 4;
    }

    /**
     * True if this string pool contains strings in UTF-8 format. Otherwise, strings are in UTF-16.
     *
     * @return true if @{code strings} are in UTF-8; false if they're in UTF-16.
     */
    public boolean isUTF8() {
        return (flags & UTF8_FLAG) != 0;
    }

    /**
     * True if this string pool contains already-sorted strings.
     *
     * @return true if @{code strings} are sorted.
     */
    public boolean isSorted() {
        return (flags & SORTED_FLAG) != 0;
    }

    private int[] readStringOffsets(ByteBuffer buffer, int offset, int count) {
        int[] stringOffsets = new int[count];

        // After the header, we now have an array of offsets for the strings in this pool.
        for (int i = 0; i < count; ++i) {
            stringOffsets[i] = offset + buffer.getInt();
        }

        return stringOffsets;
    }

    private ObjectList<StringPoolStyle> readStyles(ByteBuffer buffer, int offset, int count) {
        MutableObjectList<StringPoolStyle> styles = new MutableObjectList<>(count);

        // After the array of offsets for the strings in the pool, we have an offset for the styles in this pool.
        for (int i = 0; i < count; ++i) {
            int styleOffset = offset + buffer.getInt();
            styles.add(StringPoolStyle.create(buffer, styleOffset, this));
        }

        return styles;
    }

    /**
     * Write 0s a specified amount of times as placeholder offsets to be filled in later.
     */
    private void writePlaceholderOffsets(GrowableByteBuffer buffer, int count) {
        for (int i = 0; i < count; i++) {
            buffer.putInt(0); // Will be filled in later
        }
    }

    /**
     * Writes the string data and fills in the offset placeholders.
     * @param buffer The buffer positioned to where the string data should be written.
     * @param stringOffsetsStart Position in the buffer where the placeholder string offsets start
     */
    private void writeStrings(GrowableByteBuffer buffer, int stringOffsetsStart) {
        int currentOffset = 0;
        int stringIdx = 0;

        // existing string offset -> new string offset
        MutableIntIntMap used = new MutableIntIntMap(getStringCount());

        for (int stringOffset : stringOffsets) {
            int existingOffset;
            if ((existingOffset = used.getOrDefault(stringOffset, -1)) >= 0) {
                buffer.putInt(stringOffsetsStart + (stringIdx++ * 4), existingOffset);
            } else {
                used.put(stringOffset, currentOffset);

                int stringLength = BinaryResourceString.decodeFullLength(srcBuffer, stringOffset, getStringType());

                buffer.putInt(stringOffsetsStart + (stringIdx++ * 4), currentOffset);
                buffer.put(srcBuffer.array(), stringOffset, stringLength);
                currentOffset += stringLength;
            }
        }

        // New strings aren't deduped since it's unlikely someone would manually add duplicated strings
        for (int i = 0; i < newStrings.getSize(); i++) {
            String string = newStrings.get(i);
            byte[] encodedString = BinaryResourceString.encodeString(string, getStringType());

            buffer.putInt(stringOffsetsStart + (stringIdx++ * 4), currentOffset);
            buffer.put(encodedString);
            currentOffset += encodedString.length;
        }

        // ARSC files pad to a 4-byte boundary. We should do so too.
        ChunkUtils.writePad(buffer, currentOffset);
    }

    /**
     * Writes the styles data and fills in the offset placeholders.
     * @param buffer The buffer positioned to where the styles data should be written.
     * @param styleOffsetsStart Position in the buffer where the placeholder style offsets start
     */
    private void writeStyles(GrowableByteBuffer buffer, int styleOffsetsStart) {
        if (styles.isEmpty()) return;

        int styleOffset = 0;
        int styleIdx = 0;

        // Keeps track of bytes already written
        MutableObjectIntMap<StringPoolStyle> used = new MutableObjectIntMap<>();

        for (int i = 0; i < styles.getSize(); i++) {
            StringPoolStyle style = styles.get(i);
            int existingOffset;

            if ((existingOffset = used.getOrDefault(style, -1)) >= 0) {
                buffer.putInt(styleOffsetsStart + (styleIdx++ * 4), existingOffset);
            } else {
                used.put(style, styleOffset);

                int start = buffer.position();
                style.writeTo(buffer);
                int styleSize = buffer.position() - start;

                buffer.putInt(styleOffsetsStart + (styleIdx++ * 4), styleOffset);

                styleOffset += styleSize;
            }
        }

        // This is such a cursed format, 2 terminators??
        for (int i = 0; i < 2; i++) {
            // The end of the spans are terminated with another sentinel value
            buffer.putInt(StringPoolStyle.RES_STRING_POOL_SPAN_END);
            styleOffset += 4;
        }

        ChunkUtils.writePad(buffer, styleOffset);
    }

    @Override
    protected void writeHeader(GrowableByteBuffer buffer) {
        writtenChunkOffset = buffer.position();
        super.writeHeader(buffer);
        int stringsStart = getOriginalHeaderSize() + getOffsetSize();
        buffer.putInt(getStringCount());
        buffer.putInt(styles.getSize());
        buffer.putInt(flags);
        buffer.putInt(getStringCount() == 0 ? 0 : stringsStart);
        buffer.putInt(0);  // Placeholder. The styles starting offset cannot be computed at this point.
    }

    @Override
    protected void writePayload(GrowableByteBuffer buffer) {
        int stringOffsetsStart = buffer.position();
        writePlaceholderOffsets(buffer, getStringCount());

        int styleOffsetsStart = buffer.position();
        writePlaceholderOffsets(buffer, getStyleCount());

        int stringsStart = buffer.position();
        writeStrings(buffer, stringOffsetsStart);
        int stringsSize = buffer.position() - stringsStart;

        writeStyles(buffer, styleOffsetsStart);

        if (!styles.isEmpty()) {
            buffer.putInt(writtenChunkOffset + STYLE_START_OFFSET,
                    getOriginalHeaderSize() + getOffsetSize() + stringsSize);
        }
    }

    /**
     * Represents all of the styles for a particular string. The string is determined by its index
     * in {@link StringPoolChunk}.
     */
    public static class StringPoolStyle implements SerializableResource {
        // Styles are a list of integers with 0xFFFFFFFF serving as a sentinel value.
        private static final int RES_STRING_POOL_SPAN_END = 0xFFFFFFFF;

        private final ObjectList<StringPoolSpan> spans;

        private StringPoolStyle(ObjectList<StringPoolSpan> spans) {
            this.spans = spans;
        }

        static StringPoolStyle create(ByteBuffer buffer, int offset, StringPoolChunk parent) {
            MutableObjectList<StringPoolSpan> spans = new MutableObjectList<>();

            while (/* nameIndex */ buffer.getInt(offset) != RES_STRING_POOL_SPAN_END) {
                spans.add(StringPoolSpan.create(buffer, offset, parent));
                offset += StringPoolSpan.SPAN_LENGTH;
            }

            return new StringPoolStyle(spans);
        }

        @Override
        public void writeTo(GrowableByteBuffer buffer) {
            for (int i = 0; i < spans.getSize(); i++) {
                spans.get(i).writeTo(buffer);
            }
            buffer.putInt(RES_STRING_POOL_SPAN_END);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringPoolStyle that = (StringPoolStyle) o;
            return Objects.equals(spans, that.spans);
        }

        @Override
        public int hashCode() {
            return Objects.hash(spans);
        }

        /**
         * Returns a brief description of the contents of this style. The representation of this
         * information is subject to change, but below is a typical example:
         *
         * <pre>"StringPoolStyle{spans=[StringPoolSpan{foo, start=0, stop=5}, ...]}"</pre>
         */
        @Override
        public String toString() {
            return String.format("StringPoolStyle{spans=%s}", spans);
        }
    }

    /**
     * Represents a styled span associated with a specific string.
     */
    private static class StringPoolSpan implements SerializableResource {
        static final int SPAN_LENGTH = 12;

        private final int nameIndex;
        private final int start;
        private final int stop;
        private final StringPoolChunk parent;

        private StringPoolSpan(int nameIndex, int start, int stop, StringPoolChunk parent) {
            this.nameIndex = nameIndex;
            this.start = start;
            this.stop = stop;
            this.parent = parent;
        }

        static StringPoolSpan create(ByteBuffer buffer, int offset, StringPoolChunk parent) {
            int nameIndex = buffer.getInt(offset);
            int start = buffer.getInt(offset + 4);
            int stop = buffer.getInt(offset + 8);
            return new StringPoolSpan(nameIndex, start, stop, parent);
        }

        @Override
        public void writeTo(GrowableByteBuffer buffer) {
            buffer.putInt(nameIndex);
            buffer.putInt(start);
            buffer.putInt(stop);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringPoolSpan that = (StringPoolSpan) o;
            return nameIndex == that.nameIndex &&
                    start == that.start &&
                    stop == that.stop &&
                    Objects.equals(parent, that.parent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nameIndex, start, stop, parent);
        }

        /**
         * Returns a brief description of this span. The representation of this information is subject
         * to change, but below is a typical example:
         *
         * <pre>"StringPoolSpan{foo, start=0, stop=5}"</pre>
         */
        @Override
        public String toString() {
            return String.format("StringPoolSpan{%s, start=%d, stop=%d}",
                    parent.getString(nameIndex), start, stop);
        }
    }
}
