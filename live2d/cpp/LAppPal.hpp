#pragma once

/**
 * @file LAppPal.hpp
 *
 * Platform abstraction layer (Android port of the official Cubism sample):
 *
 *  - LoadFileAsBytes(): reads an asset, transparently decrypting AES-128-CTR
 *    encrypted ".enc" files (see .cpp for the decryption details).
 *  - UpdateTime()/GetDeltaTime(): frame timing via CLOCK_MONOTONIC.
 *  - PrintLog*(): logs to logcat.
 */

#include <CubismFramework.hpp>
#include <Type/csmString.hpp>

class LAppPal
{
public:
    /**
     * Loads `filePath` as raw bytes from the APK assets (plaintext - the
     * IceGirl model ships unencrypted; see live2d/LICENSE-NOTES.md).
     *
     * @return heap-allocated buffer (release with ReleaseBytes) or nullptr
     */
    static Csm::csmByte* LoadFileAsBytes(const std::string filePath, Csm::csmSizeInt* outSize);

    /** Frees a buffer returned by LoadFileAsBytes. */
    static void ReleaseBytes(Csm::csmByte* byteData);

    /** Delta seconds since the previous UpdateTime() call. */
    static Csm::csmFloat32 GetDeltaTime();

    /** Call once per rendered frame. */
    static void UpdateTime();

    // --- Logging ------------------------------------------------------------
    static void PrintLog(const Csm::csmChar* format, ...);
    static void PrintLogLn(const Csm::csmChar* format, ...);
    static void PrintMessage(const Csm::csmChar* message);
    static void PrintMessageLn(const Csm::csmChar* message);

    /** Monotonic clock seconds (used by UpdateTime). */
    static double GetSystemTime();

private:
    static double s_currentFrame; ///< time of the current frame [s]
    static double s_lastFrame;    ///< time of the previous frame [s]
    static double s_deltaTime;    ///< frame delta [s]
};