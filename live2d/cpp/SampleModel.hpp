#pragma once

/*
 * SampleModel.hpp
 *
 * Wraps one Live2D model. Loads moc3, textures, physics, pose, expressions
 * and motions. When the model json does not declare expressions or motions,
 * they are discovered from the model directory instead. Also handles the
 * per frame update, drawing, hit tests and dragging.
 */

#include <string>

#include <CubismFramework.hpp>
#include <CubismModelSettingJson.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Math/CubismModelMatrix.hpp>
#include <Model/CubismUserModel.hpp>
#include <Motion/ACubismMotion.hpp>
#include <Motion/CubismMotionQueueManager.hpp>
#include <Type/csmVector.hpp>
#include <Type/csmMap.hpp>

#include "LAppTextureManager.hpp"

class SampleModel : public Csm::CubismUserModel
{
public:
    explicit SampleModel(const std::string& modelDirectory);
    ~SampleModel() override;

    SampleModel(const SampleModel&) = delete;
    SampleModel& operator=(const SampleModel&) = delete;

    /** Loads moc3, textures, expressions, motions, physics and pose. */
    void LoadAssets(const Csm::csmChar* modelJsonFileName, Csm::csmUint32 renderWidth,
                    Csm::csmUint32 renderHeight);

    /**
     * Measures the real drawable vertex bounds after load. The engine fits
     * the model using these instead of assuming a coordinate convention.
     */
    void MeasureBounds();

    /** Measured bounds, see MeasureBounds. */
    Csm::csmFloat32 GetBoundsCenterX() const { return _boundsCenterX; }
    Csm::csmFloat32 GetBoundsCenterY() const { return _boundsCenterY; }
    Csm::csmFloat32 GetBoundsHalfWidth() const { return _boundsHalfWidth; }
    Csm::csmFloat32 GetBoundsHalfHeight() const { return _boundsHalfHeight; }

    void Update();

    /** Draws the model with the given projection matrix. */
    void Draw(Csm::CubismMatrix44& matrix);

    // motions
    /** Starts a motion from a group with the given priority. */
    Csm::CubismMotionQueueEntryHandle StartMotion(const Csm::csmChar* group, Csm::csmInt32 no,
                                                  Csm::csmInt32 priority);

    /** Random motion of the given group. */
    void StartRandomMotion(const Csm::csmChar* group);

    /** Random motion from any available group. */
    void StartRandomMotionInAnyGroup();

    /** True when the group exists, declared or discovered. */
    bool HasMotionGroup(const Csm::csmChar* group) const;

    /**
     * Group used for the idle loop, prefers Idle and falls back to the
     * first available group.
     */
    const Csm::csmChar* GetIdleGroupName() const;

    // expressions
    Csm::csmInt32 GetExpressionCount() const;
    const Csm::csmChar* GetExpressionName(Csm::csmInt32 index) const;
    void SetExpression(Csm::csmInt32 index);          // apply by index
    void SetRandomExpression();                       // apply a random one
    /** Clears all expressions, they stack otherwise. */
    void ClearExpressions();

    // motion groups
    Csm::csmInt32 GetMotionGroupCount() const;
    const Csm::csmChar* GetMotionGroupName(Csm::csmInt32 index) const;
    /** Plays the first motion of the group at force priority. */
    void PlayMotionGroup(const Csm::csmChar* group);

    // interaction
    /** True when the point hits the named hit area. */
    bool HitTest(const Csm::csmChar* hitAreaName, Csm::csmFloat32 x, Csm::csmFloat32 y);

    /** Feeds the drag manager so head and eyes follow. */
    void SetDragging(Csm::csmFloat32 x, Csm::csmFloat32 y);

private:
    Csm::csmFloat32 _boundsCenterX = 0.0f;
    Csm::csmFloat32 _boundsCenterY = 0.0f;
    Csm::csmFloat32 _boundsHalfWidth = 0.5f;
    Csm::csmFloat32 _boundsHalfHeight = 0.7f;
    void SetupModel(Csm::csmUint32 renderWidth, Csm::csmUint32 renderHeight);
    void SetupTextures();
    void PreloadMotionGroup(const Csm::csmChar* group);
    void ReleaseModelSetting();

    // discovery helpers for models that declare nothing in the json
    void DiscoverExpressions();
    void DiscoverMotions();

    Csm::ICubismModelSetting* _modelSetting;  // parsed model json
    std::string _modelDirectory;              // asset prefix
    LAppTextureManager* _textureManager;      // png to GL texture cache

    // loaded motion and expression assets, same as the official sample
    Csm::csmMap<Csm::csmString, Csm::ACubismMotion*> _motions;     // per group
    Csm::csmMap<Csm::csmString, Csm::ACubismMotion*> _expressions; // by name

    // expression names in order, the UI cycles through these
    Csm::csmVector<Csm::csmString> _expressionNames;

    // motion group names in order
    Csm::csmVector<Csm::csmString> _motionGroupNames;

    // parameter ids used by the look updater, fetched once at construction
    Csm::CubismIdHandle _idParamAngleX;
    Csm::CubismIdHandle _idParamAngleY;
    Csm::CubismIdHandle _idParamAngleZ;
    Csm::CubismIdHandle _idParamBodyAngleX;
    Csm::CubismIdHandle _idParamEyeBallX;
    Csm::CubismIdHandle _idParamEyeBallY;

    Csm::csmFloat32 _userTimeSeconds; // accumulated model time
    Csm::csmBool _motionUpdated;      // last frame updated a motion
};