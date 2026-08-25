/*
 * JNI bridge between the native engine and Live2DBridge on the Kotlin side.
 * Caches method ids in JNI_OnLoad and forwards lifecycle, rendering and
 * touch events over to Live2DEngine.
 */

#include "JniBridgeC.hpp"

#include <jni.h>
#include <string>

#include "Live2DEngine.hpp"

using namespace Csm;

static JavaVM* g_JVM; // valid for the whole process, cache it
static jclass g_JniBridgeJavaClass;
static jmethodID g_GetAssetListMethodId;
static jmethodID g_LoadFileMethodId;

// set when we attached the current thread ourselves
static thread_local bool g_attachedByUs = false;

// JNIEnv of the calling thread, attaches it to the JVM if needed
static JNIEnv* GetEnv()
{
    JNIEnv* env = NULL;
    if (g_JVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK)
    {
        return env;
    }
    if (g_JVM->AttachCurrentThread(&env, NULL) == JNI_OK)
    {
        g_attachedByUs = true;
        return env;
    }
    return NULL;
}

// called by the VM when the .so loads
jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    g_JVM = vm;

    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK)
    {
        return JNI_ERR;
    }

    // cache the bridge class and its static methods
    jclass clazz = env->FindClass("com/legacydroid/luminaai/live2d/Live2DBridge");
    if (clazz == nullptr)
    {
        return JNI_ERR;
    }
    g_JniBridgeJavaClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    g_GetAssetListMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "getAssetList", "(Ljava/lang/String;)[Ljava/lang/String;");
    g_LoadFileMethodId = env->GetStaticMethodID(g_JniBridgeJavaClass, "loadFile", "(Ljava/lang/String;)[B");
    env->DeleteLocalRef(clazz);

    return JNI_VERSION_1_6;
}

void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved)
{
    JNIEnv* env = GetEnv();
    if (env != nullptr)
    {
        env->DeleteGlobalRef(g_JniBridgeJavaClass);
    }
    Live2DEngine::ReleaseInstance();
}

void JniBridgeC::DetachThreadEnv()
{
    if (g_attachedByUs)
    {
        g_attachedByUs = false;
        g_JVM->DetachCurrentThread();
    }
}

Csm::csmVector<Csm::csmString> JniBridgeC::GetAssetList(const Csm::csmString& path)
{
    Csm::csmVector<Csm::csmString> list;
    JNIEnv* env = GetEnv();
    if (env == nullptr)
    {
        return list;
    }

    jobjectArray obj = reinterpret_cast<jobjectArray>(
        env->CallStaticObjectMethod(g_JniBridgeJavaClass, g_GetAssetListMethodId,
                                    env->NewStringUTF(path.GetRawString())));
    if (obj == nullptr)
    {
        return list;
    }

    const jsize size = env->GetArrayLength(obj);
    for (jsize i = 0; i < size; i++)
    {
        jstring jstr = reinterpret_cast<jstring>(env->GetObjectArrayElement(obj, i));
        if (jstr == nullptr)
        {
            continue;
        }
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        if (chars != nullptr)
        {
            list.PushBack(Csm::csmString(chars));
            env->ReleaseStringUTFChars(jstr, chars);
        }
        env->DeleteLocalRef(jstr);
    }
    env->DeleteLocalRef(obj);
    return list;
}

char* JniBridgeC::LoadFileAsBytesFromJava(const char* filePath, Csm::csmSizeInt* outSize)
{
    JNIEnv* env = GetEnv();
    if (env == nullptr)
    {
        return nullptr;
    }

    jbyteArray obj = reinterpret_cast<jbyteArray>(
        env->CallStaticObjectMethod(g_JniBridgeJavaClass, g_LoadFileMethodId,
                                    env->NewStringUTF(filePath)));
    if (obj == nullptr)
    {
        return nullptr; // Kotlin returns null when the asset is missing
    }

    *outSize = static_cast<Csm::csmSizeInt>(env->GetArrayLength(obj));
    char* buffer = new char[*outSize];
    env->GetByteArrayRegion(obj, 0, static_cast<jsize>(*outSize), reinterpret_cast<jbyte*>(buffer));
    env->DeleteLocalRef(obj);
    return buffer;
}

// native methods called from Live2DBridge

extern "C"
{
    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnSurfaceCreated(JNIEnv* env, jclass type)
    {
        Live2DEngine::GetInstance()->Initialize();
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnSurfaceChanged(JNIEnv* env, jclass type, jint width, jint height)
    {
        Live2DEngine::GetInstance()->Resize(width, height);
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnDrawFrame(JNIEnv* env, jclass type)
    {
        Live2DEngine::GetInstance()->Run();
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnTouchesBegan(JNIEnv* env, jclass type, jfloat pointX, jfloat pointY)
    {
        Live2DEngine::GetInstance()->OnTouchesBegan(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnTouchesMoved(JNIEnv* env, jclass type, jfloat pointX, jfloat pointY)
    {
        Live2DEngine::GetInstance()->OnTouchesMoved(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnTouchesEnded(JNIEnv* env, jclass type, jfloat pointX, jfloat pointY)
    {
        Live2DEngine::GetInstance()->OnTouchesEnded(pointX, pointY);
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeOnStop(JNIEnv* env, jclass type)
    {
        // surface gone, drop the engine with its GL resources
        Live2DEngine::ReleaseInstance();
    }

    JNIEXPORT jboolean JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeIsModelLoaded(JNIEnv* env, jclass type)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        return (engine != nullptr && engine->GetModel() != nullptr &&
                engine->GetModel()->GetModel() != nullptr) ? JNI_TRUE : JNI_FALSE;
    }

    JNIEXPORT jint JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeGetExpressionCount(JNIEnv* env, jclass type)
    {
        // PeekInstance, not GetInstance. This is polled from the UI thread
        // while the GL thread is still loading. Creating the engine here
        // would init it without a surface and the model would never load.
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return 0;
        }
        return engine->GetExpressionCount();
    }

    JNIEXPORT jstring JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeGetExpressionName(JNIEnv* env, jclass type, jint index)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return nullptr;
        }
        const char* name = engine->GetExpressionName(index);
        return name != nullptr ? env->NewStringUTF(name) : nullptr;
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeSetExpression(JNIEnv* env, jclass type, jint index)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return;
        }
        engine->SetExpression(index);
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeClearExpressions(JNIEnv* env, jclass type)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return;
        }
        engine->ClearExpressions();
    }

    JNIEXPORT jint JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeGetMotionGroupCount(JNIEnv* env, jclass type)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return 0;
        }
        return engine->GetMotionGroupCount();
    }

    JNIEXPORT jstring JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativeGetMotionGroupName(JNIEnv* env, jclass type, jint index)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return nullptr;
        }
        const char* name = engine->GetMotionGroupName(index);
        return name != nullptr ? env->NewStringUTF(name) : nullptr;
    }

    JNIEXPORT void JNICALL
    Java_com_legacydroid_luminaai_live2d_Live2DBridge_nativePlayMotionGroup(JNIEnv* env, jclass type, jint index)
    {
        Live2DEngine* engine = Live2DEngine::PeekInstance();
        if (engine == nullptr || engine->GetModel() == nullptr)
        {
            return;
        }
        engine->PlayMotionGroup(index);
    }
} // extern "C"