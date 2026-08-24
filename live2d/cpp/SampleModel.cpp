/**
 * Single-model wrapper around CubismUserModel.
 *
 * Based on the official sample's CubismUserModelExtend (Minimum demo) plus the
 * expression/motion/hit-test parts of LAppModel, with one important addition:
 * for models whose *.model3.json does not declare Expressions/Motions
 * (e.g. IceGirl) the files are auto-discovered from the model directory.
 */

#include "SampleModel.hpp"

#include <stdlib.h>
#include <string.h>

#include <future>
#include <string>
#include <vector>

#include <CubismDefaultParameterId.hpp>
#include <Id/CubismIdManager.hpp>
#include <Math/CubismMath.hpp>
#include <Model/CubismMoc.hpp>
#include <Motion/CubismExpressionUpdater.hpp>
#include <Motion/CubismExpressionMotion.hpp>
#include <Motion/CubismEyeBlinkUpdater.hpp>
#include <Motion/CubismLookUpdater.hpp>
#include <Motion/CubismMotion.hpp>
#include <Motion/CubismPhysicsUpdater.hpp>
#include <Motion/CubismPoseUpdater.hpp>
#include <Physics/CubismPhysics.hpp>
#include <Rendering/OpenGL/CubismOffscreenManager_OpenGLES2.hpp>
#include <Rendering/OpenGL/CubismRenderer_OpenGLES2.hpp>
#include <Utils/CubismString.hpp>

#include "JniBridgeC.hpp"
#include "LAppDefine.hpp"
#include "LAppPal.hpp"

using namespace Csm;
using namespace DefaultParameterId;
using namespace LAppDefine;

namespace {

/** Sorts asset name lists so discovery order is deterministic. */
int CompareCsmString(const void* a, const void* b)
{
    return strcmp(reinterpret_cast<const csmString*>(a)->GetRawString(),
                  reinterpret_cast<const csmString*>(b)->GetRawString());
}

/** True when `name` ends with `suffix`. */
bool EndsWith(const std::string& name, const std::string& suffix)
{
    return name.size() >= suffix.size() &&
           name.compare(name.size() - suffix.size(), suffix.size(), suffix) == 0;
}

/** Strips a suffix from an asset entry name. */
std::string StripSuffix(const std::string& name, const std::string& suffix)
{
    return name.substr(0, name.size() - suffix.size());
}

} // namespace

SampleModel::SampleModel(const std::string& modelDirectory)
    : CubismUserModel()
    , _modelSetting(nullptr)
    , _modelDirectory(modelDirectory)
    , _textureManager(new LAppTextureManager())
    , _userTimeSeconds(0.0f)
    , _motionUpdated(false)
{
    // Parameter ids for the look updater (drag -> head/eyes).
    _idParamAngleX = CubismFramework::GetIdManager()->GetId(ParamAngleX);
    _idParamAngleY = CubismFramework::GetIdManager()->GetId(ParamAngleY);
    _idParamAngleZ = CubismFramework::GetIdManager()->GetId(ParamAngleZ);
    _idParamBodyAngleX = CubismFramework::GetIdManager()->GetId(ParamBodyAngleX);
    _idParamEyeBallX = CubismFramework::GetIdManager()->GetId(ParamEyeBallX);
    _idParamEyeBallY = CubismFramework::GetIdManager()->GetId(ParamEyeBallY);
}

SampleModel::~SampleModel()
{
    ReleaseModelSetting();
    delete _textureManager;
}

void SampleModel::LoadAssets(const csmChar* modelJsonFileName, csmUint32 renderWidth,
                             csmUint32 renderHeight)
{
    const std::string path = _modelDirectory + modelJsonFileName;

    csmSizeInt size = 0;
    csmByte* buffer = LAppPal::LoadFileAsBytes(path, &size);
    _modelSetting = new CubismModelSettingJson(buffer, size);
    LAppPal::ReleaseBytes(buffer);

    SetupModel(renderWidth, renderHeight);
}

