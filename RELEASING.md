# Releasing

1. Update the version of `ktormqtt` in [libs.versions.toml](gradle/libs.versions.toml).

1. Change the "Using in your project" section in [README.md](README.md) (check the ktor version as well).

1. Commit the changes and create a tag with the new version, for example `v.1.12.0`.

1. Push both the commit and the tag.

1. Wait for the GitHub action to create the release bundles and upload them to maven.

1. Visit [Maven Central](https://central.sonatype.com/publishing) and promote the 2 new deployments (one for iOS and one
   for the rest).

1. Visit the [GitHub Releases](https://github.com/ukemp/ktor-mqtt/releases) page and create a new release.