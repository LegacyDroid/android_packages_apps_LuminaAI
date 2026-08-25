#pragma once

/*
 * JniBridgeC.hpp
 *
 * Java side of the JNI bridge. The Cubism file loader reads every model and
 * shader file through JniBridgeJava because those live in the APK assets.
 * Method ids are cached once in JNI_OnLoad to keep call overhead low.
 */

#include <CubismFramework.hpp>
#include <Type/csmString.hpp>
#include <Type/csmVector.hpp>

class JniBridgeC
{
public:
    /**
     * Loads an asset file as raw bytes. Returns a heap buffer the caller
     * frees with delete[], or null when the asset does not exist.
     */
    static char* LoadFileAsBytesFromJava(const char* filePath, Csm::csmSizeInt* outSize);

    /** Lists an asset directory, empty path means the assets root. */
    static Csm::csmVector<Csm::csmString> GetAssetList(const Csm::csmString& path);

    /**
     * Detaches the calling thread from the JVM if we attached it earlier.
     * Worker threads doing asset IO call this before exiting.
     */
    static void DetachThreadEnv();
};