void SampleModel::SetupModel(csmUint32 renderWidth, csmUint32 renderHeight)
{
    _updating = true;
    _initialized = false;

    csmByte* buffer = nullptr;
    csmSizeInt size = 0;

    // --- Cubism model (.moc3) ------------------------------------------------
    if (strcmp(_modelSetting->GetModelFileName(), "") != 0)
    {
        std::string path = _modelDirectory + _modelSetting->GetModelFileName();
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        LoadModel(buffer, size);
        LAppPal::ReleaseBytes(buffer);
    }

    // --- Expressions ----------------------------------------------------------
    // 1) The ones declared in *.model3.json ...
    for (csmInt32 i = 0; i < _modelSetting->GetExpressionCount(); i++)
    {
        const csmString name = _modelSetting->GetExpressionName(i);
        std::string path = _modelDirectory + _modelSetting->GetExpressionFileName(i);

        buffer = LAppPal::LoadFileAsBytes(path, &size);
        ACubismMotion* motion = LoadExpression(buffer, size, name.GetRawString());
        LAppPal::ReleaseBytes(buffer);

        if (motion != nullptr)
        {
            if (_expressions[name] != nullptr)
            {
                ACubismMotion::Delete(_expressions[name]);
            }
            _expressions[name] = motion;
            _expressionNames.PushBack(name);
        }
    }
    // 2) ... plus any *.exp3.json found next to the model (IceGirl keeps its
    //    expressions undeclared in the json).
    DiscoverExpressions();
    if (_expressionNames.GetSize() > 0)
    {
        CubismExpressionUpdater* expression = CSM_NEW CubismExpressionUpdater(*_expressionManager);
        _updateScheduler.AddUpdatableList(expression);
    }

    // --- Pose / physics / user data ---------------------------------------------
    if (strcmp(_modelSetting->GetPoseFileName(), "") != 0)
    {
        std::string path = _modelDirectory + _modelSetting->GetPoseFileName();
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        LoadPose(buffer, size);
        LAppPal::ReleaseBytes(buffer);
    }
    if (_pose != nullptr)
    {
        CubismPoseUpdater* pose = CSM_NEW CubismPoseUpdater(*_pose);
        _updateScheduler.AddUpdatableList(pose);
    }

    if (strcmp(_modelSetting->GetPhysicsFileName(), "") != 0)
    {
        std::string path = _modelDirectory + _modelSetting->GetPhysicsFileName();
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        LoadPhysics(buffer, size);
        LAppPal::ReleaseBytes(buffer);
    }
    if (_physics != nullptr)
    {
        CubismPhysicsUpdater* physics = CSM_NEW CubismPhysicsUpdater(*_physics);
        _updateScheduler.AddUpdatableList(physics);
    }

    if (strcmp(_modelSetting->GetUserDataFile(), "") != 0)
    {
        std::string path = _modelDirectory + _modelSetting->GetUserDataFile();
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        LoadUserData(buffer, size);
        LAppPal::ReleaseBytes(buffer);
    }

    // --- Eye blink ---------------------------------------------------------------
    // Uses the ids from the json when present; falls back to the standard
    // ParamEyeLOpen/ParamEyeROpen when the model has those parameters.
    {
        csmVector<CubismIdHandle> blinkIds;
        for (csmInt32 i = 0; i < _modelSetting->GetEyeBlinkParameterCount(); i++)
        {
            blinkIds.PushBack(_modelSetting->GetEyeBlinkParameterId(i));
        }
        if (blinkIds.GetSize() == 0)
        {
            CubismIdHandle l = CubismFramework::GetIdManager()->GetId(ParamEyeLOpen);
            CubismIdHandle r = CubismFramework::GetIdManager()->GetId(ParamEyeROpen);
            // The framework has no IsExistParameterId(); a parameter "exists"
            // when its index is within the real (non-virtual) parameter range.
            if (_model->GetParameterIndex(l) < _model->GetParameterCount() &&
                _model->GetParameterIndex(r) < _model->GetParameterCount())
            {
                blinkIds.PushBack(l);
                blinkIds.PushBack(r);
            }
        }
        if (blinkIds.GetSize() > 0)
        {
            _eyeBlink = CubismEyeBlink::Create();
            _eyeBlink->SetParameterIds(blinkIds);
            CubismEyeBlinkUpdater* blink = CSM_NEW CubismEyeBlinkUpdater(_motionUpdated, *_eyeBlink);
            _updateScheduler.AddUpdatableList(blink);
        }
    }

    // --- Look (head / eyes follow the drag) ---------------------------------------
    {
        _look = CubismLook::Create();
        csmVector<CubismLook::LookParameterData> lookParameters;
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamAngleX, 30.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamAngleY, 0.0f, 30.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamAngleZ, 0.0f, 0.0f, -30.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamBodyAngleX, 10.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamEyeBallX, 1.0f));
        lookParameters.PushBack(CubismLook::LookParameterData(_idParamEyeBallY, 0.0f, 1.0f));
        _look->SetParameters(lookParameters);

        CubismLookUpdater* look = CSM_NEW CubismLookUpdater(*_look, *_dragManager);
        _updateScheduler.AddUpdatableList(look);
    }

    _updateScheduler.SortUpdatableList();

    // --- Layout from *.model3.json ------------------------------------------------
    csmMap<csmString, csmFloat32> layout;
    _modelSetting->GetLayoutMap(layout);
    _modelMatrix->SetupFromLayout(layout);
    // IceGirl's model3.json has no Layout block, so the canvas stays anchored
    // top-left (Y-down) after SetupFromLayout. Center it on the logical
    // origin once here - per-frame centering would accumulate drift because
    // CubismModelMatrix setters are incremental.
    _modelMatrix->CenterX(0.0f);
    _modelMatrix->CenterY(0.0f);
    _model->SaveParameters();

    // --- Motions -------------------------------------------------------------------
    for (csmInt32 i = 0; i < _modelSetting->GetMotionGroupCount(); i++)
    {
        const csmChar* group = _modelSetting->GetMotionGroupName(i);
        _motionGroupNames.PushBack(csmString(group));
        PreloadMotionGroup(group);
    }
    DiscoverMotions(); // json did not declare any group (IceGirl case)
    _motionManager->StopAllMotions();

    // --- Renderer + textures ---------------------------------------------------------
    CreateRenderer(renderWidth, renderHeight);
    SetupTextures();

    _updating = false;
    _initialized = true;
}

