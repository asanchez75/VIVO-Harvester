#!/bin/bash

# see example-scripts/README.txt for information about HARVESTER_JAVA_OPTS

java $HARVESTER_JAVA_OPTS org.vivoweb.harvester.fetch.D2RMapFetch -X d2rmapfetch.conf.xml
