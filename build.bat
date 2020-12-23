del "Craftec.zip"
"C:\Program Files\7-Zip\7z" a -tzip -r "%cd%\Craftec.zip" "%cd%\*" -x!"*.git*" -x!*"Craftec.zip" -x!"build.bat" -x!"*.cubik" -x!"*.bbmodel"
echo.
echo [92mSuccesfully built resource pack![0m
pause