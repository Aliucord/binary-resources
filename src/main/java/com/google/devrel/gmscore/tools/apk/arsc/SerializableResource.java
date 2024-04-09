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

import java.nio.ByteOrder;

/**
 * A resource, typically a @{link Chunk}, that can be converted to an array of bytes.
 */
public interface SerializableResource {

    /**
     * Writes this resource to a byte buffer at the current position.
     * When finished, the byte buffer should be advanced to the end of the current buffer.
     */
    void writeTo(GrowableByteBuffer buffer);

    /**
     * Serializes this resource and returns a byte array representing this resource.
     * @param assumedOutputSize The guessed output size necessary for the buffer in order to preallocate enough.
     */
    default byte[] toByteArray(int assumedOutputSize) {
        GrowableByteBuffer buffer = new GrowableByteBuffer(assumedOutputSize)
                .order(ByteOrder.LITTLE_ENDIAN);

        this.writeTo(buffer);

        byte[] copy = new byte[buffer.position()];
        System.arraycopy(buffer.array(), buffer.arrayOffset(), copy, 0, buffer.position());
        return copy;
    }

    /**
     * Serializes this resource and returns a byte array representing this resource.
     */
    default byte[] toByteArray() {
        return toByteArray(512);
    }
}
