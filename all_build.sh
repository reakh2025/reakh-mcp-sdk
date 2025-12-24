#! /bin/bash

START_SEC=$(date +%s)
./gradlew clean
./gradlew build publishToMavenLocal -x test -parallel --max-workers=8

COST_SEC=$(( $(date +%s)-START_SEC ))
NOW=$(date +%H:%M:%S)
printf "\033[32mBuild complete at %s, cost %d min %d sec\033[0m\n" "$NOW" "$(( COST_SEC / 60 ))" "$(( COST_SEC % 60 ))"
