#pragma once

/*
 * LAppPal.hpp
 *
 * Platform layer, Android port of the Cubism sample. Asset loading, frame
 * timing and logcat logging.
 */

#include <CubismFramework.hpp>
#include <Type/csmString.hpp>

class LAppPal
{
public:
    /**
     * Loads a file from the APK assets as raw bytes. Returns a heap buffer
     * the caller releases with ReleaseBytes, or null.
     */
    static Csm::csmByte* LoadFileAsBytes(const std::string filePath, Csm::csmSizeInt* outSize);

    /** Frees a buffer from LoadFileAsBytes. */
    static void ReleaseBytes(Csm::csmByte* byteData);

    /** Seconds since the previous UpdateTime call. */
    static Csm::csmFloat32 GetDeltaTime();

    /** Call once per frame. */
    static void UpdateTime();

    // logging
    static void PrintLog(const Csm::csmChar* format, ...);
    static void PrintLogLn(const Csm::csmChar* format, ...);
    static void PrintMessage(const Csm::csmChar* message);
    static void PrintMessageLn(const Csm::csmChar* message);

    /** Monotonic clock in seconds. */
    static double GetSystemTime();

private:
    static double s_currentFrame; // current frame time
    static double s_lastFrame;    // previous frame time
    static double s_deltaTime;    // frame delta
};