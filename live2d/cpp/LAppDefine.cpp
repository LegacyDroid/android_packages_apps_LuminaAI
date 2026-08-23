#include "LAppDefine.hpp"

namespace LAppDefine {

const csmFloat32 ViewScale = 1.0f;
const csmFloat32 ViewMaxScale = 2.0f;
const csmFloat32 ViewMinScale = 0.8f;

const csmFloat32 ViewLogicalLeft = -1.0f;
const csmFloat32 ViewLogicalRight = 1.0f;
const csmFloat32 ViewLogicalBottom = -1.0f;
const csmFloat32 ViewLogicalTop = 1.0f;

const csmFloat32 ViewLogicalMaxLeft = -2.0f;
const csmFloat32 ViewLogicalMaxRight = 2.0f;
const csmFloat32 ViewLogicalMaxBottom = -2.0f;
const csmFloat32 ViewLogicalMaxTop = 2.0f;

const csmChar* MotionGroupIdle = "Idle";
const csmChar* MotionGroupTapBody = "TapBody";

const csmChar* HitAreaNameHead = "Head";
const csmChar* HitAreaNameBody = "Body";

const csmInt32 PriorityNone = 0;
const csmInt32 PriorityIdle = 1;
const csmInt32 PriorityNormal = 2;
const csmInt32 PriorityForce = 3;

const csmBool DebugLogEnable = true;
const Csm::CubismFramework::Option::LogLevel CubismLoggingLevel =
    Csm::CubismFramework::Option::LogLevel_Verbose;

} // namespace LAppDefine