void SampleModel::DiscoverExpressions()
{
    csmVector<csmString> entries = JniBridgeC::GetAssetList(_modelDirectory.c_str());
    qsort(entries.GetPtr(), entries.GetSize(), sizeof(csmString), CompareCsmString);

    for (csmUint32 i = 0; i < entries.GetSize(); i++)
    {
        const std::string entry(entries[i].GetRawString());
        // Skip directories (trailing '/') and files inside sub-directories.
        if (entry.empty() || entry.back() == '/')
        {
            continue;
        }
        // Match "xxx.exp3.json" (plain) or "xxx.exp3.json.enc" (encrypted).
        std::string name;
        std::string asset;
        if (EndsWith(entry, ".exp3.json.enc"))
        {
            asset = StripSuffix(entry, ".enc");
            name = StripSuffix(asset, ".exp3.json");
        }
        else if (EndsWith(entry, ".exp3.json"))
        {
            asset = entry;
            name = StripSuffix(entry, ".exp3.json");
        }
        else
        {
            continue;
        }

        if (_expressions.IsExist(csmString(name.c_str())))
        {
            continue; // already loaded from the json
        }

        csmSizeInt size = 0;
        csmByte* buffer = LAppPal::LoadFileAsBytes(_modelDirectory + asset, &size);
        ACubismMotion* motion = LoadExpression(buffer, size, name.c_str());
        LAppPal::ReleaseBytes(buffer);
        if (motion != nullptr)
        {
            _expressions[csmString(name.c_str())] = motion;
            _expressionNames.PushBack(csmString(name.c_str()));
        }
    }
}

