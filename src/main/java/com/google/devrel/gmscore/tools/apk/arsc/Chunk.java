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

import androidx.annotation.CallSuper;
import androidx.collection.MutableIntObjectMap;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Shorts;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Represents a generic chunk.
 */
public abstract class Chunk implements SerializableResource {
    /**
     * The number of bytes in every chunk that describes chunk type, header size, and chunk size.
     */
    public static final int METADATA_SIZE = 8;

    /**
     * The offset in bytes, from the start of the chunk, where the chunk size can be found.
     */
    private static final int CHUNK_SIZE_OFFSET = 4;

    /**
     * Size of the chunk header in bytes.
     */
    private final int headerSize;

    /**
     * headerSize + dataSize. The total size of this chunk.
     */
    private final int chunkSize;

    /**
     * Offset of this chunk from the start of the file.
     */
    private final int offset;

    /**
     * The parent to this chunk, if any.
     */
    @Nullable
    private final Chunk parent;

    protected Chunk(int headerSize, @Nullable Chunk parent) {
        this.headerSize = headerSize;
        this.chunkSize = -1;
        this.offset = -1;
        this.parent = parent;
    }

    protected Chunk(ByteBuffer buffer, @Nullable Chunk parent) {
        this.parent = parent;
        offset = buffer.position() - 2;
        headerSize = (buffer.getShort() & 0xFFFF);
        chunkSize = buffer.getInt();
    }

    /**
     * Creates a new chunk whose contents start at {@code buffer}'s current position.
     *
     * @param buffer A buffer positioned at the start of a chunk.
     * @return new chunk
     */
    public static Chunk newInstance(ByteBuffer buffer) {
        return newInstance(buffer, null);
    }

    /**
     * Creates a new chunk whose contents start at {@code buffer}'s current position.
     *
     * @param buffer A buffer positioned at the start of a chunk.
     * @param parent The parent to this chunk (or null if there's no parent).
     * @return new chunk
     */
    public static Chunk newInstance(ByteBuffer buffer, @Nullable Chunk parent) {
        Chunk result;
        Type type = Type.fromCode(buffer.getShort());
        switch (type) {
            case STRING_POOL:
                result = new StringPoolChunk(buffer, parent);
                break;
            case TABLE:
                result = new ResourceTableChunk(buffer, parent);
                break;
            case XML:
                result = new XmlChunk(buffer, parent);
                break;
            case XML_START_NAMESPACE:
                result = new XmlNamespaceStartChunk(buffer, parent);
                break;
            case XML_END_NAMESPACE:
                result = new XmlNamespaceEndChunk(buffer, parent);
                break;
            case XML_START_ELEMENT:
                result = new XmlStartElementChunk(buffer, parent);
                break;
            case XML_END_ELEMENT:
                result = new XmlEndElementChunk(buffer, parent);
                break;
            case XML_CDATA:
                result = new XmlCdataChunk(buffer, parent);
                break;
            case XML_RESOURCE_MAP:
                result = new XmlResourceMapChunk(buffer, parent);
                break;
            case TABLE_PACKAGE:
                result = new PackageChunk(buffer, parent);
                break;
            case TABLE_TYPE:
                result = new TypeChunk(buffer, parent);
                break;
            case TABLE_TYPE_SPEC:
                result = new TypeSpecChunk(buffer, parent);
                break;
            case TABLE_LIBRARY:
                result = new LibraryChunk(buffer, parent);
                break;
            default:
                result = new UnknownChunk(buffer, parent);
        }
        result.init(buffer);
        result.seekToEndOfChunk(buffer);
        return result;
    }

    /**
     * Finishes initialization of a chunk. This should be called immediately after the constructor.
     * This is separate from the constructor so that the header of a chunk can be fully initialized
     * before the payload of that chunk is initialized for chunks that require such behavior.
     *
     * @param buffer The buffer that the payload will be initialized from.
     */
    protected void init(ByteBuffer buffer) {
    }

