package com.teamarcadia.arcadiatweaks.neoforge.refinedstorage;

/**
 * Added to RS2 {@code PlatformStorage} (every item and fluid disk) by {@code PlatformStorageMixin}.
 *
 * The content version is bumped before every executed insert or extract and every load, which
 * are the only paths that change what the disk saves. The cached encoding is the saved form of
 * the disk at a given content version.
 *
 * @author vyrriox
 */
public interface ArcadiaVersionedStorage {

    long arcadia$getContentVersion();

    /** The cached encoding if it was made at {@code contentVersion}, else null. */
    Object arcadia$getCachedEncoding(long contentVersion);

    void arcadia$setCachedEncoding(long contentVersion, Object encoding);
}