void SampleModel::DiscoverMotions()
{
    csmVector<csmString> entries = JniBridgeC::GetAssetList(_modelDirectory.c_str());
    qsort(entries.GetPtr(), entries.GetSize(), sizeof(csmString), CompareCsmString);

    for (csmUint32 i = 0; i < entries.GetSize(); i++)
    {
        const std::string entry(entries[i].GetRawString());
        if (entry.empty() || entry.back() == '/')
        {
            continue;
        }
        // Match "xxx.motion3.json" / "xxx.motion3.json.enc"; the group name is
        // the file name without the suffix (e.g. "DaiJi", "HuiShou").
        std::string asset;
        std::string groupName;
        if (EndsWith(entry, ".motion3.json.enc"))
        {
            asset = StripSuffix(entry, ".enc");
            groupName = StripSuffix(asset, ".motion3.json");
        }
        else if (EndsWith(entry, ".motion3.json"))
        {
            asset = entry;
            groupName = StripSuffix(entry, ".motion3.json");
        }
        else
        {
            continue;
        }

        // Reuse a group that was already declared in the json.
        bool knownGroup = false;
        for (csmUint32 g = 0; g < _motionGroupNames.GetSize(); g++)
        {
            if (_motionGroupNames[g] == groupName.c_str())
            {
                knownGroup = true;
                break;
            }
        }
        if (!knownGroup)
        {
            _motionGroupNames.PushBack(csmString(groupName.c_str()));
        }

        csmString name = Utils::CubismString::GetFormatedString("%s_0", groupName.c_str());
        if (_motions.IsExist(name))
        {
            continue;
        }
        csmSizeInt size = 0;
        csmByte* buffer = LAppPal::LoadFileAsBytes(_modelDirectory + asset, &size);
        CubismMotion* motion = static_cast<CubismMotion*>(
            LoadMotion(buffer, size, name.GetRawString(), nullptr, nullptr, _modelSetting,
                       groupName.c_str(), 0));
        LAppPal::ReleaseBytes(buffer);
        if (motion != nullptr)
        {
            _motions[name] = motion;
        }
    }
}

void SampleModel::PreloadMotionGroup(const csmChar* group)
{
    const csmInt32 count = _modelSetting->GetMotionCount(group);
    for (csmInt32 i = 0; i < count; i++)
    {
        csmString name = Utils::CubismString::GetFormatedString("%s_%d", group, i);
        std::string path = _modelDirectory + _modelSetting->GetMotionFileName(group, i);

        csmByte* buffer = nullptr;
        csmSizeInt size = 0;
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        CubismMotion* motion = static_cast<CubismMotion*>(
            LoadMotion(buffer, size, name.GetRawString(), nullptr, nullptr, _modelSetting,
                       group, i));
        LAppPal::ReleaseBytes(buffer);

        if (motion != nullptr)
        {
            if (_motions[name] != nullptr)
            {
                ACubismMotion::Delete(_motions[name]);
            }
            _motions[name] = motion;
        }
    }
}

void SampleModel::ReleaseModelSetting()
{
    for (csmMap<csmString, ACubismMotion*>::const_iterator it = _motions.Begin();
         it != _motions.End(); ++it)
    {
        ACubismMotion::Delete(it->Second);
    }
    _motions.Clear();

    for (csmMap<csmString, ACubismMotion*>::const_iterator it = _expressions.Begin();
         it != _expressions.End(); ++it)
    {
        ACubismMotion::Delete(it->Second);
    }
    _expressions.Clear();

    _expressionNames.Clear();
    _motionGroupNames.Clear();

    delete _modelSetting;
    _modelSetting = nullptr;

    Csm::Rendering::CubismOffscreenManager_OpenGLES2::ReleaseInstance();
}

