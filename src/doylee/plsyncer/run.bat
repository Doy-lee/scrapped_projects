@echo off
pushd ..\..\..\build
FOR %%c IN (sample_data/*.m3u) DO (
	java doylee.plsyncer.PlaylistSyncer sample_data/%%c sample_data/target/
)
popd
