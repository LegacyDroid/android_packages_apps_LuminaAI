#pragma once

/**
 * @file JniBridgeC.hpp
 *
 * Native <-> Java bridge.
 *
 * The Cubism file loader reads every file through the Java class
 * `JniBridgeJava` (which reads from the APK assets via AssetManager), because
 * the model + shader files are packaged as assets - including the encrypted
 * `.enc` model files (see LAppPal::LoadFileAsBytes for the decryption step).
 *
 * Method IDs are cached once in JNI_OnLoad so the per-call JNI overhead is
 * minimal.
 */

#include <CubismFramework.hpp>
#include <Type/csmString.hpp>
#include <Type/csmVector.hpp>

class JniBridgeC
{
public:
    /**
     * Loads an asset file (e.g. "IceGirl/IceGirl.moc3.enc") as raw bytes.
     *
     * @param filePath asset path
     * @param outSize  receives the buffer size
     * @return heap-allocated buffer (delete[] it) or nullptr when the asset
     *         does not exist
     */
    static char* LoadFileAsBytesFromJava(const char* filePath, Csm::csmSizeInt* outSize);

    /**
     * Lists the entries of an asset directory ("" = assets root).
     * Directory entries have a trailing '/'.
     *
     * @return asset name list
     */
    static Csm::csmVector<Csm::csmString> GetAssetList(const Csm::csmString& path);

    /**
     * Detaches the calling thread from the JVM when it was attached on demand
     * (worker threads doing asset IO must call this before exiting - Java
     * threads and the GL thread are never affected).
     */
    static void DetachThreadEnv();
};