Csm::CubismMotionQueueEntryHandle SampleModel::StartMotion(const csmChar* group,
                                                           csmInt32 no, csmInt32 priority)
{
    if (_modelSetting->GetMotionCount(group) == 0)
    {
        return InvalidMotionQueueEntryHandleValue;
    }

    if (priority == PriorityForce)
    {
        _motionManager->SetReservePriority(priority);
    }
    else if (!_motionManager->ReserveMotion(priority))
    {
        return InvalidMotionQueueEntryHandleValue;
    }

    csmString name = Utils::CubismString::GetFormatedString("%s_%d", group, no);
    CubismMotion* motion = static_cast<CubismMotion*>(_motions[name.GetRawString()]);
    csmBool autoDelete = false;

    if (motion == nullptr)
    {
        // Lazily load when the preload did not include it.
        std::string path = _modelDirectory + _modelSetting->GetMotionFileName(group, no);
        csmByte* buffer = nullptr;
        csmSizeInt size = 0;
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        motion = static_cast<CubismMotion*>(
            LoadMotion(buffer, size, nullptr, nullptr, nullptr, _modelSetting, group, no));
        LAppPal::ReleaseBytes(buffer);
        if (motion != nullptr)
        {
            autoDelete = true; // freed by the motion manager when finished
        }
    }

    return _motionManager->StartMotionPriority(motion, autoDelete, priority);
}

void SampleModel::StartRandomMotion(const csmChar* group)
{
    const csmInt32 count = _modelSetting->GetMotionCount(group);
    if (count == 0)
    {
        LAppPal::PrintLogLn("[Model] no motions in group '%s'", group);
        return;
    }
    StartMotion(group, rand() % count, PriorityNormal);
}

void SampleModel::StartRandomMotionInAnyGroup()
{
    if (_motionGroupNames.GetSize() == 0)
    {
        LAppPal::PrintLogLn("[Model] no motion groups available");
        return;
    }
    const csmUint32 groupIndex = static_cast<csmUint32>(rand()) % _motionGroupNames.GetSize();
    StartRandomMotion(_motionGroupNames[groupIndex].GetRawString());
}

bool SampleModel::HasMotionGroup(const csmChar* group) const
{
    for (csmUint32 i = 0; i < _motionGroupNames.GetSize(); i++)
    {
        if (_motionGroupNames[i] == group)
        {
            return true;
        }
    }
    return false;
}

const csmChar* SampleModel::GetIdleGroupName() const
{
    for (csmUint32 i = 0; i < _motionGroupNames.GetSize(); i++)
    {
        if (_motionGroupNames[i] == MotionGroupIdle)
        {
            return _motionGroupNames[i].GetRawString();
        }
    }
    // Fallback: first available group (IceGirl's "DaiJi" is a standby motion).
    return _motionGroupNames.GetSize() > 0 ? _motionGroupNames[0].GetRawString() : nullptr;
}

csmInt32 SampleModel::GetExpressionCount() const
{
    return static_cast<csmInt32>(_expressionNames.GetSize());
}

const csmChar* SampleModel::GetExpressionName(csmInt32 index) const
{
    if (index < 0 || index >= static_cast<csmInt32>(_expressionNames.GetSize()))
    {
        return nullptr;
    }
    return _expressionNames[static_cast<csmUint32>(index)].GetRawString();
}

void SampleModel::SetExpression(csmInt32 index)
{
    const csmChar* name = GetExpressionName(index);
    if (name == nullptr)
    {
        LAppPal::PrintLogLn("[Model] expression index out of range: %d", index);
        return;
    }

    ACubismMotion* motion = _expressions[csmString(name)];
    if (motion != nullptr)
    {
        LAppPal::PrintLogLn("[Model] expression: %s", name);
        _expressionManager->StartMotion(motion, false);
    }
    else
    {
        LAppPal::PrintLogLn("[Model] expression '%s' not loaded", name);
    }
}

void SampleModel::ClearExpressions()
{
    _expressionManager->StopAllMotions();
    LAppPal::PrintLogLn("[Model] expressions cleared");
}

csmInt32 SampleModel::GetMotionGroupCount() const
{
    return static_cast<csmInt32>(_motionGroupNames.GetSize());
}

const csmChar* SampleModel::GetMotionGroupName(csmInt32 index) const
{
    if (index < 0 || index >= static_cast<csmInt32>(_motionGroupNames.GetSize()))
    {
        return nullptr;
    }
    return _motionGroupNames[static_cast<csmUint32>(index)].GetRawString();
}

