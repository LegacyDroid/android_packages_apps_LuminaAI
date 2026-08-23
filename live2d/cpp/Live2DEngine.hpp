#pragma once

/**
 * @file Live2DEngine.hpp
 *
 * The native render engine: owns the Cubism framework instance, the loaded
 * model, the view matrices (device -> logical screen) and the touch state.
 *
 * The Kotlin side (via JniBridgeJava) drives it through the lifecycle:
 *
 *   Initialize()  - called when the GL surface is created
 *   Resize()      - called when the surface size changes
 *   Run()         - called every frame (render)
 *   OnTouches*()  - touch events
 *   SetExpression / GetExpression* - face expression UI
 *
 * All public methods are thread-safe (a mutex guards the model, since
 * expression calls come from the UI thread while Run() runs on the GL thread).
 */

#include <mutex>

#include <CubismFramework.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Math/CubismViewMatrix.hpp>

#include "LAppAllocator.hpp"
#include "SampleModel.hpp"

class Live2DEngine
{
public:
    static Live2DEngine* GetInstance();
    /** Returns the engine without creating one (null when disposed) - the
     *  Kotlin side uses this to poll model state without instantiating the
     *  engine on the UI thread. */
    static Live2DEngine* PeekInstance();
    static void ReleaseInstance();

    // --- GL surface lifecycle -------------------------------------------------
    void Initialize();                       ///< onSurfaceCreated
    void Resize(Csm::csmInt32 width, Csm::csmInt32 height); ///< onSurfaceChanged
    void Run();                              ///< onDrawFrame

    // --- Touch ------------------------------------------------------------------
    void OnTouchesBegan(Csm::csmFloat32 x, Csm::csmFloat32 y);
    void OnTouchesMoved(Csm::csmFloat32 x, Csm::csmFloat32 y);
    void OnTouchesEnded(Csm::csmFloat32 x, Csm::csmFloat32 y);

    // --- Face expressions ----------------------------------------------------------
    Csm::csmInt32 GetExpressionCount() const;
    const char* GetExpressionName(Csm::csmInt32 index) const;
    void SetExpression(Csm::csmInt32 index);
    /** Stops all Add-blended expressions (the assistant tool path). */
    void ClearExpressions();

    // --- Motion groups (assistant-driven motions) ---------------------------------
    Csm::csmInt32 GetMotionGroupCount() const;
    const char* GetMotionGroupName(Csm::csmInt32 index) const;
    void PlayMotionGroup(Csm::csmInt32 index);

    SampleModel* GetModel() const { return _model; }

private:
    Live2DEngine();
    ~Live2DEngine();

    Live2DEngine(const Live2DEngine&) = delete;
    Live2DEngine& operator=(const Live2DEngine&) = delete;

    // View/touch plumbing (ported from the Cubism sample's LAppView).
    void SetupViewMatrices();
    Csm::csmFloat32 TransformViewX(Csm::csmFloat32 deviceX) const;
    Csm::csmFloat32 TransformViewY(Csm::csmFloat32 deviceY) const;
    void OnTap(Csm::csmFloat32 x, Csm::csmFloat32 y);

    LAppAllocator _cubismAllocator;          ///< framework allocator
    Csm::CubismFramework::Option _cubismOption; ///< framework options (file loader, log)

    SampleModel* _model;                     ///< the loaded Live2D model
    Csm::CubismMatrix44* _deviceToScreen;    ///< device px -> logical screen
    Csm::CubismViewMatrix* _viewMatrix;      ///< logical screen -> view

    Csm::csmInt32 _width;                    ///< surface width [px]
    Csm::csmInt32 _height;                   ///< surface height [px]

    // Touch state (device coordinates).
    bool _touchStarted;
    Csm::csmFloat32 _startX, _startY;        ///< where the touch began
    Csm::csmFloat32 _lastX, _lastY;          ///< latest touch position

    mutable std::mutex _mutex;               ///< guards the model from the UI thread
};