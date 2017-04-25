REM Requires Windows Powershell, with Git in Path
foreach ($i in iex 'git ls-files -i --exclude-from=.gitignore') { git rm --cached $i }
