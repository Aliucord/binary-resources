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

import androidx.collection.MutableObjectList;
import androidx.collection.ObjectList;

import org.jetbrains.annotations.Nullable;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a chunk whose payload is a list of sub-chunks.
 */
public abstract class ChunkWithChunks extends Chunk {

    private final MutableObjectList<Chunk> chunks = new MutableObjectList<>();

    protected ChunkWithChunks(ByteBuffer buffer, @Nullable Chunk parent) {
        super(buffer, parent);
    }

    @Override
    protected void init(ByteBuffer buffer) {
        super.init(buffer);
        chunks.clear();
        int start = this.offset + getHeaderSize();
        int offset = start;
        int end = this.offset + getOriginalChunkSize();
        int position = buffer.position();
        buffer.position(start);

        while (offset < end) {
            Chunk chunk = Chunk.newInstance(buffer, this);
            chunks.add(chunk);
            offset += chunk.getOriginalChunkSize();
        }

        buffer.position(position);
    }

    /**
     * Retrieves the sub-chunks contained in this chunk.
     */
    public final ObjectList<Chunk> getChunks() {
        return chunks;
    }

    /**
     * Add a new sub-chunk to this chunk.
     * @param index The index to insert the new chunk at.
     * @param chunk A new chunk that is allowed here.
     * @throws IndexOutOfBoundsException If the index is out of range (`0 <= index <= size`)
     */
    public final void addChunk(int index, Chunk chunk) {
        if (index < 0 || index > chunks.getSize()) {
            throw new IndexOutOfBoundsException("Cannot insert new chunk out of bounds!");
        }

        chunks.add(index, chunk);
    }

    @Override
    protected void writePayload(DataOutput output, ByteBuffer header, boolean shrink) throws IOException {
        for (int i = 0; i < chunks.getSize(); i++) {
            Chunk chunk = chunks.get(i);
            byte[] chunkBytes = chunk.toByteArray(shrink);
            output.write(chunkBytes);
            writePad(output, chunkBytes.length);
        }
    }
}
