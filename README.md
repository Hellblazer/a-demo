# Delos Large Scale Demo

## Build Status

![Build Status](https://github.com/Hellblazer/a-demo/actions/workflows/maven.yml/badge.svg)

This is the repository of the Sky application, built on the Delos distributed platform. It's a fantasy POC for a
minimal
viable system built using the modular framework of Delos.

## Build

The build contains the Maven wrapper and can be invoked in that top level directory with:
`./mvnw clean install`

## End to End Docker testing (local)

To run the Sky End to End testing using local docker, add the **e2e** profile to the
invocation: `./mvnw -P e2e clean install`

One can also cd into the [local-demo](local-demo) and build in that directory using the **e2e** profile.

## Docker Image

The Sky container image is built in the aptly named [sky-image](sky-image) module and is currently generically
programmed
via key environment variables as can be seen in
the [End to End smoke testing using the TestContainer framework](local-demo/src/test/java/com/hellblazer/sky/demo/SmokeTest.java)
