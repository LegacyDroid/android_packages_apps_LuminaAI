/**
 * Native render engine - see Live2DEngine.hpp for the big picture.
 *
 * The flow mirrors the official Cubism Android sample (LAppDelegate /
 * LAppView / LAppLive2DManager), but with a single model and the model
 * resources delivered encrypted through LAppPal.
 */

#include "Live2DEngine.hpp"

#include <math.h>
#include <string.h>

#include <GLES2/gl2.h>

#include <Math/CubismMath.hpp>
#include <Rendering/OpenGL/CubismShader_OpenGLES2.hpp>

#include "LAppDefine.hpp"
#include "LAppPal.hpp"

using namespace Csm;
using namespace LAppDefine;

namespace {
Live2DEngine* s_instance = nullptr;

// Model location inside the APK assets. This engine is purpose-built for the
// bundled IceGirl model (free Booth download by TianYeLuLu), so the names are
// hardcoded instead of injected via build flags.
const csmChar* kModelDirectory = "";
const csmChar* kModelJsonName = "IceGirl.model3.json";

/** Uniform zoom applied after the fit - makes IceGirl 25% larger than the
 *  plain fit-to-screen (user preference for this model). */
const csmFloat32 kAvatarZoom = 1.25f;

/** Normalized canvas extents reported by the moc (GetCanvasWidth/Height).
 *  Drawable vertices are centered around the origin in these units. */
const csmFloat32 kAvatarNormalizedWidth = 1.0f;
const csmFloat32 kAvatarNormalizedHeight = 1.4f;

/** Distance in device px below which a touch counts as a tap. */
const csmFloat32 kTapThreshold = 20.0f;
} // namespace

Live2DEngine* Live2DEngine::GetInstance()
{
    if (s_instance == nullptr)
    {
        s_instance = new Live2DEngine();
    }
    return s_instance;
}

Live2DEngine* Live2DEngine::PeekInstance()
{
    return s_instance;
}

void Live2DEngine::ReleaseInstance()
{
    if (s_instance != nullptr)
    {
        delete s_instance;
        s_instance = nullptr;
    }
}

Live2DEngine::Live2DEngine()
    : _cubismAllocator()
    , _cubismOption()
    , _model(nullptr)
    , _deviceToScreen(new CubismMatrix44())
    , _viewMatrix(new CubismViewMatrix())
    , _width(1080)
    , _height(1920)
    , _touchStarted(false)
    , _startX(0.0f)
    , _startY(0.0f)
    , _lastX(0.0f)
    , _lastY(0.0f)
{
    // Set up the Cubism framework: logging + file loading through LAppPal
    // (which transparently decrypts the AES-128-CTR encrypted model assets).
    _cubismOption.LogFunction = LAppPal::PrintMessageLn;
    _cubismOption.LoggingLevel = LAppDefine::CubismLoggingLevel;
    _cubismOption.LoadFileFunction = LAppPal::LoadFileAsBytes;
    _cubismOption.ReleaseBytesFunction = LAppPal::ReleaseBytes;

    CubismFramework::CleanUp();
    CubismFramework::StartUp(&_cubismAllocator, &_cubismOption);
}

Live2DEngine::~Live2DEngine()
{
    std::lock_guard<std::mutex> lock(_mutex);

    delete _model;
    _model = nullptr;

    CubismFramework::Dispose();
    delete _viewMatrix;
    delete _deviceToScreen;
}

void Live2DEngine::Initialize()
{
    std::lock_guard<std::mutex> lock(_mutex);

    // Texture sampling + blending (model textures are premultiplied by the
    // renderer; the standard sample blend setup is used here).
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

    LAppPal::UpdateTime();

    if (!CubismFramework::IsInitialized())
    {
        CubismFramework::Initialize();
    }

    // The GL context is fresh: invalidate shader programs from the previous
    // context and reload them on the next draw.
    Live2D::Cubism::Framework::Rendering::CubismShader_OpenGLES2::GetInstance()
        ->ReleaseInvalidShaderProgram();
    Live2D::Cubism::Framework::Rendering::CubismShader_OpenGLES2::DeleteInstance();

    // (Re)load the model.
    delete _model;
    _model = new SampleModel(kModelDirectory);
    const double loadStart = LAppPal::GetSystemTime();
    _model->LoadAssets(kModelJsonName, static_cast<csmUint32>(_width),
                       static_cast<csmUint32>(_height));

    LAppPal::PrintLogLn("[Engine] model loaded in %.2fs: %s%s",
                        LAppPal::GetSystemTime() - loadStart, kModelDirectory, kModelJsonName);
}

