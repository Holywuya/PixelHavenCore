#!/bin/bash

# PixelHavenCore Release Script
# Usage: ./release.sh <version>
# Example: ./release.sh 1.0.0

set -e

if [ $# -ne 1 ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 1.0.0"
    exit 1
fi

VERSION=$1
TAG="v$VERSION"

echo "Releasing PixelHavenCore version $VERSION..."

# Check if tag already exists
if git tag -l | grep -q "^$TAG$"; then
    echo "Error: Tag $TAG already exists!"
    exit 1
fi

# Update version in gradle.properties
echo "Updating version in gradle.properties..."
sed -i "s/version=.*/version=$VERSION/" gradle.properties

# Commit version change
echo "Committing version change..."
git add gradle.properties
git commit -m "release: bump version to $VERSION"

# Create and push tag
echo "Creating tag $TAG..."
git tag -a "$TAG" -m "Release version $VERSION"
git push origin master
git push origin "$TAG"

echo ""
echo "✅ Release $VERSION created successfully!"
echo "GitHub Actions will automatically build and create a release."
echo ""
echo "Release URL: https://github.com/Holywuya/PixelHavenCore/releases/tag/$TAG"