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

/**
 * A resource, typically a @{link Chunk}, that can be converted to an array of bytes.
 */
public interface SerializableResource {

    /**
     * Writes this resource to a byte buffer at the current position.
     * When finished, the byte buffer should be advanced to the end of the current buffer.
     */
    void writeTo(GrowableByteBuffer buffer);
}
