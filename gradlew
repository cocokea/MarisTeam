#!/usr/bin/env sh
exec gradle "$@"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
