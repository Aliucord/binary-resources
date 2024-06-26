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

import androidx.collection.MutableIntList;
import androidx.collection.MutableObjectList;
import androidx.collection.ObjectList;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedBytes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Represents a type chunk, which contains the resource values for a specific resource type and
 * configuration in a {@link PackageChunk}. The resource values in this chunk correspond to
 * the array of type strings in the enclosing {@link PackageChunk}.
 *
 * <p>A {@link PackageChunk} can have multiple of these chunks for different
 * (configuration, resource type) combinations.
 */
public final class TypeChunk extends Chunk {
    /**
     * The type identifier of the resource type this chunk is holding.
     */
    private final int id;

    /**
     * The number of resources of this type at creation time.
     */
    private final int entryCount;

    /**
     * The offset (from {@code offset}) in the original buffer where {@code entries} start.
     */
    private final int entriesStart;

    /**
     * The raw entry offsets based, not including the offset of {@link TypeChunk#entriesStart},
     * into the buffer {@link TypeChunk#srcBuffer}.
     */
    private final MutableIntList entryOffsets;

    /**
     * Additional overrides (or additions) to the entry list not in the original chunk.
     * Mapped by index -> new entry.
     */
    private final TreeMap<Integer, Entry> entryOverrides = new TreeMap<>();

    /**
     * The amount of additional entries added to this chunk through {@link TypeChunk#entryOverrides}
     * that do not override an existing entry.
     */
    private int newEntryCount = 0;

    /**
     * The resource configuration that these resource entries correspond to.
     */
    private BinaryResourceConfiguration configuration;

    /**
     * The buffer to read entries based on {@link TypeChunk#entryOffsets} from.
     */
    private ByteBuffer srcBuffer = null;

    TypeChunk(ByteBuffer buffer, @Nullable Chunk parent) {
        super(buffer, parent);
        id = UnsignedBytes.toInt(buffer.get());
        buffer.position(buffer.position() + 3);  // Skip 3 bytes for packing
        entryCount = buffer.getInt();
        entriesStart = buffer.getInt();
        configuration = BinaryResourceConfiguration.create(buffer);
        entryOffsets = new MutableIntList(entryCount);
    }

    @Override
    protected void init(ByteBuffer buffer) {
        srcBuffer = buffer;
        for (int i = 0; i < entryCount; ++i) {
            entryOffsets.add(buffer.getInt());
        }
    }

    /**
     * Returns the (1-based) type id of the resource types that this {@link TypeChunk} is holding.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the name of the type this chunk represents (e.g. string, attr, id).
     */
    public String getTypeName() {
        PackageChunk packageChunk = getPackageChunk();
        Preconditions.checkNotNull(packageChunk, "%s has no parent package.", getClass());
        StringPoolChunk typePool = packageChunk.getTypeStringPool();
        Preconditions.checkNotNull(typePool, "%s's parent package has no type pool.", getClass());
        return typePool.getString(getId() - 1);  // - 1 here to convert to 0-based index
    }

