; HisabX Installer Script for Inno Setup
; نظام إدارة المخازن والمبيعات

#define MyAppName "HisabX"
#define MyAppNameArabic "حساب إكس - نظام إدارة المخازن والمبيعات"
#define MyAppVersion "1.0.6"
#define MyAppPublisher "HisabX"
#define MyAppExeName "HisabX.exe"
#define MyAppIcon "src\main\resources\templates\HisabX.ico"

[Setup]
; Basic Information
AppId={{A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={sd}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=installer-output
OutputBaseFilename=HisabX-Setup-{#MyAppVersion}
Compression=lzma2/fast
SolidCompression=no
WizardStyle=modern
SetupIconFile={#MyAppIcon}
UninstallDisplayIcon={app}\{#MyAppExeName}

; Performance and Progress
SetupLogging=yes
DiskSpanning=no
InternalCompressLevel=fast
UsePreviousAppDir=no
AllowNoIcons=yes
ShowTasksTreeLines=yes
AlwaysShowComponentsList=no
DisableReadyPage=no
DisableReadyMemo=no

; Language and UI
ShowLanguageDialog=no
LanguageDetectionMethod=none

; Privileges
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog

; Version Info
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=نظام إدارة المخازن والمبيعات
VersionInfoCopyright=Copyright (C) 2026 {#MyAppPublisher}

; Architecture
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "quicklaunchicon"; Description: "{cm:CreateQuickLaunchIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Main Application Files
Source: "distribution\HisabX.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "distribution\HisabX.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "distribution\HisabX.vbs"; DestDir: "{app}"; Flags: ignoreversion
Source: "distribution\HisabX.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "distribution\README.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme

; Runtime (Java) - copy everything to avoid missing security/config files
Source: "distribution\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; Start Menu
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Comment: "{#MyAppNameArabic}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"

; Desktop Icon
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon; Comment: "{#MyAppNameArabic}"

; Quick Launch
Name: "{userappdata}\Microsoft\Internet Explorer\Quick Launch\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: quicklaunchicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent shellexec

[UninstallDelete]
Type: filesandordirs; Name: "{app}\receipts"
Type: filesandordirs; Name: "{app}\reports"
Type: filesandordirs; Name: "{app}\statements"
Type: filesandordirs; Name: "{app}\logs"

[Code]

function InitializeSetup(): Boolean;
var
  ResultCode: Integer;
begin
  Result := True;
  
  // Check if already installed
  if RegKeyExists(HKEY_LOCAL_MACHINE, 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{#SetupSetting("AppId")}_is1') or
     RegKeyExists(HKEY_CURRENT_USER, 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{#SetupSetting("AppId")}_is1') then
  begin
    if MsgBox('يبدو أن البرنامج مثبت بالفعل. هل تريد إلغاء التثبيت القديم والمتابعة؟' + #13#10 + 
              'HisabX is already installed. Do you want to uninstall the old version and continue?', 
              mbConfirmation, MB_YESNO) = IDYES then
    begin
      Result := True;
    end
    else
    begin
      Result := False;
    end;
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  case CurStep of
    ssInstall:
      begin
        Log('Starting installation process...');
      end;
    ssPostInstall:
      begin
        Log('Creating application directories...');
        // Create directories for application data
        if not DirExists(ExpandConstant('{app}\receipts')) then
          CreateDir(ExpandConstant('{app}\receipts'));
        if not DirExists(ExpandConstant('{app}\reports')) then
          CreateDir(ExpandConstant('{app}\reports'));
        if not DirExists(ExpandConstant('{app}\statements')) then
          CreateDir(ExpandConstant('{app}\statements'));
        Log('Installation completed successfully.');
      end;
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  case CurPageID of
    wpPreparing:
      begin
        Log('Preparing to install - extracting files...');
      end;
    wpInstalling:
      begin
        Log('Installing files to destination...');
      end;
    wpFinished:
      begin
        Log('Installation wizard finished.');
      end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ResultCode: Integer;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    if MsgBox('هل تريد حذف قاعدة البيانات والملفات المرتبطة؟' + #13#10 + 
              'Do you want to delete the database and related files?', 
              mbConfirmation, MB_YESNO) = IDYES then
    begin
      DelTree(ExpandConstant('{app}'), True, True, True);
    end;
  end;
end;

[Messages]
WelcomeLabel2=سيقوم هذا البرنامج بتثبيت [name/ver] على جهازك.%n%nيُنصح بإغلاق جميع التطبيقات الأخرى قبل المتابعة.
ClickNext=اضغط التالي للمتابعة، أو إلغاء للخروج من التثبيت.
SelectDirLabel3=سيتم تثبيت البرنامج في المجلد التالي.
SelectDirBrowseLabel=للمتابعة، اضغط التالي. لاختيار مجلد آخر، اضغط استعراض.
DiskSpaceMBLabel=يتطلب التثبيت على الأقل [mb] ميجابايت من المساحة الحرة.
StatusExtractFiles=جاري استخراج الملفات...
StatusCreatingIcons=جاري إنشاء الاختصارات...
StatusRunProgram=جاري إنهاء التثبيت...
FinishedHeadingLabel=اكتمل تثبيت [name]
FinishedLabelNoIcons=تم تثبيت [name] بنجاح على جهازك.
FinishedLabel=تم تثبيت [name] بنجاح. يمكنك تشغيل البرنامج من الاختصارات المثبتة.
ClickFinish=اضغط إنهاء للخروج من التثبيت.
SetupAppTitle=تثبيت {#MyAppName}
SetupWindowTitle=تثبيت - {#MyAppName}
UninstallAppTitle=إلغاء تثبيت {#MyAppName}
UninstallAppFullTitle=إلغاء تثبيت {#MyAppName}