    /**
     * Returns the parent to this chunk, if any. A parent is a chunk whose payload contains this
     * chunk. If there's no parent, null is returned.
     */
    @Nullable
    public Chunk getParent() {
        return parent;
    }

    /**
     * Get the type of this chunk
     */
    protected abstract Type getType();

    /**
     * Get the original offset of this chunk from the start of the file.
     */
    public final int getOriginalOffset() {
        return offset;
    }

    /**
     * Returns the size of this chunk's header. This should always stay constant even when the chunk changes.
     */
    public final int getOriginalHeaderSize() {
        return headerSize;
    }

    /**
     * Returns the size of this chunk when it was first read from a buffer. A chunk's size can deviate
     * from this value when its data is modified (e.g. adding an entry, changing a string).
     */
    public final int getOriginalChunkSize() {
        return chunkSize;
    }

    /**
     * Reposition the buffer after this chunk. Use this at the end of a Chunk constructor.
     *
     * @param buffer The buffer to be repositioned.
     */
    private void seekToEndOfChunk(ByteBuffer buffer) {
        buffer.position(offset + chunkSize);
    }

    /**
     * Writes the type and header size. We don't know how big this chunk will be (it could be
     * different since the last time we checked), so it is overwritten later after the chunk.
     * <p/>
     * This can be overridden to add additional header properties after the basic chunk header.
     *
     * @param buffer The buffer that will be written to.
     */
    @CallSuper
    protected void writeHeader(GrowableByteBuffer buffer) {
        buffer.putShort(getType().code());
        buffer.putShort((short) getOriginalHeaderSize());
        buffer.putInt(0); // This will be filled in later after writing the payload
    }

    /**
     * Writes the chunk payload. The payload is data in a chunk which is not in
     * the first {@link Chunk#getOriginalHeaderSize()} bytes of the chunk.
     *
     * @param buffer The buffer that this will be written to.
     */
    protected abstract void writePayload(GrowableByteBuffer buffer);

    /**
     * Converts this chunk into an array of bytes representation. Normally you will not need to
     * override this method unless your header changes based on the contents / size of the payload.
     */
    @Override
    public void writeTo(GrowableByteBuffer buffer) {
        int start = buffer.position();
        writeHeader(buffer);

        int headerSize = buffer.position() - start;
        Preconditions.checkState(headerSize == getOriginalHeaderSize(),
                "Written header is wrong size. Got %s, want %s", headerSize, getOriginalHeaderSize());

        writePayload(buffer);

        // Fill in chunk size that was previously left as 0
        int chunkSize = buffer.position() - start;
        buffer.putInt(start + CHUNK_SIZE_OFFSET, chunkSize);
    }

    /**
     * Types of chunks that can exist.
     */
    public enum Type {
        NULL(0x0000),
        STRING_POOL(0x0001),
        TABLE(0x0002),
        XML(0x0003),
        XML_START_NAMESPACE(0x0100),
        XML_END_NAMESPACE(0x0101),
        XML_START_ELEMENT(0x0102),
        XML_END_ELEMENT(0x0103),
        XML_CDATA(0x0104),
        XML_RESOURCE_MAP(0x0180),
        TABLE_PACKAGE(0x0200),
        TABLE_TYPE(0x0201),
        TABLE_TYPE_SPEC(0x0202),
        TABLE_LIBRARY(0x0203),
        TABLE_OVERLAYABLE(0x0204),
        TABLE_OVERLAYABLE_POLICY(0x0205),
        TABLE_STAGED_ALIAS(0x0206);

        private static final MutableIntObjectMap<Type> FROM_SHORT;

        static {
            FROM_SHORT = new MutableIntObjectMap<>(values().length);
            for (Type type : values()) {
                FROM_SHORT.put(type.code, type);
            }
        }

        private final short code;

        Type(int code) {
            this.code = Shorts.checkedCast(code);
        }

        public static Type fromCode(short code) {
            return Preconditions.checkNotNull(FROM_SHORT.get(code), "Unknown chunk type: %s", code);
        }

        public short code() {
            return code;
        }
    }
}