    /**
     * Returns the resource configuration that these resource entries correspond to.
     */
    public BinaryResourceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the resource configuration that this chunk's entries correspond to.
     *
     * @param configuration The new configuration.
     */
    public void setConfiguration(BinaryResourceConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the total number of entries for this type + configuration, including null entries.
     */
    public int getTotalEntryCount() {
        return entryCount + newEntryCount;
    }

    private int getEntryOffset(int index) {
        return getOriginalOffset() + entriesStart + entryOffsets.get(index);
    }

    /**
     * Gets the entry at a specific index. If this entry has been overridden,
     * then the new entry will be returned, likewise with a newly added entry.
     */
    @Nullable
    public Entry getEntry(int index) {
        if (index < 0 || index >= getTotalEntryCount()) {
            return null;
        }

        return entryOverrides.containsKey(index)
                ? entryOverrides.get(index)
                : (entryOffsets.get(index) != Entry.NO_ENTRY
                ? Entry.newInstance(srcBuffer, getEntryOffset(index), this)
                : null);
    }

    /**
     * Returns true if this chunk contains an entry for {@code resourceId}.
     */
    public boolean containsResource(BinaryResourceIdentifier resourceId) {
        PackageChunk packageChunk = Preconditions.checkNotNull(getPackageChunk());
        int packageId = packageChunk.getId();
        int typeId = getId();
        return resourceId.packageId() == packageId
                && resourceId.typeId() == typeId
                && resourceId.entryId() < getTotalEntryCount()
                && (
                entryOverrides.containsKey(resourceId.entryId())
                        ? entryOverrides.get(resourceId.entryId()) != null
                        : entryOffsets.get(resourceId.entryId()) != Entry.NO_ENTRY);
    }

    /**
     * Overrides the entries in this chunk at the given index:entry pairs in {@code entries}.
     * For example, if the current list of entries is {0: foo, 1: bar, 2: baz}, and {@code entries}
     * is {1: qux, 3: quux}, then the entries will be changed to {0: foo, 1: qux, 2: baz}. If an entry
     * has an index that does not exist in the dense entry list, then it is considered a no-op for
     * that single entry.
     *
     * @param entries A sparse list containing index:entry pairs to override.
     */
    public void overrideEntries(Map<Integer, Entry> entries) {
        for (Map.Entry<Integer, Entry> entry : entries.entrySet()) {
            int index = entry.getKey() != null ? entry.getKey() : -1;
            overrideEntry(index, entry.getValue());
        }
    }

    /**
     * Adds a new entry to the end of the entries list in this chunk.
     *
     * @param entry The new entry to be added. This can be null to indicate no entry.
     * @return The entry index of the newly added resource.
     */
    public int addEntry(@Nullable Entry entry) {
        entryOverrides.put(getTotalEntryCount(), entry);
        newEntryCount++;
        return getTotalEntryCount() - 1;
    }

    /**
     * Overrides an entry at the given index. Passing null for the {@code entry} will remove that
     * entry from {@code entries}. Indices < 0 or >= {@link #getTotalEntryCount()} are a no-op.
     *
     * @param index The 0-based index for the entry to override.
     * @param entry The entry to override, or null if the entry should be removed at this location.
     */
    public void overrideEntry(int index, @Nullable Entry entry) {
        if (index >= 0 && index < getTotalEntryCount()) {
            entryOverrides.put(index, entry);
        }
    }

    private String getString(int index) {
        ResourceTableChunk resourceTable = getResourceTableChunk();
        Preconditions.checkNotNull(resourceTable, "%s has no resource table.", getClass());
        return resourceTable.getStringPool().getString(index);
    }

    private String getKeyName(int index) {
        PackageChunk packageChunk = getPackageChunk();
        Preconditions.checkNotNull(packageChunk, "%s has no parent package.", getClass());
        StringPoolChunk keyPool = packageChunk.getKeyStringPool();
        Preconditions.checkNotNull(keyPool, "%s's parent package has no key pool.", getClass());
        return keyPool.getString(index);
    }

    @Nullable
    private ResourceTableChunk getResourceTableChunk() {
        Chunk chunk = getParent();
        while (chunk != null && !(chunk instanceof ResourceTableChunk)) {
            chunk = chunk.getParent();
        }
        return chunk != null ? (ResourceTableChunk) chunk : null;
    }

    /**
     * Returns the package enclosing this chunk, if any. Else, returns null.
     */
    @Nullable
    public PackageChunk getPackageChunk() {
        Chunk chunk = getParent();
        while (chunk != null && !(chunk instanceof PackageChunk)) {
            chunk = chunk.getParent();
        }
        return chunk != null ? (PackageChunk) chunk : null;
    }

    @Override
    protected Type getType() {
        return Chunk.Type.TABLE_TYPE;
    }

    /**
     * Returns the number of bytes needed for offsets based on {@code entries}.
     */
    private int getOffsetSize() {
        return getTotalEntryCount() * 4;
    }

    /**
     * Write 0s a specified amount of times as placeholder offsets to be filled in later.
     */
    private void writePlaceholderOffsets(GrowableByteBuffer buffer, int count) {
        for (int i = 0; i < count; i++) {
            buffer.putInt(0); // Will be filled in later
        }
    }

    private void writeEntries(GrowableByteBuffer buffer, int offsetsStart) {
        int offset = 0;
        int offsetIdx = 0;

        for (int i = 0; i < entryOffsets.getSize(); ++i) {
            if (!entryOverrides.isEmpty() && entryOverrides.containsKey(i)) {
                Entry entry = entryOverrides.get(i);
                if (entry == null) {
                    buffer.putInt(offsetsStart + (offsetIdx++ * 4), Entry.NO_ENTRY);
                } else {
                    int entryStart = buffer.position();
                    entry.writeTo(buffer);
                    int entrySize = buffer.position() - entryStart;

                    buffer.putInt(offsetsStart + (offsetIdx++ * 4), offset);
                    offset += entrySize;
                }
            } else {
                if (entryOffsets.get(i) == Entry.NO_ENTRY) {
                    buffer.putInt(offsetsStart + (offsetIdx++ * 4), Entry.NO_ENTRY);
                } else {
                    int entryOffset = getEntryOffset(i);
                    int entrySize = Entry.readSize(srcBuffer, entryOffset);
                    buffer.put(srcBuffer.array(), entryOffset, entrySize);
                    buffer.putInt(offsetsStart + (offsetIdx++ * 4), offset);
                    offset += entrySize;
                }
            }
        }

        if (newEntryCount > 0) {
            @SuppressWarnings("unchecked")
            TreeMap<Integer, Entry> newEntries = (TreeMap<Integer, Entry>) entryOverrides.clone();

            // Remove all overridden entries to only write new ones
            final Iterator<Integer> indexIterator = newEntries.keySet().iterator();
            while (indexIterator.hasNext()) {
                if (indexIterator.next() < entryCount) {
                    indexIterator.remove();
                }
            }

            for (Entry entry : newEntries.values()) {
                if (entry == null) {
                    buffer.putInt(offsetsStart + (offsetIdx++ * 4), Entry.NO_ENTRY);
                } else {
                    int entryStart = buffer.position();
                    entry.writeTo(buffer);
                    int entrySize = buffer.position() - entryStart;

                    buffer.putInt(offsetsStart + (offsetIdx++ * 4), offset);
                    offset += entrySize;
                }
            }
        }

        ChunkUtils.writePad(buffer, offset);
    }

    @Override
    protected void writeHeader(GrowableByteBuffer buffer) {
        super.writeHeader(buffer);
        int entriesStart = getOriginalHeaderSize() + getOffsetSize();
        buffer.putInt(id);  // Write an unsigned byte with 3 bytes padding
        buffer.putInt(getTotalEntryCount());
        buffer.putInt(entriesStart);
        configuration.writeTo(buffer);
    }

    @Override
    protected void writePayload(GrowableByteBuffer buffer) {
        int start = buffer.position();
        writePlaceholderOffsets(buffer, getTotalEntryCount());
        writeEntries(buffer, start);
    }

    /**
     * An {@link Entry} in a {@link TypeChunk}. Contains one or more {@link BinaryResourceValue}.
     */
    public static class Entry implements SerializableResource {

        /**
         * An entry offset that indicates that a given resource is not present.
         */
        public static final int NO_ENTRY = 0xFFFFFFFF;

        /**
         * Set if this is a complex resource. Otherwise, it's a simple resource.
         */
        private static final int FLAG_COMPLEX = 0x0001;

        /**
         * Size of a single resource id + value mapping entry.
         */
        private static final int MAPPING_SIZE = 4 + BinaryResourceValue.SIZE;

        private final int headerSize;
        private final int flags;
        private final int keyIndex;
        private final BinaryResourceValue value;
        private final ObjectList<BinaryResourceValue> values;
        private final int parentEntry;
        private final TypeChunk parent;

        public Entry(int headerSize,
                     int flags,
                     int keyIndex,
                     BinaryResourceValue value,
                     ObjectList<BinaryResourceValue> values,
                     int parentEntry,
                     TypeChunk parent) {
            this.headerSize = headerSize;
            this.flags = flags;
            this.keyIndex = keyIndex;
            this.value = value;
            this.values = values;
            this.parentEntry = parentEntry;
            this.parent = parent;
        }

        private static Entry newInstance(ByteBuffer buffer, int offset, TypeChunk parent) {
            buffer.position(offset);

            int headerSize = buffer.getShort() & 0xFFFF;
            int flags = buffer.getShort() & 0xFFFF;
            int keyIndex = buffer.getInt();
            BinaryResourceValue value = null;
            MutableObjectList<BinaryResourceValue> values = null;
            int parentEntry = -1;
            if ((flags & FLAG_COMPLEX) != 0) {
                parentEntry = buffer.getInt();
                int valueCount = buffer.getInt();
                values = new MutableObjectList<>(valueCount);
                for (int i = 0; i < valueCount; ++i) {
                    values.add(BinaryResourceValue.createComplex(buffer));
                }
            } else {
                value = BinaryResourceValue.create(buffer);
            }

            return new Entry(headerSize, flags, keyIndex, value, values, parentEntry, parent);
        }

        private static int readSize(ByteBuffer buffer, int offset) {
            buffer.position(offset);
            int headerSize = buffer.getShort() & 0xFFFF;
            int flags = buffer.getShort() & 0xFFFF;
            if ((flags & FLAG_COMPLEX) != 0) {
                buffer.getInt(); // keyIndex
                buffer.getInt(); // parentEntry
                int valueCount = buffer.getInt();

                return headerSize + valueCount * MAPPING_SIZE;
            } else {
//                Log.i("binary-resources", headerSize + " @" + Integer.toHexString(offset));
                return headerSize + BinaryResourceValue.SIZE;
            }
        }

        /**
         * Number of bytes in the header of the {@link Entry}.
         */
        public int headerSize() {
            return headerSize;
        }

        /**
         * Resource entry flags.
         */
        public int flags() {
            return flags;
        }

        /**
         * Index into {@link PackageChunk#getKeyStringPool} identifying this entry.
         */
        public int keyIndex() {
            return keyIndex;
        }

        /**
         * The value of this resource entry, if this is not a complex entry.
         *
         * @throws IllegalStateException If this value is complex.
         */
        @NotNull
        public BinaryResourceValue value() {
            if (isComplex()) {
                throw new IllegalStateException("Cannot get single value for complex entry!");
            }

            return value;
        }

        /**
         * The extra values in this resource entry if this {@link #isComplex}.
         *
         * @throws IllegalStateException If this value is not complex.
         */
        @NotNull
        public ObjectList<BinaryResourceValue> values() {
            if (!isComplex()) {
                throw new IllegalStateException("Cannot get values for non-complex entry!");
            }

            return values;
        }

        /**
         * Entry into {@link PackageChunk} that is the parent {@link Entry} to this entry.
         * This value only makes sense when this is complex ({@link #isComplex} returns true).
         */
        public int parentEntry() {
            return parentEntry;
        }

        /**
         * The {@link TypeChunk} that this resource entry belongs to.
         */
        public TypeChunk parent() {
            return parent;
        }

        /**
         * Returns the name of the type this chunk represents (e.g. string, attr, id).
         */
        public final String typeName() {
            return parent().getTypeName();
        }

        /**
         * The total number of bytes that this {@link Entry} takes up.
         */
        public final int size() {
            return headerSize() + (isComplex() ? values.getSize() * MAPPING_SIZE : BinaryResourceValue.SIZE);
        }

        /**
         * Returns the key name identifying this resource entry.
         */
        public final String key() {
            return parent().getKeyName(keyIndex());
        }

        /**
         * Returns true if this is a complex resource.
         */
        public final boolean isComplex() {
            return (flags() & FLAG_COMPLEX) != 0;
        }

        @Override
        public void writeTo(GrowableByteBuffer buffer) {
            buffer.putShort((short) headerSize());
            buffer.putShort((short) flags());
            buffer.putInt(keyIndex());
            if (isComplex()) {
                buffer.putInt(parentEntry());
                buffer.putInt(values.getSize());
                for (int i = 0; i < values.getSize(); i++) {
                    BinaryResourceValue value = values.get(i);
                    buffer.putInt(value.key());
                    value.writeTo(buffer);
                }
            } else {
                BinaryResourceValue value = value();
                Preconditions.checkNotNull(value, "A non-complex TypeChunk entry must have a value.");
                value.writeTo(buffer);
            }
        }

        @Override
        public final String toString() {
            return String.format("Entry{key=%s}", key());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return headerSize == entry.headerSize &&
                    flags == entry.flags &&
                    keyIndex == entry.keyIndex &&
                    parentEntry == entry.parentEntry &&
                    Objects.equals(value, entry.value) &&
                    Objects.equals(values, entry.values) &&
                    Objects.equals(parent, entry.parent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(headerSize, flags, keyIndex, value, values, parentEntry, parent);
        }
    }
}
