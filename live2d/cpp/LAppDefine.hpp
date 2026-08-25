#pragma once

/*
 * LAppDefine.hpp
 *
 * App wide constants, values match the Cubism sample so model behaviour
 * stays consistent.
 */

#include <CubismFramework.hpp>

namespace LAppDefine {

using Csm::csmBool;
using Csm::csmChar;
using Csm::csmFloat32;
using Csm::csmInt32;

// view
extern const csmFloat32 ViewScale;      // default zoom
extern const csmFloat32 ViewMaxScale;   // max zoom
extern const csmFloat32 ViewMinScale;   // min zoom

// logical screen bounds the device is mapped onto
extern const csmFloat32 ViewLogicalLeft;
extern const csmFloat32 ViewLogicalRight;
extern const csmFloat32 ViewLogicalBottom;
extern const csmFloat32 ViewLogicalTop;

// how far the logical screen can pan
extern const csmFloat32 ViewLogicalMaxLeft;
extern const csmFloat32 ViewLogicalMaxRight;
extern const csmFloat32 ViewLogicalMaxBottom;
extern const csmFloat32 ViewLogicalMaxTop;

// motion groups, must match the model json
extern const csmChar* MotionGroupIdle;     // plays when nothing else runs
extern const csmChar* MotionGroupTapBody;  // plays when the body is tapped

// hit areas, must match the model
extern const csmChar* HitAreaNameHead;     // head tap, random expression
extern const csmChar* HitAreaNameBody;     // body tap, motion

// motion priority
extern const csmInt32 PriorityNone;    // cannot interrupt
extern const csmInt32 PriorityIdle;    // idle motions
extern const csmInt32 PriorityNormal;  // normal motions
extern const csmInt32 PriorityForce;   // interrupts everything

// logging
extern const csmBool DebugLogEnable;                            // app logs
extern const Csm::CubismFramework::Option::LogLevel CubismLoggingLevel; // framework logs

} // namespace LAppDefine