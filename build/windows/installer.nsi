; Aqua Browser installer for Windows. Installs for the current user (no admin needed), adds Start menu
; and desktop shortcuts, and registers Aqua Browser with Windows so it shows up in Default apps.
;   makensis /DSRC=<prepared core folder> /DOUTFILE=<setup.exe> /DVERSION=2.0.0 /DICON=<aqua.ico> installer.nsi
Unicode true
SetCompressor /SOLID lzma
!include "MUI2.nsh"
!define APP "Aqua Browser"
!define KEY "AquaBrowser"
!define EXE "firefox.exe"
; Aqua's identity file (its own profile folder, %APPDATA%\AquaBrowser). "-app <file>" must be the first argument.
!define APPARG '-app "$INSTDIR\browser\application.ini"'
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${KEY}"
Name "${APP}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\${APP}"
InstallDirRegKey HKCU "Software\${KEY}" "InstallDir"
RequestExecutionLevel user
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_WELCOMEPAGE_TITLE "Aqua Browser ${VERSION}"
!define MUI_WELCOMEPAGE_TEXT "A glass web browser in the style of Aqua MD, built on Mozilla's open-source engine.$\r$\n$\r$\nIf Aqua Browser is open, close it before you continue."
!define MUI_FINISHPAGE_RUN "$INSTDIR\${EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Open Aqua Browser now"
!define MUI_FINISHPAGE_RUN_PARAMETERS '-app "$INSTDIR\browser\application.ini"'
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APP}"
VIAddVersionKey "FileDescription" "${APP} setup"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "Aqua Browser. Engine by Mozilla (MPL 2.0)."

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "${SRC}\*.*"
  File "/oname=aqua.ico" "${ICON}"
  WriteUninstaller "$INSTDIR\Uninstall Aqua Browser.exe"
  CreateShortCut "$SMPROGRAMS\${APP}.lnk" "$INSTDIR\${EXE}" '${APPARG}' "$INSTDIR\aqua.ico" 0
  CreateShortCut "$SMPROGRAMS\${APP} (private window).lnk" "$INSTDIR\${EXE}" '${APPARG} -private-window' "$INSTDIR\aqua.ico" 0
  CreateShortCut "$DESKTOP\${APP}.lnk" "$INSTDIR\${EXE}" '${APPARG}' "$INSTDIR\aqua.ico" 0
  WriteRegStr HKCU "Software\${KEY}" "InstallDir" "$INSTDIR"
  ; ---- what Windows needs to offer Aqua Browser as a default browser ----
  WriteRegStr HKCU "Software\Classes\AquaBrowserURL" "" "Aqua Browser URL"
  WriteRegStr HKCU "Software\Classes\AquaBrowserURL" "URL Protocol" ""
  WriteRegStr HKCU "Software\Classes\AquaBrowserURL\DefaultIcon" "" "$INSTDIR\aqua.ico,0"
  WriteRegStr HKCU "Software\Classes\AquaBrowserURL\shell\open\command" "" '"$INSTDIR\${EXE}" ${APPARG} -url "%1"'
  WriteRegStr HKCU "Software\Classes\AquaBrowserHTML" "" "Aqua Browser web page"
  WriteRegStr HKCU "Software\Classes\AquaBrowserHTML\DefaultIcon" "" "$INSTDIR\aqua.ico,0"
  WriteRegStr HKCU "Software\Classes\AquaBrowserHTML\shell\open\command" "" '"$INSTDIR\${EXE}" ${APPARG} -url "%1"'
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}" "" "${APP}"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\DefaultIcon" "" "$INSTDIR\aqua.ico,0"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\shell\open\command" "" '"$INSTDIR\${EXE}" ${APPARG}'
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities" "ApplicationName" "${APP}"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities" "ApplicationDescription" "A glass web browser in the style of Aqua MD"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities" "ApplicationIcon" "$INSTDIR\aqua.ico,0"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\StartMenu" "StartMenuInternet" "${KEY}"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\URLAssociations" "http" "AquaBrowserURL"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\URLAssociations" "https" "AquaBrowserURL"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\FileAssociations" ".htm" "AquaBrowserHTML"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\FileAssociations" ".html" "AquaBrowserHTML"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\FileAssociations" ".xhtml" "AquaBrowserHTML"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\FileAssociations" ".svg" "AquaBrowserHTML"
  WriteRegStr HKCU "Software\Clients\StartMenuInternet\${KEY}\Capabilities\FileAssociations" ".pdf" "AquaBrowserHTML"
  WriteRegStr HKCU "Software\RegisteredApplications" "${APP}" "Software\Clients\StartMenuInternet\${KEY}\Capabilities"
  ; ---- Apps & features ----
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayName" "${APP}"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTKEY}" "Publisher" "Aqua Browser"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\aqua.ico"
  WriteRegStr HKCU "${UNINSTKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" '"$INSTDIR\Uninstall Aqua Browser.exe"'
  WriteRegStr HKCU "${UNINSTKEY}" "URLInfoAbout" "https://danii45.github.io/Aqua-Browser/"
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\${APP}.lnk"
  Delete "$SMPROGRAMS\${APP} (private window).lnk"
  Delete "$DESKTOP\${APP}.lnk"
  DeleteRegKey HKCU "Software\Classes\AquaBrowserURL"
  DeleteRegKey HKCU "Software\Classes\AquaBrowserHTML"
  DeleteRegKey HKCU "Software\Clients\StartMenuInternet\${KEY}"
  DeleteRegValue HKCU "Software\RegisteredApplications" "${APP}"
  DeleteRegKey HKCU "${UNINSTKEY}"
  DeleteRegKey HKCU "Software\${KEY}"
  RMDir /r "$INSTDIR"
  ; Your profiles (bookmarks, passwords, history) stay in %APPDATA%\Aqua unless you delete that folder.
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd
