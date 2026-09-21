[CmdletBinding()]
param(
    [string]$TargetRoot = "",
    [ValidateSet("IntAssist", "EngineOS")]
    [string]$TargetKind = "IntAssist",
    [switch]$ObserveExistingTarget,
    [switch]$ShowTarget,
    [switch]$NoForeground,
    [string]$IntAssistRoot = "C:\projects\intassist",
    [string]$OutputRoot = (Join-Path $PSScriptRoot "..\observations"),
    [string]$MavenCommand = "mvn.cmd"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Add-TimelineEvent {
    param(
        [System.Collections.Generic.List[object]]$Timeline,
        [string]$Name,
        [string]$Detail
    )

    $Timeline.Add([pscustomobject]@{
        timestamp = (Get-Date).ToUniversalTime().ToString("o")
        name      = $Name
        detail    = $Detail
    })
}

function Invoke-GitCapture {
    param(
        [string]$Root,
        [string[]]$Arguments
    )

    $git = Get-Command git.exe -ErrorAction SilentlyContinue
    if ($null -eq $git -or -not (Test-Path (Join-Path $Root ".git"))) {
        return [pscustomobject]@{
            available = $false
            output    = ""
        }
    }

    Push-Location $Root
    try {
        $output = (& $git.Source @Arguments 2>&1 | Out-String).TrimEnd()
        return [pscustomobject]@{
            available = $true
            output    = $output
        }
    }
    finally {
        Pop-Location
    }
}

function Quote-ProcessArgument {
    param([string]$Value)

    if ($Value -notmatch '[\s"]') {
        return $Value
    }
    return '"' + ($Value -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}

Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;
using System.Runtime.InteropServices;

public static class EngineOsWindowAutomation
{
    [DllImport("user32.dll")]
    public static extern bool PostMessage(IntPtr hWnd, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int command);

    [DllImport("user32.dll")]
    public static extern bool SetWindowPos(
        IntPtr hWnd,
        IntPtr insertAfter,
        int x,
        int y,
        int width,
        int height,
        uint flags);
}
'@

function Get-TargetWindow {
    param(
        [string]$WindowTitle,
        [int[]]$ExcludedProcessIds = @()
    )

    $processes = @(Get-Process java,javaw -ErrorAction SilentlyContinue |
        Where-Object {
            $_.MainWindowTitle -eq $WindowTitle -and
            $_.MainWindowHandle -ne 0 -and
            ($ExcludedProcessIds -notcontains $_.Id)
        })
    if ($processes.Count -gt 1) {
        throw "Ambiguous $TargetKind target: found $($processes.Count) newly-created Java processes with window title '$WindowTitle'."
    }
    if ($processes.Count -eq 0) {
        return $null
    }
    return $processes[0]
}

function Find-UiElementByAutomationId {
    param(
        [System.Windows.Automation.AutomationElement]$Window,
        [string]$AutomationId
    )

    $condition = New-Object System.Windows.Automation.PropertyCondition(
        [System.Windows.Automation.AutomationElement]::AutomationIdProperty,
        $AutomationId
    )
    return $Window.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $condition)
}

function Find-UiElementByName {
    param(
        [System.Windows.Automation.AutomationElement]$Window,
        [string]$Name
    )

    $condition = New-Object System.Windows.Automation.PropertyCondition(
        [System.Windows.Automation.AutomationElement]::NameProperty,
        $Name
    )
    return $Window.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $condition)
}

function Get-UiNames {
    param([System.Windows.Automation.AutomationElement]$Window)

    $names = [System.Collections.Generic.List[string]]::new()
    $condition = [System.Windows.Automation.Condition]::TrueCondition
    foreach ($element in $Window.FindAll(
            [System.Windows.Automation.TreeScope]::Descendants, $condition)) {
        try {
            $name = $element.Current.Name
            if (-not [string]::IsNullOrWhiteSpace($name)) {
                $names.Add($name)
            }
        }
        catch {
        }
    }
    return @($names | Select-Object -Unique)
}

