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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Given an arsc file, maps the contents of the file.
 */
public final class BinaryResourceFile implements SerializableResource {

    /**
     * The chunks contained in this resource file.
     */
    private final MutableObjectList<Chunk> chunks = new MutableObjectList<>(40);

    /**
     * The original byte size of the file used for pre-allocating when writing.
     */
    private final int originalSize;

    public BinaryResourceFile(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 0) {
            chunks.add(Chunk.newInstance(buffer));
        }
        originalSize = buf.length;
    }

    /**
     * Returns the chunks in this resource file.
     */
    public ObjectList<Chunk> getChunks() {
        return chunks;
    }


    @Override
    public byte[] toByteArray() {
        return toByteArray((int) (originalSize * 1.1)); // A bit bigger to account for any additions
    }

    @Override
    public void writeTo(GrowableByteBuffer buffer) {
        for (int i = 0; i < chunks.getSize(); i++) {
            chunks.get(i).writeTo(buffer);
        }
    }
}
