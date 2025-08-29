#!/bin/bash

# Exit immediately if a command fails
set -e

# Check if the environment variable is set
if [ -z "$MAVEN_GPG_PASSPHRASE" ]; then
  echo "Error: MAVEN_GPG_PASSPHRASE environment variable is not set."
  exit 1
fi
if [ -z "$MAVEN_GPG_KEYNAME" ]; then
  echo "Error: MAVEN_GPG_KEYNAME environment variable (e.g. C33CD8D62B72FE01) is not set."
  exit 1
fi

VERSION="0.5.0-SNAPSHOT"

echo "--- Publishing version ${VERSION} locally ---"

# Run the Maven command with release profile using the environment vars for version and GPG key/passphrase
mvn clean install -Prelease -Dversion.coordinate="${VERSION}"

echo "--- Done ---"