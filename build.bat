@echo off
set "repPath=%cd%"
cd ..
set "rpPath=%cd%"
cd %repPath%
rmdir "%rpPath%\Craftec\" /Q/S
"C:\Program Files\7-Zip\7z" x "VanillaTweaks.zip" -o"%rpPath%\Craftec" -r -aoa
ren "%rpPath%\Craftec\Selected Packs.txt" "Vanilla Tweaks.txt"
Xcopy "%repPath%" "%rpPath%\Craftec" /S/Y/Q /EXCLUDE:buildexclude.txt
del "%repPath%\Craftec.zip"
"C:\Program Files\7-Zip\7z" a "%repPath%\Craftec.zip" "%rpPath%\Craftec\*"
echo.
echo [92mSuccesfully built resource pack![0m