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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.primitives.UnsignedBytes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single typed resource value.
 */
public class BinaryResourceValue implements SerializableResource {

    /**
     * The serialized size in bytes of a {@link BinaryResourceValue}.
     */
    public static final int SIZE = 8;

    private Type type;
    private int data;
    private final int subValueKey;

    /**
     * Create a new resource value as not part of a complex resource.
     *
     * @param type The type of this resource value.
     * @param data The raw data value of type {@code type}.
     */
    public BinaryResourceValue(Type type, int data) {
        this(type, data, -1);
    }

    /**
     * Create a new resource value.
     *
     * @param type The type of this resource value.
     * @param data The raw data value of type {@code type}.
     * @param key If this resource is a sub-resource as part of a complex resource, then
     * this is the key of this resource inside the list of sub-resources.
     */
    public BinaryResourceValue(Type type, int data, int key) {
        this.type = type;
        this.data = data;
        this.subValueKey = key;
    }

    public static BinaryResourceValue create(ByteBuffer buffer) {
        int size = (buffer.getShort() & 0xFFFF);
        if (size != SIZE) {
            throw new IllegalStateException("BinaryResourceValue must be of size 8");
        }

        buffer.get();  // Unused
        Type type = Type.fromCode(buffer.get());
        int data = buffer.getInt();
        return new BinaryResourceValue(type, data);
    }

    public static BinaryResourceValue createComplex(ByteBuffer buffer) {
        int key = buffer.getInt();
        int size = (buffer.getShort() & 0xFFFF);
        if (size != SIZE) {
            throw new IllegalStateException("BinaryResourceValue must be of size 8");
        }

        buffer.get();  // Unused
        Type type = Type.fromCode(buffer.get());
        int data = buffer.getInt();
        return new BinaryResourceValue(type, data, key);
    }

    /**
     * The length in bytes of this value.
     */
    public int size() {
        return SIZE;
    }

    /**
     * The raw data type of this value.
     */
    public Type type() {
        return type;
    }

    /**
     * The actual 4-byte value; interpretation of the value depends on {@code dataType}.
     */
    public int data() {
        return data;
    }

    /**
     * If this resource is a sub-resource apart of a complex resource, then this
     * is this resource's key in the list of sub-resources. Otherwise, -1.
     */
    public int key() {
        return subValueKey;
    }

    /**
     * Replaces the value stored inside this resource value.
     */
    public void setValue(Type type, int data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public byte[] toByteArray(boolean shrink) {
        ByteBuffer buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        writeToBuffer(buffer);
        return buffer.array();
    }

    public int writeToBuffer(ByteBuffer buffer) {
        int start = buffer.position();
        buffer.putShort((short) size());
        buffer.put((byte) 0);  // Unused
        buffer.put(type().code());
        buffer.putInt(data());
        return buffer.position() - start;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryResourceValue that = (BinaryResourceValue) o;
        return data == that.data && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, data);
    }

    /**
     * Resource type codes.
     */
    public enum Type {
        /**
         * {@code data} is either 0 (undefined) or 1 (empty).
         */
        NULL(0x00),
        /**
         * {@code data} holds a {@link ResourceTableChunk} entry reference.
         */
        REFERENCE(0x01),
        /**
         * {@code data} holds an attribute resource identifier.
         */
        ATTRIBUTE(0x02),
        /**
         * {@code data} holds an index into the containing resource table's string pool.
         */
        STRING(0x03),
        /**
         * {@code data} holds a single-precision floating point number.
         */
        FLOAT(0x04),
        /**
         * {@code data} holds a complex number encoding a dimension value, such as "100in".
         */
        DIMENSION(0x05),
        /**
         * {@code data} holds a complex number encoding a fraction of a container.
         */
        FRACTION(0x06),
        /**
         * {@code data} holds a dynamic {@link ResourceTableChunk} entry reference.
         */
        DYNAMIC_REFERENCE(0x07),
        /**
         * {@code data} holds a dynamic attribute resource identifier.
         */
        DYNAMIC_ATTRIBUTE(0x08),
        /**
         * {@code data} is a raw integer value of the form n..n.
         */
        INT_DEC(0x10),
        /**
         * {@code data} is a raw integer value of the form 0xn..n.
         */
        INT_HEX(0x11),
        /**
         * {@code data} is either 0 (false) or 1 (true).
         */
        INT_BOOLEAN(0x12),
        /**
         * {@code data} is a raw integer value of the form #aarrggbb.
         */
        INT_COLOR_ARGB8(0x1c),
        /**
         * {@code data} is a raw integer value of the form #rrggbb.
         */
        INT_COLOR_RGB8(0x1d),
        /**
         * {@code data} is a raw integer value of the form #argb.
         */
        INT_COLOR_ARGB4(0x1e),
        /**
         * {@code data} is a raw integer value of the form #rgb.
         */
        INT_COLOR_RGB4(0x1f);

        private static final Map<Byte, Type> FROM_BYTE;

        static {
            Builder<Byte, Type> builder = ImmutableMap.builder();
            for (Type type : values()) {
                builder.put(type.code(), type);
            }
            FROM_BYTE = builder.build();
        }

        private final byte code;

        Type(int code) {
            this.code = UnsignedBytes.checkedCast(code);
        }

        public static Type fromCode(byte code) {
            return Preconditions.checkNotNull(FROM_BYTE.get(code), "Unknown resource type: %s", code);
        }

        public byte code() {
            return code;
        }
    }
}
