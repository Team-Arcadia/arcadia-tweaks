package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache rules of S3 with a fake disk and a counting encoder (no Minecraft, no RS).
 *
 * @author vyrriox
 */
final class ArcadiaStorageSaveCacheTest {

    private static final DynamicOps<JsonElement> OPS = JsonOps.INSTANCE;

    /** Fake disk: the version moves on every write, like PlatformStorageMixin. */
    private static final class FakeDisk implements ArcadiaVersionedStorage {
        long version;
        long encodedVersion = -1;
        Object encoding;
        String content = "a";
        boolean failCacheRead;

        void write(String newContent) {
            version++;
            content = newContent;
        }

        @Override
        public long arcadia$getContentVersion() {
            return version;
        }

        @Override
        public Object arcadia$getCachedEncoding(long contentVersion) {
            if (failCacheRead) {
                throw new IllegalStateException("broken");
            }
            return encodedVersion == contentVersion ? encoding : null;
        }

        @Override
        public void arcadia$setCachedEncoding(long contentVersion, Object encoding) {
            this.encodedVersion = contentVersion;
            this.encoding = encoding;
        }
    }

    private static final class CountingEncoder implements Encoder<FakeDisk> {
        int calls;
        boolean fail;

        @Override
        public <T> DataResult<T> encode(FakeDisk input, DynamicOps<T> ops, T prefix) {
            calls++;
            if (fail) {
                return DataResult.error(() -> "cannot encode");
            }
            return DataResult.success(ops.createString(input.content));
        }
    }

    private CountingEncoder encoder;
    private FakeDisk disk;

    @BeforeEach
    void setUp() {
        ArcadiaStorageSaveCache.resetForTests();
        encoder = new CountingEncoder();
        disk = new FakeDisk();
    }

    @AfterEach
    void tearDown() {
        ArcadiaStorageSaveCache.resetForTests();
    }

    private DataResult<JsonElement> save(boolean enabled) {
        ArcadiaStorageSaveCache.onSaveStart(0);
        return ArcadiaStorageSaveCache.encode(encoder, disk, OPS, OPS.empty(), enabled, true);
    }

    @Test
    void unchangedDiskReusesTheCachedResult() {
        final DataResult<JsonElement> first = save(true);
        final DataResult<JsonElement> second = save(true);
        assertSame(first, second);
        assertEquals(1, encoder.calls);
        assertEquals(1, ArcadiaStorageSaveCache.hits());
    }

    @Test
    void writeInvalidatesTheCache() {
        save(true);
        disk.write("b");
        final DataResult<JsonElement> afterWrite = save(true);
        assertEquals(new JsonPrimitive("b"), afterWrite.getOrThrow());
        assertEquals(2, encoder.calls);
    }

    @Test
    void disabledOrUncacheableContextAlwaysEncodes() {
        save(false);
        save(false);
        assertEquals(2, encoder.calls);

        ArcadiaStorageSaveCache.encode(encoder, disk, OPS, OPS.empty(), true, false);
        ArcadiaStorageSaveCache.encode(encoder, disk, OPS, OPS.empty(), true, false);
        assertEquals(4, encoder.calls);
    }

    @Test
    void valuesWithoutVersionAreNeverCached() {
        final Encoder<String> plain = new Encoder<>() {
            int calls;

            @Override
            public <T> DataResult<T> encode(String input, DynamicOps<T> ops, T prefix) {
                calls++;
                return DataResult.success(ops.createInt(calls));
            }
        };
        final DataResult<JsonElement> a = ArcadiaStorageSaveCache.encode(plain, "addon storage", OPS, OPS.empty(), true, true);
        final DataResult<JsonElement> b = ArcadiaStorageSaveCache.encode(plain, "addon storage", OPS, OPS.empty(), true, true);
        assertNotSame(a, b);
    }

    @Test
    void errorsAreNotCached() {
        encoder.fail = true;
        assertTrue(save(true).isError());
        assertTrue(save(true).isError());
        assertEquals(2, encoder.calls);
        encoder.fail = false;
        assertTrue(save(true).isSuccess());
        assertEquals(3, encoder.calls);
    }

    @Test
    void verifySaveEncodesAgainAndKeepsTheCacheWhenEqual() {
        save(true);
        ArcadiaStorageSaveCache.onSaveStart(1);
        final DataResult<JsonElement> verified = ArcadiaStorageSaveCache.encode(encoder, disk, OPS, OPS.empty(), true, true);
        assertEquals(2, encoder.calls);
        assertEquals(new JsonPrimitive("a"), verified.getOrThrow());
        assertEquals(1, ArcadiaStorageSaveCache.verifications());
        assertFalse(ArcadiaStorageSaveCache.isDisabledForSession());
    }

    @Test
    void verifyMismatchWritesFreshDataAndTurnsTheCacheOff() {
        save(true);
        // Content changed behind the version tracking: the cache is now stale.
        disk.content = "changed without a version bump";
        assertEquals(new JsonPrimitive("a"), save(true).getOrThrow(), "stale hit before verification");

        ArcadiaStorageSaveCache.onSaveStart(1);
        final DataResult<JsonElement> verified = ArcadiaStorageSaveCache.encode(encoder, disk, OPS, OPS.empty(), true, true);
        assertEquals(new JsonPrimitive("changed without a version bump"), verified.getOrThrow());
        assertTrue(ArcadiaStorageSaveCache.isDisabledForSession());

        final int calls = encoder.calls;
        save(true);
        assertEquals(calls + 1, encoder.calls, "cache off: every save encodes");
    }

    @Test
    void exceptionInCacheCodeFallsBackToPlainEncoding() {
        disk.failCacheRead = true;
        final DataResult<JsonElement> result = save(true);
        assertEquals(new JsonPrimitive("a"), result.getOrThrow());
        assertTrue(ArcadiaStorageSaveCache.isDisabledForSession());
    }

    @Test
    void verifyIntervalCountsSaves() {
        save(true);
        for (int i = 1; i <= 6; i++) {
            ArcadiaStorageSaveCache.onSaveStart(3);
            ArcadiaStorageSaveCache.encode(encoder, disk, OPS, OPS.empty(), true, true);
        }
        // Saves 1..6 of this loop are global saves 2..7: saves 3 and 6 verify.
        assertEquals(2, ArcadiaStorageSaveCache.verifications());
        assertEquals(1 + 2, encoder.calls);
    }
}