void Live2DEngine::Resize(csmInt32 width, csmInt32 height)
{
    std::lock_guard<std::mutex> lock(_mutex);

    _width = width;
    _height = height;
    glViewport(0, 0, width, height);
    SetupViewMatrices();
}

void Live2DEngine::SetupViewMatrices()
{
    if (_width == 0 || _height == 0)
    {
        return;
    }

    // Map the device screen onto the logical screen (height is the reference).
    const csmFloat32 ratio = static_cast<csmFloat32>(_width) / static_cast<csmFloat32>(_height);
    const csmFloat32 left = -ratio;
    const csmFloat32 right = ratio;
    const csmFloat32 bottom = ViewLogicalLeft;
    const csmFloat32 top = ViewLogicalRight;

    _viewMatrix->SetScreenRect(left, right, bottom, top);
    _viewMatrix->Scale(ViewScale, ViewScale);

    _deviceToScreen->LoadIdentity();
    if (_width > _height)
    {
        const csmFloat32 screenW = CubismMath::AbsF(right - left);
        _deviceToScreen->ScaleRelative(screenW / _width, -screenW / _width);
    }
    else
    {
        const csmFloat32 screenH = CubismMath::AbsF(top - bottom);
        _deviceToScreen->ScaleRelative(screenH / _height, -screenH / _height);
    }
    _deviceToScreen->TranslateRelative(-_width * 0.5f, -_height * 0.5f);

    _viewMatrix->SetMaxScale(ViewMaxScale);
    _viewMatrix->SetMinScale(ViewMinScale);
    _viewMatrix->SetMaxScreenRect(ViewLogicalMaxLeft, ViewLogicalMaxRight,
                                  ViewLogicalMaxBottom, ViewLogicalMaxTop);
}

csmFloat32 Live2DEngine::TransformViewX(csmFloat32 deviceX) const
{
    // device px -> logical screen -> view space
    const csmFloat32 screenX = _deviceToScreen->TransformX(deviceX);
    return _viewMatrix->InvertTransformX(screenX);
}

csmFloat32 Live2DEngine::TransformViewY(csmFloat32 deviceY) const
{
    const csmFloat32 screenY = _deviceToScreen->TransformY(deviceY);
    return _viewMatrix->InvertTransformY(screenY);
}

void Live2DEngine::Run()
{
    std::lock_guard<std::mutex> lock(_mutex);

    LAppPal::UpdateTime();

    // Fully transparent clear: the TextureView composites this surface over
    // the dimmed app, so empty pixels must have alpha 0.
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glClearDepthf(1.0f);

    if (_model == nullptr || _model->GetModel() == nullptr)
    {
        return;
    }

    // Fully deterministic per-frame transform. This model's drawable
    // vertices are already centered around the origin in normalized units
    // (the canvas reports 1.0x1.4), so correct placement is a pure uniform
    // fit-scale with ZERO translation - any canvas-dimension offset shoves
    // her off-screen. Positive Y scale keeps triangle winding intact.
    const csmFloat32 aspectRatio =
        static_cast<csmFloat32>(_width) / static_cast<csmFloat32>(_height);
    const csmFloat32 fitWidth = (2.0f * aspectRatio) / kAvatarNormalizedWidth;
    const csmFloat32 fitHeight = 2.0f / kAvatarNormalizedHeight;
    csmFloat32 z = fitWidth < fitHeight ? fitWidth : fitHeight;
    z *= kAvatarZoom;

    // Identity projection: the model matrix above carries the whole
    // canvas -> logical transform.
    CubismMatrix44 projection;

    CubismModelMatrix* modelMatrix = _model->GetModelMatrix();
    modelMatrix->LoadIdentity();
    // Vertices span [0..W]x[0..H] with Y up and feet at the origin: shift by
    // half the fitted size so the model centers on the logical origin.
    modelMatrix->Scale(z, z);
    modelMatrix->TranslateRelative(-z * kAvatarNormalizedWidth * 0.5f,
                                   -z * kAvatarNormalizedHeight * 0.5f);

    projection.MultiplyByMatrix(_viewMatrix);

    _model->Update();
    _model->Draw(projection);
}

void Live2DEngine::OnTouchesBegan(csmFloat32 x, csmFloat32 y)
{
    std::lock_guard<std::mutex> lock(_mutex);

    _startX = x;
    _startY = y;
    _lastX = x;
    _lastY = y;
    _touchStarted = true;
}

void Live2DEngine::OnTouchesMoved(csmFloat32 x, csmFloat32 y)
{
    std::lock_guard<std::mutex> lock(_mutex);

    if (!_touchStarted || _model == nullptr)
    {
        return;
    }

    // Convert the previous touch position into view space and feed the drag
    // manager (drives ParamAngleX/Y/Z so the head follows the finger).
    const csmFloat32 viewX = TransformViewX(_lastX);
    const csmFloat32 viewY = TransformViewY(_lastY);
    _lastX = x;
    _lastY = y;
    _model->SetDragging(viewX, viewY);
}

