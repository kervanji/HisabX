; -- HisabX Installer (Inno Setup 6) --
; Put this file in your project root:
;   C:\Users\smart\Projects\HisabX\CascadeProjects\windsurf-project\HisabX\
; Make sure you built the "distribution" folder first.
;
; Output EXE will be generated into: .\installer\

#define AppName "HisabX"
#define AppVersion "1.0.6"
#define AppPublisher "Kervanji"
#define AppURL "https://example.local/"
#define DistDir "distribution"
#define IconFile "src\main\resources\templates\HisabX.ico"

[Setup]
AppId={{9D69B7D5-7B3A-4B69-9A8E-6A6DD0B0B0E6}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}

DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}

; Installer look
WizardStyle=modern
DisableProgramGroupPage=yes

; Because your package contains a full Java runtime (~160MB),
; compression can use a lot of RAM in the compiler.
; These settings make compilation more stable:
Compression=lzma2
SolidCompression=no
LZMAUseSeparateProcess=yes

; Icons
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\{#AppName}.ico

; 64-bit
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

PrivilegesRequired=admin

; Output
OutputDir={#SourcePath}\installer
OutputBaseFilename={#AppName}-Setup-{#AppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "arabic"; MessagesFile: "compiler:Languages\Arabic.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Files]
; Copy the entire distribution folder into {app}
Source: "{#DistDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

; Copy the ICO to the install directory (used by shortcut/uninstall icon)
Source: "{#IconFile}"; DestDir: "{app}"; DestName: "{#AppName}.ico"; Flags: ignoreversion

[Icons]
; Start Menu shortcut (runs VBS via wscript with no console window)
Name: "{group}\{#AppName}"; Filename: "{sys}\wscript.exe"; Parameters: """{app}\{#AppName}.vbs"""; WorkingDir: "{app}"; IconFilename: "{app}\{#AppName}.ico"

; Optional Desktop icon
Name: "{commondesktop}\{#AppName}"; Filename: "{sys}\wscript.exe"; Parameters: """{app}\{#AppName}.vbs"""; WorkingDir: "{app}"; IconFilename: "{app}\{#AppName}.ico"; Tasks: desktopicon

[Run]
Filename: "{sys}\wscript.exe"; Parameters: """{app}\{#AppName}.vbs"""; Description: "Run {#AppName}"; Flags: nowait postinstall skipifsilent
