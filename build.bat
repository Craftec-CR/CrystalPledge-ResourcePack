@echo off
del "Craftec.zip"
"C:\Program Files\7-Zip\7z" x "VanillaTweaks.zip" -o"%cd%\temp" -r -aoa
ren "%cd%\temp\Selected Packs.txt" "Vanilla Tweaks.txt"
"C:\Program Files\7-Zip\7z" a "%cd%\Craftec.zip" "%cd%\temp\*"
rmdir "%cd%\temp\" /Q/S
"C:\Program Files\7-Zip\7z" a "%cd%\Craftec.zip" "%cd%\*" -xr@buildexclude.txt
echo.
echo [92mSuccesfully built resource pack![0m
pause