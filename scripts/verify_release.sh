#!/usr/bin/env bash
# verify_release.sh — Build and verify OpenBible release APK
# 
# Runs unit tests, builds the release APK, then verifies that critical
# classes survive R8 minification (the most common crash cause).
#
# Usage: ./scripts/verify_release.sh
# Returns: 0 if all checks pass, 1 on any failure

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

PASS=0
FAIL=0

pass()  { PASS=$((PASS+1)); echo "  ✓ $1"; }
fail()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; }

echo "══════════════════════════════════════════════════════════════"
echo "  OpenBible Release Verification"
echo "══════════════════════════════════════════════════════════════"
echo ""

# ── Phase 1: Unit Tests ─────────────────────────────────────────────
echo "── Phase 1: Unit Tests ────────────────────────────────────────"

TEST_OUTPUT=$(./gradlew test 2>&1 || true)
if echo "$TEST_OUTPUT" | grep -q "BUILD SUCCESSFUL"; then
    pass "Unit tests pass"
else
    fail "Unit tests failed"
    echo "$TEST_OUTPUT" | tail -30
fi

# ── Phase 2: Release Build ──────────────────────────────────────────
echo "── Phase 2: Release Build ─────────────────────────────────────"

BUILD_OUTPUT=$(./gradlew assembleRelease 2>&1 || true)
if echo "$BUILD_OUTPUT" | grep -q "BUILD SUCCESSFUL"; then
    pass "Release build successful"
else
    fail "Release build failed"
    echo "$BUILD_OUTPUT" | tail -30
fi

APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
MAPPING_DIR="$PROJECT_DIR/app/build/outputs/mapping/release"

if [ ! -f "$APK" ]; then
    fail "APK not found at $APK"
    exit 1
fi

APK_SIZE=$(stat --printf="%s" "$APK" 2>/dev/null || stat -f%z "$APK" 2>/dev/null)
pass "APK exists ($(( APK_SIZE / 1024 / 1024 )) MB)"

# ── Phase 3: R8 Survival Checks ─────────────────────────────────────
echo "── Phase 3: R8 Class Survival ─────────────────────────────────"

MAPPING="$MAPPING_DIR/mapping.txt"
USAGE="$MAPPING_DIR/usage.txt"
SEEDS="$MAPPING_DIR/seeds.txt"

declare -A CRITICAL=(
    ["Navigation library"]="androidx.navigation.compose.NavHostKt"
    ["NavGraph composable"]="com.openbible.navigation.NavGraphKt"
    ["HomeScreen"]="com.openbible.ui.home.HomeScreenKt"
    ["BibleScreen"]="com.openbible.ui.bible.BibleScreenKt"
    ["SearchScreen"]="com.openbible.ui.search.SearchScreenKt"
    ["BookmarksScreen"]="com.openbible.ui.bookmarks.BookmarksScreenKt"
    ["SettingsScreen"]="com.openbible.ui.settings.SettingsScreenKt"
    ["StrongDetailScreen"]="com.openbible.ui.strongs.StrongDetailScreenKt"
    ["StrongSearchScreen"]="com.openbible.ui.strongs.StrongSearchScreenKt"
    ["LocationListScreen"]="com.openbible.ui.locations.LocationListScreenKt"
    ["LocationDetailScreen"]="com.openbible.ui.locations.LocationDetailScreenKt"
    ["ReadingPlanScreen"]="com.openbible.ui.readingplan.ReadingPlanScreenKt"
    ["BibleWithNotesScreen"]="com.openbible.ui.notes.BibleWithNotesScreenKt"
    ["NotebookListScreen"]="com.openbible.ui.notes.NotebookListScreenKt"
    ["OpenBibleTheme"]="com.openbible.ui.theme.ThemeKt"
    ["Hilt_OpenBibleApp"]="com.openbible.Hilt_OpenBibleApp"
    ["DaggerOpenBibleApp"]="com.openbible.DaggerOpenBibleApp"
)

ALL_SURVIVED=true
ALL_TOTAL=0
ALL_KEPT=0

for LABEL in "${!CRITICAL[@]}"; do
    CLS="${CRITICAL[$LABEL]}"
    ALL_TOTAL=$((ALL_TOTAL+1))

    # Check if class is in mapping (kept, possibly obfuscated)
    IN_MAPPING=$(grep -c "^$CLS" "$MAPPING" 2>/dev/null || true)
    # Check if class is in usage (removed)
    IN_USAGE=$(grep -c "^$CLS" "$USAGE" 2>/dev/null || true)

    # Also check with $ wildcard for inner classes
    # A class is kept if it appears in mapping OR if it appears in seeds
    IN_SEEDS=$(grep -c "^$CLS" "$SEEDS" 2>/dev/null || true)

    if [ "$IN_MAPPING" -gt 0 ] || [ "$IN_SEEDS" -gt 0 ]; then
        ALL_KEPT=$((ALL_KEPT+1))
    else
        echo "  ✗ MISSING: $LABEL ($CLS)"
        ALL_SURVIVED=false
    fi
done

if [ "$ALL_SURVIVED" = true ]; then
    pass "All $ALL_KEPT/$ALL_TOTAL critical classes survive R8"
else
    fail "$((ALL_TOTAL-ALL_KEPT)) critical classes missing from DEX!"
fi

# ── Phase 4: Counts Summary ─────────────────────────────────────────
echo "── Phase 4: Summary Counts ────────────────────────────────────"

NAV_COUNT=$(grep -c "^androidx.navigation\." "$MAPPING" 2>/dev/null || true)
UI_COUNT=$(grep -c "^com.openbible.ui\." "$MAPPING" 2>/dev/null || true)
HILT_COUNT=$(grep -c "^com.openbible.Hilt_\|^com.openbible.DaggerOpenBibleApp" "$MAPPING" 2>/dev/null || true)

echo "  Navigation library classes: $NAV_COUNT"
echo "  UI screen classes:          $UI_COUNT"
echo "  Hilt/Dagger classes:        $HILT_COUNT"

if [ "$NAV_COUNT" -gt 100 ]; then
    pass "Navigation classes count reasonable (>100)"
else
    fail "Navigation classes seem low ($NAV_COUNT)"
fi

if [ "$UI_COUNT" -gt 100 ]; then
    pass "Screen classes count reasonable (>100)"
else
    fail "Screen classes seem low ($UI_COUNT)"
fi

# ── Phase 5: Proguards rules validate ───────────────────────────────
echo "── Phase 5: ProGuard Rules ────────────────────────────────────"

CONFIG="$MAPPING_DIR/configuration.txt"
if grep -q "com.openbible.ui." "$CONFIG" 2>/dev/null; then
    pass "ProGuard keep rules for ui screens present in config"
else
    fail "Missing keep rules for ui screens in R8 config!"
fi

if grep -q "androidx.navigation." "$CONFIG" 2>/dev/null; then
    pass "ProGuard keep rules for navigation present in config"
else
    fail "Missing keep rules for navigation in R8 config!"
fi

# ── Results ──────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Results: $PASS passed, $FAIL failed"
echo "══════════════════════════════════════════════════════════════"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "  Some checks failed. Review output above."
    exit 1
else
    echo "  All checks passed. Release APK is ready."
    echo "  APK: $APK"
    exit 0
fi
