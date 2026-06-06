package dev.fucksable.fix;

import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局损坏指针缓存，供多个 Mixin 共享。
 * key = chunkPos.toLong(), value = 该 chunk 中已知损坏的指针集合。
 */
public final class CorruptedPointerCache {

    private static final ConcurrentHashMap<Long, Set<SavedSubLevelPointer>> cache = new ConcurrentHashMap<>();

    public static boolean isCorrupted(long chunkKey, SavedSubLevelPointer pointer) {
        Set<SavedSubLevelPointer> set = cache.get(chunkKey);
        return set != null && set.contains(pointer);
    }

    public static void markCorrupted(long chunkKey, SavedSubLevelPointer pointer) {
        cache.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(pointer);
    }

    public static Set<SavedSubLevelPointer> getCorrupted(long chunkKey) {
        return cache.get(chunkKey);
    }

    public static void clear() {
        cache.clear();
    }
}
