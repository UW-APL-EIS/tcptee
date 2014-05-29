#!/bin/bash

# This merely runs the app out of the ./target dir when building from Maven
#
# Re-jig this to place your jars in some more accessible place...
# Note that the main artifact's manifest declares a class path,
# containing all dependent jars (currently just commons-cli). So if
# you DO move the jars, move them ALL, at once, to the SAME final
# destination.

ARTIFACT=tcptee-1.0
JAR=target/$ARTIFACT.jar

exec java -jar $JAR "$@"

# eof
