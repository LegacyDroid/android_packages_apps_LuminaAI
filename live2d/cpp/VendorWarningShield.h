/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

/*
 * Force included for the vendored Cubism sources only.
 *
 * The ROM build appends a global -Werror after the module cflags, so -Wno
 * flags in Android.bp never win and upstream warnings turn into errors.
 * Pragmas in this header are evaluated with the translation unit, after all
 * command line flags, so they reliably silence everything here.
 */

#pragma GCC diagnostic ignored "-Weverything"