function Wait-ForUiElementByName {
    param(
        [IntPtr]$WindowHandle,
        [string]$Name,
        [int]$TimeoutSeconds = 5
    )

    $attempts = $TimeoutSeconds * 4
    for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
        $window = [System.Windows.Automation.AutomationElement]::FromHandle($WindowHandle)
        $element = Find-UiElementByName $window $Name
        if ($null -ne $element) {
            return $element
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Required EngineOS control '$Name' was not found on the existing Desktop."
}

function Wait-ForTargetWindow {
    param(
        [int]$TimeoutSeconds = 30,
        [int[]]$ExcludedProcessIds = @()
    )

    $attempts = $TimeoutSeconds * 2
    for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
        $windowTitle = if ($TargetKind -eq "EngineOS") { "EngineOS" } else { "IntAssist" }
        $process = Get-TargetWindow $windowTitle $ExcludedProcessIds
        if ($null -ne $process) {
            return [pscustomobject]@{
                ProcessId = $process.Id
                ProcessStartTime = $process.StartTime
                Window = [System.Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
            }
        }
        Start-Sleep -Milliseconds 500
    }
    if ($ObserveExistingTarget) {
        throw "Existing $TargetKind verification target was not found within the bounded lookup window."
    }
    throw "Timed out waiting for the visible $TargetKind window."
}

function Set-TargetWindowVisibility {
    param([IntPtr]$WindowHandle)

    if ($ShowTarget) {
        [EngineOsWindowAutomation]::ShowWindow($WindowHandle, 9) | Out-Null
        return
    }
    if ($NoForeground) {
        [EngineOsWindowAutomation]::ShowWindow($WindowHandle, 4) | Out-Null
        [EngineOsWindowAutomation]::SetWindowPos(
            $WindowHandle,
            [IntPtr]::Zero,
            0,
            0,
            0,
            0,
            0x0003 -bor 0x0010 -bor 0x0040) | Out-Null
    }
}

function Wait-ForUiName {
    param(
        [IntPtr]$WindowHandle,
        [string]$ExpectedName,
        [int]$TimeoutSeconds = 20
    )

    $condition = New-Object System.Windows.Automation.PropertyCondition(
        [System.Windows.Automation.AutomationElement]::NameProperty,
        $ExpectedName
    )
    $attempts = $TimeoutSeconds * 2
    for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
        $window = [System.Windows.Automation.AutomationElement]::FromHandle($WindowHandle)
        if ($null -ne $window -and $null -ne $window.FindFirst(
                [System.Windows.Automation.TreeScope]::Descendants, $condition)) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

$engineOsRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($TargetRoot)) {
    $TargetRoot = $IntAssistRoot
}
$targetPath = (Resolve-Path $TargetRoot).Path
$targetWindowTitle = if ($TargetKind -eq "EngineOS") { "EngineOS" } else { "IntAssist" }
$preselectedTargetIdentity = $null
if ($ObserveExistingTarget) {
    $preselectedTargetIdentity = Wait-ForTargetWindow -TimeoutSeconds 5
}
$runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$outputRootPath = (New-Item -ItemType Directory -Force -Path $OutputRoot).FullName
$packageRoot = Join-Path $outputRootPath $runId
$logsRoot = Join-Path $packageRoot "logs"
$testOutputRoot = Join-Path $packageRoot "test-output"
$gitRoot = Join-Path $packageRoot "git"
$processesRoot = Join-Path $packageRoot "processes"
$apiRoot = Join-Path $packageRoot "api"
$observationRoot = Join-Path $packageRoot "observation"
$timeline = [System.Collections.Generic.List[object]]::new()
$startedAt = (Get-Date).ToUniversalTime()
$mavenProcess = $null
$ownsTarget = -not $ObserveExistingTarget
$recorderProcess = $null
$targetWindowHandle = [IntPtr]::Zero
$targetProcessId = $null
$targetProcessStartTime = $null
$mavenExitCode = $null
$recordingError = $null
$uiOutput = [System.Collections.Generic.List[string]]::new()

New-Item -ItemType Directory -Force -Path $logsRoot, $testOutputRoot, $gitRoot,
    $processesRoot, $apiRoot, $observationRoot | Out-Null
Add-TimelineEvent $timeline "package-created" "Observation package directory created."

$ffmpeg = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
if ($null -eq $ffmpeg) {
    throw "ffmpeg.exe was not found on PATH. Automatic screen capture cannot start."
}

$stdoutPath = Join-Path $logsRoot "maven.stdout.log"
$stderrPath = Join-Path $logsRoot "maven.stderr.log"
$recordingPath = Join-Path $packageRoot "screen-recording.ts"
$scenarioName = if ($TargetKind -eq "EngineOS") {
    "EngineOS desktop application"
} else {
    "IntAssist visible Desktop Demo Mode"
}
$testSelector = if ($TargetKind -eq "EngineOS") {
    "EngineOsApplication via spring-boot:run"
} else {
    "DesktopApplication via javafx:run"
}

$engineOsStatus = Invoke-GitCapture $engineOsRoot @("status", "--short", "--branch")
$engineOsDiff = Invoke-GitCapture $engineOsRoot @("diff", "--no-ext-diff", "--binary")
$engineOsChangedFiles = Invoke-GitCapture $engineOsRoot @("diff", "--name-only")
[IO.File]::WriteAllText((Join-Path $gitRoot "engineos-status.txt"), $engineOsStatus.output)
[IO.File]::WriteAllText((Join-Path $gitRoot "engineos-diff.patch"), $engineOsDiff.output)
[IO.File]::WriteAllText((Join-Path $gitRoot "engineos-changed-files.txt"), $engineOsChangedFiles.output)

$targetGit = Invoke-GitCapture $targetPath @("status", "--short", "--branch")
[IO.File]::WriteAllText((Join-Path $gitRoot "$($TargetKind.ToLowerInvariant())-status.txt"), $targetGit.output)
Add-TimelineEvent $timeline "workspace-captured" "EngineOS and $TargetKind workspace state captured."

$ffmpegArguments = @(
    "-y",
    "-f", "gdigrab",
    "-framerate", "10",
    "-draw_mouse", "1",
    "-i", "desktop",
    "-c:v", "libx264",
    "-preset", "ultrafast",
    "-pix_fmt", "yuv420p",
    "-f", "mpegts",
    $recordingPath
) | ForEach-Object { Quote-ProcessArgument $_ }

$recorderInfo = [Diagnostics.ProcessStartInfo]::new()
$recorderInfo.FileName = $ffmpeg.Source
$recorderInfo.Arguments = ($ffmpegArguments -join " ")
$recorderInfo.UseShellExecute = $false
$recorderInfo.CreateNoWindow = $true
$recorderInfo.RedirectStandardInput = $true
$recorderInfo.RedirectStandardError = $true
$recorderProcess = [Diagnostics.Process]::new()
$recorderProcess.StartInfo = $recorderInfo
if (-not $recorderProcess.Start()) {
    throw "Unable to start ffmpeg screen capture."
}
$recorderErrorTask = $recorderProcess.StandardError.ReadToEndAsync()
Add-TimelineEvent $timeline "screen-recording-started" "FFmpeg desktop capture started."

try {
    $mavenCommandLine = if ($TargetKind -eq "EngineOS") {
        "$MavenCommand -q spring-boot:run"
    } else {
        "$MavenCommand -q -Djavafx.mainClass=com.example.interview.DesktopApplication javafx:run"
    }
    $mavenInfo = [Diagnostics.ProcessStartInfo]::new()
    $mavenInfo.FileName = "cmd.exe"
    $mavenInfo.Arguments = "/d /c $mavenCommandLine"
    $mavenInfo.WorkingDirectory = $targetPath
    $mavenInfo.UseShellExecute = $false
    $mavenInfo.CreateNoWindow = $true
    $mavenInfo.RedirectStandardOutput = $true
    $mavenInfo.RedirectStandardError = $true
    $existingJavaProcessIds = @()
    if ($ObserveExistingTarget) {
        Add-TimelineEvent $timeline "existing-target-selection-started" `
            "Searching only for the already-running $TargetKind window."
        $targetIdentity = $preselectedTargetIdentity
    }
    else {
        $existingJavaProcessIds = @(
            Get-Process java,javaw -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty Id
        )
        Add-TimelineEvent $timeline "pre-existing-processes-snapshotted" `
            "Excluded pre-existing Java/javaw process IDs: $($existingJavaProcessIds -join ', ')."
        $mavenProcess = [Diagnostics.Process]::new()
        $mavenProcess.StartInfo = $mavenInfo
        if (-not $mavenProcess.Start()) {
            throw "Unable to start Maven test process."
        }
        $stdoutTask = $mavenProcess.StandardOutput.ReadToEndAsync()
        $stderrTask = $mavenProcess.StandardError.ReadToEndAsync()
        Add-TimelineEvent $timeline "test-started" "Launched the $TargetKind application under controlled verification."
        $targetIdentity = Wait-ForTargetWindow -ExcludedProcessIds $existingJavaProcessIds
    }
    $targetProcessId = $targetIdentity.ProcessId
    $targetProcessStartTime = $targetIdentity.ProcessStartTime
    $targetWindow = $targetIdentity.Window
    Add-TimelineEvent $timeline "target-process-selected" `
        $(if ($ObserveExistingTarget) {
            "Selected existing $TargetKind process ID $targetProcessId without launching a replacement."
        } else {
            "Selected newly-created $TargetKind process ID $targetProcessId; pre-existing process IDs were excluded."
        })
    $targetProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $targetProcessId"
    [ordered]@{
        processId = $targetProcessId
        startTime = $targetProcessStartTime.ToUniversalTime().ToString("o")
        commandLine = if ($null -eq $targetProcess) { "" } else { $targetProcess.CommandLine }
        ownership = "verification-scenario"
        preExistingProcessIds = $existingJavaProcessIds
        windowTitle = $targetWindowTitle
    } | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $processesRoot "target-process.json") -Encoding UTF8
    $targetWindowHandle = $targetWindow.Current.NativeWindowHandle
    if ($targetWindowHandle -eq 0) {
        throw "$TargetKind window did not expose a native window handle."
    }
    $targetWindowHandle = [IntPtr]$targetWindowHandle
    Set-TargetWindowVisibility $targetWindowHandle
    Add-TimelineEvent $timeline "ui-ready" "$TargetKind window found; foreground policy applied."

    if ($TargetKind -eq "IntAssist") {
        $startDemoButton = $null
        for ($attempt = 0; $attempt -lt 20 -and $null -eq $startDemoButton; $attempt++) {
            $targetWindow = [System.Windows.Automation.AutomationElement]::FromHandle($targetWindowHandle)
            $startDemoButton = Find-UiElementByAutomationId $targetWindow "JavaFX24"
            if ($null -eq $startDemoButton) {
                Start-Sleep -Milliseconds 500
            }
        }
        if ($null -eq $startDemoButton) {
            throw "Could not find the existing IntAssist Start Demo button."
        }
        $invokePattern = $startDemoButton.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
        $invokePattern.Invoke()
        $uiOutput.Add("Invoked existing Start Demo button (AutomationId JavaFX24).")
        Add-TimelineEvent $timeline "demo-started" "Invoked the existing Start Demo control through Windows UI Automation."

        if (-not (Wait-ForUiName $targetWindowHandle "Demo complete" 20)) {
            throw "Timed out waiting for IntAssist Demo complete status."
        }
        $uiOutput.Add("Observed existing Demo complete status.")
        Add-TimelineEvent $timeline "demo-completed" "The deterministic IntAssist demo reported Demo complete."
    } else {
        $sendButton = Wait-ForUiElementByName $targetWindowHandle "SEND TO CODING AGENT"
        $beforeNames = Get-UiNames $targetWindow
        $beforeNames | Set-Content -Path (Join-Path $observationRoot "ui-state-before-send.txt") -Encoding UTF8
        $invokePattern = $sendButton.GetCurrentPattern(
            [System.Windows.Automation.InvokePattern]::Pattern)
        $invokePattern.Invoke()
        $uiOutput.Add("Invoked SEND TO CODING AGENT through Windows UI Automation.")
        Add-TimelineEvent $timeline "send-to-coding-agent-invoked" `
            "Invoked the existing SEND TO CODING AGENT control without launching a replacement Desktop."

        $afterNames = Get-UiNames ([System.Windows.Automation.AutomationElement]::FromHandle($targetWindowHandle))
        $afterNames | Set-Content -Path (Join-Path $observationRoot "ui-state-after-send.txt") -Encoding UTF8
        Add-TimelineEvent $timeline "ui-state-captured" `
            "Captured the immediate UI state after SEND TO CODING AGENT."

        $stateDeadline = (Get-Date).AddSeconds(10)
        $stateSnapshots = [System.Collections.Generic.List[object]]::new()
        do {
            $snapshotNames = Get-UiNames ([System.Windows.Automation.AutomationElement]::FromHandle($targetWindowHandle))
            $stateSnapshots.Add([pscustomobject]@{
                timestamp = (Get-Date).ToUniversalTime().ToString("o")
                names = $snapshotNames
            })
            if ($snapshotNames -match "CODING AGENT ACTIVITY|Request running|Engineering Result|Observation") {
                break
            }
            Start-Sleep -Milliseconds 250
        } while ((Get-Date) -lt $stateDeadline)
        $stateSnapshots | ConvertTo-Json -Depth 5 |
            Set-Content -Path (Join-Path $observationRoot "ui-state-progress.json") -Encoding UTF8
        Add-TimelineEvent $timeline "ui-progress-captured" `
            "Captured bounded post-action UI progress evidence."
    }

    if ($ownsTarget) {
        [EngineOsWindowAutomation]::PostMessage($targetWindowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        Add-TimelineEvent $timeline "ui-stopped" "Closed the $TargetKind window after observation."
        $mavenProcess.WaitForExit()
        [IO.File]::WriteAllText($stdoutPath, $stdoutTask.Result)
        [IO.File]::WriteAllText($stderrPath, $stderrTask.Result)
        $mavenExitCode = $mavenProcess.ExitCode
        Add-TimelineEvent $timeline "test-finished" "Maven exited with code $mavenExitCode."
    }
    else {
        $mavenExitCode = 0
        Add-TimelineEvent $timeline "existing-target-preserved" `
            "Existing $TargetKind window was verified and left running."
    }
}
finally {
    if ($ownsTarget -and $targetProcessId -ne $null -and $targetWindowHandle -ne [IntPtr]::Zero) {
        $targetProcess = Get-Process -Id $targetProcessId -ErrorAction SilentlyContinue
        if ($null -ne $targetProcess -and
            $targetProcess.StartTime -eq $targetProcessStartTime -and
            $targetProcess.MainWindowHandle -eq $targetWindowHandle.ToInt64()) {
            [EngineOsWindowAutomation]::PostMessage(
                $targetWindowHandle, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        }
    }
    if ($ownsTarget -and $targetProcessId -ne $null) {
        $targetProcess = Get-Process -Id $targetProcessId -ErrorAction SilentlyContinue
        if ($null -ne $targetProcess -and
            $targetProcess.StartTime -eq $targetProcessStartTime -and
            -not $targetProcess.HasExited) {
            Stop-Process -Id $targetProcessId -Force
            Add-TimelineEvent $timeline "target-process-stopped" `
                "Controlled target process ID $targetProcessId was terminated during cleanup."
        }
        $remainingTargetProcess = Get-Process -Id $targetProcessId -ErrorAction SilentlyContinue
        if ($null -eq $remainingTargetProcess -or
            $remainingTargetProcess.StartTime -ne $targetProcessStartTime) {
            Add-TimelineEvent $timeline "target-process-cleanup-verified" `
                "Controlled target process ID $targetProcessId (started $targetProcessStartTime) is no longer running; pre-existing processes were not targeted."
        }
    }
    if ($ownsTarget -and $null -ne $mavenProcess -and -not $mavenProcess.HasExited) {
        Stop-Process -Id $mavenProcess.Id -Force
        Add-TimelineEvent $timeline "launcher-process-stopped" `
            "Controlled Maven launcher process ID $($mavenProcess.Id) was terminated during cleanup."
    }
    if ($null -ne $recorderProcess -and -not $recorderProcess.HasExited) {
        try {
            $recorderProcess.StandardInput.WriteLine("q")
            $recorderProcess.StandardInput.Flush()
            $recorderProcess.StandardInput.Close()
            if (-not $recorderProcess.WaitForExit(3000)) {
                Stop-Process -Id $recorderProcess.Id -Force
                Add-TimelineEvent $timeline "screen-recording-stopped" "FFmpeg did not stop gracefully and was terminated."
            }
            else {
                Add-TimelineEvent $timeline "screen-recording-stopped" "FFmpeg desktop capture stopped."
            }
        }
        catch {
            $recordingError = $_.Exception.Message
            if (-not $recorderProcess.HasExited) {
                Stop-Process -Id $recorderProcess.Id -Force
            }
            Add-TimelineEvent $timeline "screen-recording-error" $recordingError
        }
    }
    if ($null -ne $recorderErrorTask) {
        [IO.File]::WriteAllText((Join-Path $logsRoot "ffmpeg.stderr.log"), $recorderErrorTask.Result)
    }
}

$surefireFiles = Get-ChildItem -Path (Join-Path $targetPath "target\surefire-reports") `
    -Filter "*DesktopDemoModeTest*" -File -ErrorAction SilentlyContinue
foreach ($file in $surefireFiles) {
    Copy-Item $file.FullName (Join-Path $testOutputRoot $file.Name) -Force
}
$uiOutput | Set-Content -Path (Join-Path $testOutputRoot "visible-desktop-demo.txt") -Encoding UTF8
[ordered]@{
    targetKind = $TargetKind
    windowTitle = $targetWindowTitle
    processId = $targetProcessId
    uiActions = @($uiOutput)
    applicationExitCode = $mavenExitCode
} | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $observationRoot "ui-state.json") -Encoding UTF8
Add-TimelineEvent $timeline "evidence-captured" "Maven logs, UI scenario output, and any matching Surefire reports copied."

$finishedAt = (Get-Date).ToUniversalTime()
$manifest = [ordered]@{
    format = "engineos-observation-package-v1"
    runId = $runId
    scenario = [ordered]@{
            project = $TargetKind
            workspace = $targetPath
        name = $scenarioName
        testSelector = $testSelector
            command = $mavenCommandLine
    }
    startedAt = $startedAt.ToString("o")
    finishedAt = $finishedAt.ToString("o")
    durationSeconds = [math]::Round(($finishedAt - $startedAt).TotalSeconds, 3)
    result = [ordered]@{
        exitCode = $mavenExitCode
        succeeded = ($mavenExitCode -eq 0)
    }
    workspaces = [ordered]@{
        engineos = [ordered]@{
            root = $engineOsRoot
            gitAvailable = $engineOsStatus.available
            status = "git\engineos-status.txt"
            diff = "git\engineos-diff.patch"
            changedFiles = "git\engineos-changed-files.txt"
        }
        target = [ordered]@{
            root = $targetPath
            kind = $TargetKind
            gitAvailable = $targetGit.available
            status = "git\$($TargetKind.ToLowerInvariant())-status.txt"
        }
    }
    evidence = @(
        "manifest.json",
        "timeline.json",
        "screen-recording.ts",
        "logs\maven.stdout.log",
        "logs\maven.stderr.log",
        "logs\ffmpeg.stderr.log",
        "test-output",
        "git",
        "processes",
        "api",
        "observation"
    )
    limitations = @(
        "The visible scenario requires an interactive Windows desktop session and Windows UI Automation.",
        "The target window is kept from foreground activation unless -ShowTarget is supplied."
    )
}

$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $packageRoot "manifest.json") -Encoding UTF8
$timeline | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $packageRoot "timeline.json") -Encoding UTF8

Write-Output "Observation package: $packageRoot"
if ($mavenExitCode -ne 0) {
    exit $mavenExitCode
}
