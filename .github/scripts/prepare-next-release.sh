#!/bin/bash
# prepare-next-release.sh - Unified post-release preparation
# Usage: prepare-next-release.sh <published_version> <bump_type> <release_type>

set -e

PUBLISHED_VERSION=$1
BUMP_TYPE=$2
RELEASE_TYPE=$3

if [ -z "$PUBLISHED_VERSION" ] || [ -z "$BUMP_TYPE" ] || [ -z "$RELEASE_TYPE" ]; then
  echo "❌ Usage: $0 <published_version> <bump_type> <release_type>"
  echo "   Example: $0 0.1.0-alpha01 patch alpha"
  exit 1
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Preparing for next release after $PUBLISHED_VERSION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Step 1: Sync documentation to published version
echo ""
echo "📚 Step 1/3: Syncing docs to published version..."
kotlin .github/scripts/sync-docs-version.main.kts "$PUBLISHED_VERSION"
echo "  ✓ Documentation synced to $PUBLISHED_VERSION"

# Step 2: Bump to next development version (NO SNAPSHOT)
echo ""
echo "⬆️  Step 2/3: Bumping to next development version..."
kotlin .github/scripts/bump-version.main.kts "$BUMP_TYPE" "$RELEASE_TYPE"
NEXT_VERSION=$(grep "version=" gradle.properties | cut -d'=' -f2)
echo "  ✓ Bumped to $NEXT_VERSION"

# Step 3: Stage all changes for commit
echo ""
echo "📝 Step 3/3: Staging changes..."
# Stage each path independently: one missing path (e.g. docs/ before the site exists) must
# not abort staging of the others — `git add a missing-b c` stages nothing at all.
for path in gradle.properties gradle/libs.versions.toml docs/ samples/ README.md; do
  git add "$path" 2>/dev/null || true
done

# Check if there are changes to commit
if git diff --cached --quiet; then
  echo "  ⚠ No changes to commit"
  exit 0
fi

# Create unified commit
echo ""
echo "💾 Creating unified commit..."
# --no-verify: the local DOD pre-commit hook builds the samples, which necessarily fails on a
# version-bump commit (the bumped plugin version is only published by the NEXT release). CI
# runners have no hooks installed; this keeps local runs behaving identically.
git commit --no-verify -m "chore: prepare for next version $NEXT_VERSION"
echo "  ✓ Committed: chore: prepare for next version $NEXT_VERSION"

# Output for GitHub Actions
if [ -n "$GITHUB_OUTPUT" ]; then
  echo "next_version=$NEXT_VERSION" >> "$GITHUB_OUTPUT"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Done! Ready to push."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
