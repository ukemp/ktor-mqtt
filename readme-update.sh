#!/bin/bash
# Update version strings in README.md.
KTORMQTT=$(grep -oP 'ktormqtt\s*=\s*"\K\w+([0-9.]+)' gradle/libs.versions.toml)
KTOR=$(grep -oP 'ktor\s*=\s*"\K\w+([0-9.]+)' gradle/libs.versions.toml)

echo "Upgrading to ktormqtt ${KTORMQTT} and ktor ${KTOR}..."
sed -i -E "s/(de\.kempmobil\.ktor\.mqtt:mqtt-core:)[0-9.]+/\1${KTORMQTT}/" README.md
sed -i -E "s/(de\.kempmobil\.ktor\.mqtt:mqtt-client:)[0-9.]+/\1${KTORMQTT}/" README.md
sed -i -E "s/(de\.kempmobil\.ktor\.mqtt:mqtt-client-ws:)[0-9.]+/\1${KTORMQTT}/" README.md
sed -i -E "s/(io\.ktor:ktor-client-cio:)[0-9.]+/\1${KTOR}/" README.md
