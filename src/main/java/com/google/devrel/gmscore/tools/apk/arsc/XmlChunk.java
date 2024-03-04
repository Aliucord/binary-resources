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

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Represents an XML chunk structure.
 * <p>
 * An XML chunk can contain many nodes as well as a string pool which
 * contains all of the strings referenced by the nodes.
 */
public final class XmlChunk extends ChunkWithChunks {

    XmlChunk(ByteBuffer buffer, @Nullable Chunk parent) {
        super(buffer, parent);
    }

    @Override
    protected Type getType() {
        return Chunk.Type.XML;
    }

    /**
     * Finds the first string pool chunk that all string values should reference.
     *
     * @throws IllegalStateException If this XML chunk does not contain a string pool.
     */
    public StringPoolChunk getStringPool() {
        for (int i = 0; i < getChunks().getSize(); i++) {
            Chunk chunk = getChunks().get(i);

            if (chunk instanceof StringPoolChunk) {
                return (StringPoolChunk) chunk;
            }
        }

        throw new IllegalStateException("XmlChunk did not contain a string pool.");
    }
}
