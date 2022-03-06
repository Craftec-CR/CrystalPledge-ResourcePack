@echo off
set "repPath=%cd%"
set "rpPath=%AppData%\.minecraft\resourcepacks\"
rmdir "%rpPath%\CrystalPledge\" /Q/S
7z x "VanillaTweaks.zip" -o"%rpPath%\CrystalPledge" -r -aoa
ren "%rpPath%\CrystalPledge\Selected Packs.txt" "Vanilla Tweaks.txt"
Xcopy "%repPath%" "%rpPath%\CrystalPledge" /S/Y/Q /EXCLUDE:buildexclude.txt
del "%repPath%\CrystalPledge.zip"
7z a "%repPath%\CrystalPledge.zip" "%rpPath%\CrystalPledge\*"
echo.
echo [92mSuccesfully built resource pack![0m