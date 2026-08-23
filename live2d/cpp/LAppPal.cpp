/**
 * Android port of the Cubism sample's LAppPal.
 *
 * LoadFileAsBytes() reads model files (moc3, textures, expressions, motions,
 * physics, ...) straight from the APK assets via the Kotlin bridge. Assets
 * are shipped as plaintext: the model is a free Booth download whose terms
 * do not require protection, so no encryption layer exists here.
 */

#include "LAppPal.hpp"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include <android/log.h>
#include <Model/CubismMoc.hpp>

#include "JniBridgeC.hpp"
#include "LAppDefine.hpp"

using namespace Csm;
using namespace LAppDefine;

double LAppPal::s_currentFrame = 0.0;
double LAppPal::s_lastFrame = 0.0;
double LAppPal::s_deltaTime = 0.0;

csmByte* LAppPal::LoadFileAsBytes(const std::string filePath, csmSizeInt* outSize)
{
    char* buffer = JniBridgeC::LoadFileAsBytesFromJava(filePath.c_str(), outSize);
    if (buffer == nullptr && DebugLogEnable)
    {
        PrintLogLn("[LAppPal] asset not found: %s", filePath.c_str());
    }
    return reinterpret_cast<csmByte*>(buffer);
}

void LAppPal::ReleaseBytes(csmByte* byteData)
{
    delete[] byteData;
}

csmFloat32 LAppPal::GetDeltaTime()
{
    return static_cast<csmFloat32>(s_deltaTime);
}

void LAppPal::UpdateTime()
{
    s_currentFrame = GetSystemTime();
    s_deltaTime = s_currentFrame - s_lastFrame;
    s_lastFrame = s_currentFrame;
}

void LAppPal::PrintLog(const csmChar* format, ...)
{
    va_list args;
    va_start(args, format);
    __android_log_vprint(ANDROID_LOG_DEBUG, "LuminaLive2D", format, args);
    va_end(args);
}

void LAppPal::PrintLogLn(const csmChar* format, ...)
{
    va_list args;
    va_start(args, format);
    __android_log_vprint(ANDROID_LOG_DEBUG, "LuminaLive2D", format, args);
    va_end(args);
}

void LAppPal::PrintMessage(const csmChar* message)
{
    PrintLog("%s", message);
}

void LAppPal::PrintMessageLn(const csmChar* message)
{
    PrintLogLn("%s", message);
}

double LAppPal::GetSystemTime()
{
    struct timespec res;
    clock_gettime(CLOCK_MONOTONIC, &res);
    return static_cast<double>(res.tv_sec) + static_cast<double>(res.tv_nsec) * 1e-9;
}
