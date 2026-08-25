#pragma once

/*
 * Live2DEngine.hpp
 *
 * Native render engine. Owns the Cubism framework, the model, the view
 * matrices and the touch state. Kotlin drives it through Initialize,
 * Resize, Run and the OnTouches calls. Public methods are safe to call from
 * any thread, a mutex guards the model.
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
    /** Like GetInstance but never creates the engine, null when disposed. */
    static Live2DEngine* PeekInstance();
    static void ReleaseInstance();

    // gl surface lifecycle
    void Initialize();                       // onSurfaceCreated
    void Resize(Csm::csmInt32 width, Csm::csmInt32 height); // onSurfaceChanged
    void Run();                              // onDrawFrame

    // touch
    void OnTouchesBegan(Csm::csmFloat32 x, Csm::csmFloat32 y);
    void OnTouchesMoved(Csm::csmFloat32 x, Csm::csmFloat32 y);
    void OnTouchesEnded(Csm::csmFloat32 x, Csm::csmFloat32 y);

    // face expressions
    Csm::csmInt32 GetExpressionCount() const;
    const char* GetExpressionName(Csm::csmInt32 index) const;
    void SetExpression(Csm::csmInt32 index);
    /** Clears all stacked expressions. */
    void ClearExpressions();

    // motion groups
    Csm::csmInt32 GetMotionGroupCount() const;
    const char* GetMotionGroupName(Csm::csmInt32 index) const;
    void PlayMotionGroup(Csm::csmInt32 index);

    SampleModel* GetModel() const { return _model; }

private:
    Live2DEngine();
    ~Live2DEngine();

    Live2DEngine(const Live2DEngine&) = delete;
    Live2DEngine& operator=(const Live2DEngine&) = delete;

    // view and touch plumbing, ported from the Cubism sample
    void SetupViewMatrices();
    Csm::csmFloat32 TransformViewX(Csm::csmFloat32 deviceX) const;
    Csm::csmFloat32 TransformViewY(Csm::csmFloat32 deviceY) const;
    void OnTap(Csm::csmFloat32 x, Csm::csmFloat32 y);

    LAppAllocator _cubismAllocator;          // framework allocator
    Csm::CubismFramework::Option _cubismOption; // framework options

    SampleModel* _model;                     // the loaded model
    Csm::CubismMatrix44* _deviceToScreen;    // device pixels to logical screen
    Csm::CubismViewMatrix* _viewMatrix;      // logical screen to view space

    Csm::csmInt32 _width;                    // surface width in px
    Csm::csmInt32 _height;                   // surface height in px

    // touch state, device coordinates
    bool _touchStarted;
    Csm::csmFloat32 _startX, _startY;        // where the touch began
    Csm::csmFloat32 _lastX, _lastY;          // latest touch position

    mutable std::mutex _mutex;               // guards the model
};