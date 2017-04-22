# Dsync
![Dsync Screenshots](docs/dsync_1.png)
Simple Win32 utility that tracks new file changes and backs up the directory unintrusively.

Dsync aims to fill in the gap between source control systems and real time cloud sync programs by providing an offline alternative. Real-time sync programs lock files to upload or, back up too frequently. Source control is invoked by the user and duration between commits can become infrequent. Dsync watches folders and backs up changes periodically.

Dsync is built using the WIN32API and leverages 7zip executable, 7za.exe to backup files (for now, because it keeps things simple). Besides the 7za, it has minimal dependencies and a light footprint. It's a custom built tool that is both a command line tool and a WIN32 process depending on the arguments given and is relatively rough around the edges.

Currently when the utility detects a change in the folder, every 2 minutes (non-configurable at the moment) it will 7zip the directory to the backup directories. It ONLY supports watching directories at the moment.

# Usage
1. Use "dsync watch <directory>" in the command line to add directories to the list.
2. Use "dsync backupto <directory>" in the command line to add backup destinations.
3. Execute dsync on its own to with no arguments to launch as an app in the tray-icon.

Done! When it detects changes, after 2 minutes Windows will give you a notification that the directory is being backed up to your locations. The app will NOT run if you do not specify atleast 1 watch and backup directory.

# Build
Project is developed under Visual Studio 2017. You can build using the provided solution. There's also a build.bat file using Visual Studio build tools for command line compilation. The batch file uses a UnityBuild approach where as the solution currently compiles the regular way.
