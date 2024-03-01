package com.google.devrel.gmscore.tools.apk.arsc;

import androidx.annotation.NonNull;
import androidx.collection.IntObjectMap;
import androidx.collection.ScatterMapKt;

import java.util.Iterator;
import java.util.function.Consumer;

import kotlin.jvm.functions.Function1;

/**
 * @noinspection KotlinInternalInJava
 */
class IntObjectMapIterator {
    /**
     * Allocation free iterator over keys & values of {@link IntObjectMap}.
     * This is just essentially reusing {@link IntObjectMap#forEachIndexed(Function1)}
     */
    @SuppressWarnings("unchecked")
    public static <V, E extends Throwable> void forEachIndexed(
            IntObjectMap<V> map,
            ForEachIndexed<V, E> iterator
    ) throws E {
        long[] metadata = map.metadata;
        int lastIndex = metadata.length - 2; // We always have 0 or at least 2 entries

        for (int i = 0; i <= lastIndex; i++) {
            long slot = metadata[i];
            if (ScatterMapKt.maskEmptyOrDeleted(slot) != ScatterMapKt.BitmaskMsb) {
                int bitCount = 8 - (~(i - lastIndex) >>> 31);
                for (int j = 0; j < bitCount; j++) {
                    if (ScatterMapKt.isFull(slot & 0xFFL)) {
                        int index = (i << 3) + j;
                        iterator.invoke(map.keys[index], (V) map.values[index]);
                    }
                    slot = slot >> 8;
                }
                if (bitCount != 8) return;
            }
        }
    }

    @FunctionalInterface
    public interface ForEachIndexed<V, E extends Throwable> {
        void invoke(int key, V value) throws E;
    }
}