void Live2DEngine::OnTouchesEnded(csmFloat32 x, csmFloat32 y)
{
    std::lock_guard<std::mutex> lock(_mutex);

    _lastX = x;
    _lastY = y;
    _touchStarted = false;

    if (_model == nullptr)
    {
        return;
    }

    // Stop dragging.
    _model->SetDragging(0.0f, 0.0f);

    // A short touch with little movement counts as a tap.
    const csmFloat32 dx = _lastX - _startX;
    const csmFloat32 dy = _lastY - _startY;
    if (sqrtf(dx * dx + dy * dy) < kTapThreshold)
    {
        // Convert to logical coordinates and run the hit test.
        const csmFloat32 logicalX = _deviceToScreen->TransformX(_lastX);
        const csmFloat32 logicalY = _deviceToScreen->TransformY(_lastY);
        OnTap(logicalX, logicalY);
    }
}

void Live2DEngine::OnTap(csmFloat32 x, csmFloat32 y)
{
    // Inverse of the per-frame model matrix (pure uniform scale):
    //   view.x = z*vx  ->  vx = x/z
    //   view.y = z*vy  ->  vy = y/z
    const csmFloat32 aspectRatio =
        static_cast<csmFloat32>(_width) / static_cast<csmFloat32>(_height);
    const csmFloat32 fitWidth = (2.0f * aspectRatio) / kAvatarNormalizedWidth;
    const csmFloat32 fitHeight = 2.0f / kAvatarNormalizedHeight;
    csmFloat32 z = fitWidth < fitHeight ? fitWidth : fitHeight;
    z *= kAvatarZoom;

    // Inverse of the per-frame model matrix (uniform scale + half-size
    // centering translate):
    //   view.x = z*(vx - W/2)  ->  vx = x/z + W/2
    //   view.y = z*(vy - H/2)  ->  vy = y/z + H/2
    const csmFloat32 adjustedX = x / z + kAvatarNormalizedWidth * 0.5f;
    const csmFloat32 adjustedY = y / z + kAvatarNormalizedHeight * 0.5f;

    if (_model->HitTest(HitAreaNameHead, adjustedX, adjustedY))
    {
        LAppPal::PrintLogLn("[Engine] hit: %s -> random expression", HitAreaNameHead);
        _model->SetRandomExpression();
    }
    else if (_model->HitTest(HitAreaNameBody, adjustedX, adjustedY))
    {
        LAppPal::PrintLogLn("[Engine] hit: %s -> random motion", HitAreaNameBody);
        if (_model->HasMotionGroup(MotionGroupTapBody))
        {
            _model->StartRandomMotion(MotionGroupTapBody);
        }
        else
        {
            // Models without a "TapBody" group (IceGirl): play any motion.
            _model->StartRandomMotionInAnyGroup();
        }
    }
}

csmInt32 Live2DEngine::GetExpressionCount() const
{
    std::lock_guard<std::mutex> lock(_mutex);
    return _model != nullptr ? _model->GetExpressionCount() : 0;
}

const char* Live2DEngine::GetExpressionName(csmInt32 index) const
{
    std::lock_guard<std::mutex> lock(_mutex);
    return _model != nullptr ? _model->GetExpressionName(index) : nullptr;
}

void Live2DEngine::SetExpression(csmInt32 index)
{
    std::lock_guard<std::mutex> lock(_mutex);
    if (_model != nullptr)
    {
        _model->SetExpression(index);
    }
}

void Live2DEngine::ClearExpressions()
{
    std::lock_guard<std::mutex> lock(_mutex);
    if (_model != nullptr)
    {
        _model->ClearExpressions();
    }
}

csmInt32 Live2DEngine::GetMotionGroupCount() const
{
    std::lock_guard<std::mutex> lock(_mutex);
    return _model != nullptr ? _model->GetMotionGroupCount() : 0;
}

const char* Live2DEngine::GetMotionGroupName(csmInt32 index) const
{
    std::lock_guard<std::mutex> lock(_mutex);
    return _model != nullptr ? _model->GetMotionGroupName(index) : nullptr;
}

void Live2DEngine::PlayMotionGroup(csmInt32 index)
{
    std::lock_guard<std::mutex> lock(_mutex);
    if (_model != nullptr)
    {
        const char* group = _model->GetMotionGroupName(index);
        if (group != nullptr)
        {
            _model->PlayMotionGroup(group);
        }
    }
}