void SampleModel::PlayMotionGroup(const csmChar* group)
{
    if (!HasMotionGroup(group))
    {
        LAppPal::PrintLogLn("[Model] motion group '%s' not found", group);
        return;
    }
    StartMotion(group, 0, PriorityForce);
}

void SampleModel::SetRandomExpression()
{
    const csmInt32 count = GetExpressionCount();
    if (count == 0)
    {
        LAppPal::PrintLogLn("[Model] model has no expressions");
        return;
    }
    SetExpression(rand() % count);
}

bool SampleModel::HitTest(const csmChar* hitAreaName, csmFloat32 x, csmFloat32 y)
{
    for (csmInt32 i = 0; i < _modelSetting->GetHitAreasCount(); i++)
    {
        if (strcmp(hitAreaName, _modelSetting->GetHitAreaName(i)) == 0)
        {
            const CubismIdHandle drawId = _modelSetting->GetHitAreaId(i);
            return IsHit(drawId, x, y);
        }
    }
    return false;
}

void SampleModel::SetDragging(csmFloat32 x, csmFloat32 y)
{
    _dragManager->Set(x, y);
}

void SampleModel::Update()
{
    const csmFloat32 deltaTimeSeconds = LAppPal::GetDeltaTime();
    _userTimeSeconds += deltaTimeSeconds;

    _motionUpdated = false;

    // Restore parameters saved at the end of the previous frame.
    _model->LoadParameters();

    if (_motionManager->IsFinished())
    {
        // No motion playing -> start the idle motion (or the fallback group).
        const csmChar* idleGroup = GetIdleGroupName();
        if (idleGroup != nullptr)
        {
            StartMotion(idleGroup, 0, PriorityIdle);
        }
    }
    else
    {
        _motionUpdated = _motionManager->UpdateMotion(_model, deltaTimeSeconds);
    }

    _model->SaveParameters();

    // Eye blink / look / physics / pose / expression updaters.
    _updateScheduler.OnLateUpdate(_model, deltaTimeSeconds);

    // Commit the parameter changes.
    _model->Update();
}

void SampleModel::Draw(Csm::CubismMatrix44& matrix)
{
    if (_model == nullptr)
    {
        return;
    }

    // Begin the offscreen (mask) frame processing.
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->BeginFrameProcess();

    matrix.MultiplyByMatrix(_modelMatrix);
    GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>()->SetMvpMatrix(&matrix);
    GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>()->DrawModel();

    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->EndFrameProcess();
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->ReleaseStaleRenderTextures();
}

void SampleModel::SetupTextures()
{
    // 1) Decode all textures in parallel worker threads. Decryption + PNG
    //    inflate + downscaling are pure CPU work and dominate the model load
    //    time, so they run on several cores at once (std::async).
    std::vector<std::future<LAppTextureManager::DecodedImage>> futures;
    for (csmInt32 i = 0; i < _modelSetting->GetTextureCount(); i++)
    {
        if (strcmp(_modelSetting->GetTextureFileName(i), "") == 0)
        {
            continue;
        }
        const std::string texturePath =
            _modelDirectory + _modelSetting->GetTextureFileName(i);
        futures.push_back(std::async(std::launch::async, [this, texturePath]() {
            return _textureManager->DecodePngFile(texturePath);
        }));
    }

    // 2) Upload the decoded images to the GPU - GL calls must stay on the GL
    //    thread, so this happens here (after the workers have finished).
    csmInt32 modelTextureNumber = 0;
    for (auto& future : futures)
    {
        LAppTextureManager::DecodedImage image = future.get();
        if (image.pixels == nullptr)
        {
            LAppPal::PrintLogLn("[Model] texture load failed: %s", image.fileName.c_str());
            continue;
        }
        LAppTextureManager::TextureInfo* texture =
            _textureManager->CreateTextureFromDecoded(image);
        if (texture != nullptr)
        {
            GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->BindTexture(modelTextureNumber,
                                                                            texture->id);
            modelTextureNumber++;
        }
    }

    GetRenderer<Rendering::CubismRenderer_OpenGLES2>()->IsPremultipliedAlpha(false);
}