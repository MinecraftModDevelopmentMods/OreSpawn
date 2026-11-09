The retained LegacyApiConsumer.class has SHA-256
BA3AECFB53AAA31B7FB24CD6F7A8376EF11185F61425024A9543FA2D10220564.

It was compiled with exact Temurin 8.0.502+7 against the published
OreSpawn-4.0.16.112021.jar whose SHA-256 is
7A33DF1BB2B856F79421D69425282DBB4756E03824EB7263EDCCB8DE43D87F07.

The adjacent Java source is retained for review. The binary proves that an
existing consumer of StandardPatternSettings.CODEC and OrePatternType.create
continues to run unchanged after the internal compatibility repair.
