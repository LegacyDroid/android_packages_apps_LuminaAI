#pragma once

/**
 * @file LAppDefine.hpp
 *
 * App-wide constants: view configuration, motion groups, hit areas and
 * priorities. Values mirror the official Cubism sample (LAppDefine) so the
 * behaviour of the model (motion groups, tap handling) stays consistent.
 */

#include <CubismFramework.hpp>

namespace LAppDefine {

using Csm::csmBool;
using Csm::csmChar;
using Csm::csmFloat32;
using Csm::csmInt32;

// --- View configuration ----------------------------------------------------
extern const csmFloat32 ViewScale;      ///< default zoom
extern const csmFloat32 ViewMaxScale;   ///< max zoom
extern const csmFloat32 ViewMinScale;   ///< min zoom

// Logical screen bounds (device is mapped onto these).
extern const csmFloat32 ViewLogicalLeft;
extern const csmFloat32 ViewLogicalRight;
extern const csmFloat32 ViewLogicalBottom;
extern const csmFloat32 ViewLogicalTop;

// Maximum pannable bounds of the logical screen.
extern const csmFloat32 ViewLogicalMaxLeft;
extern const csmFloat32 ViewLogicalMaxRight;
extern const csmFloat32 ViewLogicalMaxBottom;
extern const csmFloat32 ViewLogicalMaxTop;

// --- Motion groups (must match the keys inside the model's *.model3.json) ---
extern const csmChar* MotionGroupIdle;     ///< played automatically when no motion runs
extern const csmChar* MotionGroupTapBody;  ///< played when the body is tapped

// --- Hit areas (must match the model's HitAreas[].Name) ----------------------
extern const csmChar* HitAreaNameHead;     ///< tapping the head triggers a random expression
extern const csmChar* HitAreaNameBody;     ///< tapping the body triggers a motion

// --- Motion priority ---------------------------------------------------------
extern const csmInt32 PriorityNone;    ///< 0: cannot interrupt
extern const csmInt32 PriorityIdle;    ///< 1: idle motions
extern const csmInt32 PriorityNormal;  ///< 2: normal motions
extern const csmInt32 PriorityForce;   ///< 3: force (interrupts everything)

// --- Logging ------------------------------------------------------------------
extern const csmBool DebugLogEnable;                            ///< app logs
extern const Csm::CubismFramework::Option::LogLevel CubismLoggingLevel; ///< framework logs

} // namespace LAppDefine