Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

strScriptPath = objFSO.GetParentFolderName(WScript.ScriptFullName)

cmd = """" & strScriptPath & "\runtime\bin\javaw.exe"" --module-path """ & strScriptPath & "\runtime\lib"" --add-modules javafx.controls,javafx.fxml,javafx.swing -cp """ & strScriptPath & "\HisabX.jar"" com.hisabx.MainApp"
objShell.Run cmd, 0, False

Set objShell = Nothing
Set objFSO = Nothing
