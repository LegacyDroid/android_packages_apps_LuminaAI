/*
 * Single model wrapper around CubismUserModel.
 * Based on the official sample, with the addition that expressions and
 * motions missing from the model json are discovered from the directory.
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

// qsort comparator so discovery order is stable
int CompareCsmString(const void* a, const void* b)
{
    return strcmp(reinterpret_cast<const csmString*>(a)->GetRawString(),
                  reinterpret_cast<const csmString*>(b)->GetRawString());
}

bool EndsWith(const std::string& name, const std::string& suffix)
{
    return name.size() >= suffix.size() &&
           name.compare(name.size() - suffix.size(), suffix.size(), suffix) == 0;
}

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
    // parameter ids the look updater needs
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

    // the moc3 model itself
    if (strcmp(_modelSetting->GetModelFileName(), "") != 0)
    {
        std::string path = _modelDirectory + _modelSetting->GetModelFileName();
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        LoadModel(buffer, size);
        LAppPal::ReleaseBytes(buffer);
    }

    // expressions declared in the model json
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
    // then anything else sitting next to the model, IceGirl declares none
    DiscoverExpressions();
    if (_expressionNames.GetSize() > 0)
    {
        CubismExpressionUpdater* expression = CSM_NEW CubismExpressionUpdater(*_expressionManager);
        _updateScheduler.AddUpdatableList(expression);
    }

    // pose, physics, user data
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

    // eye blink, uses json ids when present and standard eye params otherwise
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
            // no IsExistParameterId in the framework, a parameter exists
            // when its index lands inside the real parameter count
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

    // look, head and eyes follow the drag
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

    // layout values from the model json
    csmMap<csmString, csmFloat32> layout;
    _modelSetting->GetLayoutMap(layout);
    _modelMatrix->SetupFromLayout(layout);
    _model->SaveParameters();

    // preload all motion groups
    for (csmInt32 i = 0; i < _modelSetting->GetMotionGroupCount(); i++)
    {
        const csmChar* group = _modelSetting->GetMotionGroupName(i);
        _motionGroupNames.PushBack(csmString(group));
        PreloadMotionGroup(group);
    }
    DiscoverMotions(); // nothing declared, IceGirl case
    _motionManager->StopAllMotions();

    // renderer and textures
    CreateRenderer(renderWidth, renderHeight);
    SetupTextures();
    MeasureBounds();

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
        // skip directories and anything inside sub-directories
        if (entry.empty() || entry.back() == '/')
        {
            continue;
        }
        // matches exp3 json files, encrypted or not
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
            continue; // already came from the json
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
        // motion3 json files, group name comes from the file name
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

        // reuse a group the json already declared
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
        // not preloaded, load it now
        std::string path = _modelDirectory + _modelSetting->GetMotionFileName(group, no);
        csmByte* buffer = nullptr;
        csmSizeInt size = 0;
        buffer = LAppPal::LoadFileAsBytes(path, &size);
        motion = static_cast<CubismMotion*>(
            LoadMotion(buffer, size, nullptr, nullptr, nullptr, _modelSetting, group, no));
        LAppPal::ReleaseBytes(buffer);
        if (motion != nullptr)
        {
            autoDelete = true; // motion manager frees it when done
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
    // no Idle group, use whatever exists
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

void SampleModel::MeasureBounds()
{
    if (_model == nullptr)
    {
        return;
    }

    csmFloat32 minX = 0.0f, maxX = 0.0f, minY = 0.0f, maxY = 0.0f;
    bool first = true;

    const csmInt32 drawableCount = _model->GetDrawableCount();
    for (csmInt32 i = 0; i < drawableCount; ++i)
    {
        // skip invisible drawables so hidden accessories do not skew the frame
        if (_model->GetDrawableOpacity(i) <= 0.0f)
        {
            continue;
        }
        const csmInt32 count = _model->GetDrawableVertexCount(i);
        const csmFloat32* vertices = _model->GetDrawableVertices(i);
        for (csmInt32 j = 0; j < count; ++j)
        {
            const csmFloat32 x = vertices[Constant::VertexOffset + j * Constant::VertexStep];
            const csmFloat32 y = vertices[Constant::VertexOffset + j * Constant::VertexStep + 1];
            if (first)
            {
                minX = maxX = x;
                minY = maxY = y;
                first = false;
            }
            else
            {
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
    }
    if (first)
    {
        return;
    }

    _boundsCenterX = (minX + maxX) * 0.5f;
    _boundsCenterY = (minY + maxY) * 0.5f;
    _boundsHalfWidth = (maxX - minX) * 0.5f;
    _boundsHalfHeight = (maxY - minY) * 0.5f;

    LAppPal::PrintLogLn(
        "[Model] measured bounds: x[%.3f..%.3f] y[%.3f..%.3f] center=(%.3f,%.3f) half=(%.3f,%.3f)",
        minX, maxX, minY, maxY, _boundsCenterX, _boundsCenterY,
        _boundsHalfWidth, _boundsHalfHeight);
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

    // restore what we saved last frame
    _model->LoadParameters();

    if (_motionManager->IsFinished())
    {
        // nothing playing, start the idle motion
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

    // blink, look, physics, pose and expression updaters
    _updateScheduler.OnLateUpdate(_model, deltaTimeSeconds);

    // apply the parameter changes
    _model->Update();
}

void SampleModel::Draw(Csm::CubismMatrix44& matrix)
{
    if (_model == nullptr)
    {
        return;
    }

    // offscreen mask processing for this frame
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->BeginFrameProcess();

    matrix.MultiplyByMatrix(_modelMatrix);
    GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>()->SetMvpMatrix(&matrix);
    GetRenderer<Csm::Rendering::CubismRenderer_OpenGLES2>()->DrawModel();

    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->EndFrameProcess();
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->ReleaseStaleRenderTextures();
}

void SampleModel::SetupTextures()
{
    // decode every texture on worker threads first, that is the slow part
    // and parallelizes well
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

    // then upload on this thread, GL calls must stay here
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