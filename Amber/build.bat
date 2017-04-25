@echo off
gradlew installDebug && adb shell am start -n com.dqnt.amber/.MainActivity
