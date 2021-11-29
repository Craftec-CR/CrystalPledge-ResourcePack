@echo off
set "repPath=%cd%"
set "rpPath=%AppData%\.minecraft\resourcepacks\"
rmdir "%rpPath%\Craftec\" /Q/S
7z x "VanillaTweaks.zip" -o"%rpPath%\Craftec" -r -aoa
ren "%rpPath%\Craftec\Selected Packs.txt" "Vanilla Tweaks.txt"
Xcopy "%repPath%" "%rpPath%\Craftec" /S/Y/Q /EXCLUDE:buildexclude.txt
del "%repPath%\Craftec.zip"
7z a "%repPath%\Craftec.zip" "%rpPath%\Craftec\*"
echo.
echo [92mSuccesfully built resource pack![0m