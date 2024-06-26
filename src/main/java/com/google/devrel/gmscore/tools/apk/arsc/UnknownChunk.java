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
 * A chunk whose contents are unknown (or currently unhandled).
 * This is a placeholder to copy over the existing data.
 */
public final class UnknownChunk extends Chunk {

    private final Type type;

    private final byte[] header;

    private final byte[] payload;

    UnknownChunk(ByteBuffer buffer, @Nullable Chunk parent) {
        super(buffer, parent);

        type = Type.fromCode(buffer.getShort(getOriginalOffset()));
        header = new byte[getOriginalHeaderSize() - Chunk.METADATA_SIZE];
        payload = new byte[getOriginalChunkSize() - getOriginalHeaderSize()];
        buffer.get(header);
        buffer.get(payload);
    }

    @Override
    protected void writeHeader(GrowableByteBuffer buffer) {
        super.writeHeader(buffer);
        buffer.put(header);
    }

    @Override
    protected void writePayload(GrowableByteBuffer buffer) {
        buffer.put(payload);
    }

    @Override
    protected Type getType() {
        return type;
    }
}
