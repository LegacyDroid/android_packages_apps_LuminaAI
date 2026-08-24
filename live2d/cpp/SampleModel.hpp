#pragma once

/**
 * @file SampleModel.hpp
 *
 * Wraps one Live2D model (CubismUserModel) and provides everything the app
 * needs:
 *
 *  - loads the model + textures + physics + pose + userdata (all through
 *    LAppPal, so encrypted assets are handled transparently)
 *  - loads face expressions and motions; when the model's *.model3.json does
 *    not declare them (like IceGirl), they are auto-discovered from the
 *    model directory (*.exp3.json / *.motion3.json files)
 *  - per-frame update (idle motion loop, eye blink, expression/look/physics
 *    updaters) and draw
 *  - hit testing (head -> expression, body -> motion) and drag (ParamAngle*)
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

    /**
     * Loads everything: moc3, textures, expressions, motions, physics, pose.
     * @param modelJsonFileName e.g. "IceGirl.model3.json" (relative to the model dir)
     * @param renderWidth / renderHeight in px (used for the renderer size)
     */
    void LoadAssets(const Csm::csmChar* modelJsonFileName, Csm::csmUint32 renderWidth,
                    Csm::csmUint32 renderHeight);

    /** Advances the model one frame (motion, updaters) - call before Draw(). */

    /**
     * Measures the real drawable-vertex bounds after load. The transform in
     * Live2DEngine derives from these measured values instead of assuming a
     * coordinate convention.
     */
    void MeasureBounds();

    /** Bounds center / half-extents (see MeasureBounds). */
    Csm::csmFloat32 GetBoundsCenterX() const { return _boundsCenterX; }
    Csm::csmFloat32 GetBoundsCenterY() const { return _boundsCenterY; }
    Csm::csmFloat32 GetBoundsHalfWidth() const { return _boundsHalfWidth; }
    Csm::csmFloat32 GetBoundsHalfHeight() const { return _boundsHalfHeight; }

    void Update();

    /** Draws the model with the given projection matrix. */
    void Draw(Csm::CubismMatrix44& matrix);

    // --- Motions -------------------------------------------------------------
    /** Starts the motion `group`/`no` with the given priority. */
    Csm::CubismMotionQueueEntryHandle StartMotion(const Csm::csmChar* group, Csm::csmInt32 no,
                                                  Csm::csmInt32 priority);

    /** Starts a random motion of the group (PriorityNormal). */
    void StartRandomMotion(const Csm::csmChar* group);

    /** Starts a random motion from any available group (PriorityNormal). */
    void StartRandomMotionInAnyGroup();

    /** True when the group exists in the model (json or discovered). */
    bool HasMotionGroup(const Csm::csmChar* group) const;

    /**
     * Group name used for the automatic idle loop: "Idle" when the model has
     * one, otherwise the first available group (e.g. "DaiJi" for IceGirl).
     */
    const Csm::csmChar* GetIdleGroupName() const;

    // --- Expressions ----------------------------------------------------------
    Csm::csmInt32 GetExpressionCount() const;
    const Csm::csmChar* GetExpressionName(Csm::csmInt32 index) const;
    void SetExpression(Csm::csmInt32 index);          ///< starts the expression by index
    void SetRandomExpression();                       ///< starts a random expression
    /** Stops all expressions (expressions are Add-blended and would stack). */
    void ClearExpressions();

    // --- Motion groups (for the assistant-driven motion tools) ------------------
    Csm::csmInt32 GetMotionGroupCount() const;
    const Csm::csmChar* GetMotionGroupName(Csm::csmInt32 index) const;
    /** Plays the first motion of the named group at force priority. */
    void PlayMotionGroup(const Csm::csmChar* group);

    // --- Interaction -----------------------------------------------------------
    /** True when the (model-space) point hits the named hit area. */
    bool HitTest(const Csm::csmChar* hitAreaName, Csm::csmFloat32 x, Csm::csmFloat32 y);

    /** Feeds the drag manager (drives ParamAngleX/Y/Z via the look updater). */
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

    // Auto-discovery helpers (for models without Expressions/Motions in the json).
    void DiscoverExpressions();
    void DiscoverMotions();

    Csm::ICubismModelSetting* _modelSetting;  ///< parsed *.model3.json
    std::string _modelDirectory;              ///< asset prefix, e.g. "" or "Haru/"
    LAppTextureManager* _textureManager;      ///< PNG -> GL texture cache

    // Loaded motion/expression assets. These live here (not in CubismUserModel)
    // exactly like the official sample's LAppModel, keyed by group / name.
    Csm::csmMap<Csm::csmString, Csm::ACubismMotion*> _motions;     ///< motions per group
    Csm::csmMap<Csm::csmString, Csm::ACubismMotion*> _expressions; ///< expressions by name

    // Ordered expression names (json order first, then discovered ones) - the
    // app UI cycles through this list.
    Csm::csmVector<Csm::csmString> _expressionNames;

    // Ordered motion group names (json order first, then discovered ones).
    Csm::csmVector<Csm::csmString> _motionGroupNames;

    // Parameter ids used by the look updater (drag -> head/eyes). Fetched once
    // in the constructor, like the official sample's LAppModel.
    Csm::CubismIdHandle _idParamAngleX;
    Csm::CubismIdHandle _idParamAngleY;
    Csm::CubismIdHandle _idParamAngleZ;
    Csm::CubismIdHandle _idParamBodyAngleX;
    Csm::CubismIdHandle _idParamEyeBallX;
    Csm::CubismIdHandle _idParamEyeBallY;

    Csm::csmFloat32 _userTimeSeconds; ///< accumulated model time [s]
    Csm::csmBool _motionUpdated;      ///< true when the last frame updated a motion
};