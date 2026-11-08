# Contributing

Build with `./gradlew build`. Gradle needs a JDK 21+ to start and downloads Java 25 for the actual
compile. `./gradlew runClient` gives you a dev client; put a few shader zips in `run/shaderpacks`.

Before opening a PR:

- `./gradlew build` passes, including the unit tests
- if you touched the dock, `./gradlew runClientGameTest` passes too (it needs zips in `run/shaderpacks`)
- one concern per PR. A fix and a refactor are two PRs
- match the style of the file you're in: tabs, Mojang brace style. No reformat-only PRs
- anything that calls into Iris goes through `IrisCompat`, nowhere else
- keep mixins minimal. A new mixin needs a comment saying why a Fabric API event or the Iris adapter
  can't do the job

For bugs, open an issue with your Minecraft, Iris and Shadock versions and a log (mclo.gs is fine).
