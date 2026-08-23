/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

/*
 * Forced-include warning shield for the vendored Cubism SDK sources.
 *
 * The ROM tree compiles cc modules with a global -Werror that is appended
 * AFTER module cflags, so -Wno-* / -Wno-everything on Android.bp lose the
 * ordering battle and upstream warnings (unused parameters in CubismJson.hpp,
 * ctor init order, ignored qualifiers, ...) become hard errors.
 *
 * A pragma inside a force-included header is evaluated with the translation
 * unit, i.e. AFTER all command-line flags, so this reliably downgrades every
 * diagnostic for vendored code regardless of product configuration.
 *
 * Applied only to the Cubism framework/engine modules - application Kotlin
 * and other native code keep normal diagnostics.
 */

#pragma GCC diagnostic ignored "-Weverything"
