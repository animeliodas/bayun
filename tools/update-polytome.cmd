@echo off
rem Собрать wheel движка Polytome из соседней папки с исходниками и положить в app\wheels.
rem Использование: tools\update-polytome.cmd [путь к исходникам Polytome]
setlocal
set "SRC=%~1"
if "%SRC%"=="" set "SRC=%~dp0..\..\polytome"
if not exist "%SRC%\pyproject.toml" (
  echo Не найдены исходники Polytome: %SRC%
  exit /b 1
)
del /q "%~dp0..\app\wheels\polytome-*.whl" 2>nul
py -3.12 -m pip wheel "%SRC%" --no-deps -q -w "%~dp0..\app\wheels" || exit /b 1
dir /b "%~dp0..\app\wheels"
