[CmdletBinding()]
param(
    [ValidateSet('status','config','config-import','quota-set','rule-set','target-add','protections-set','lock-timer','refresh')]
    [string]$Method = 'status',
    [hashtable]$Values = @{},
    [string]$ProfilePath
)

$ErrorActionPreference = 'Stop'

# adb joins shell arguments into a remote command. Quote for Android's shell,
# not PowerShell: regexes and names can contain spaces, quotes, $ or semicolons.
function Quote-AndroidArgument([string]$Value) {
    $singleQuote = [string][char]39
    $escapedQuote = $singleQuote + [char]34 + $singleQuote + [char]34 + $singleQuote
    return $singleQuote + $Value.Replace($singleQuote, $escapedQuote) + $singleQuote
}

if ($ProfilePath) {
    if ($Method -ne 'config-import') { throw 'ProfilePath is only for config-import.' }
    $profile = Get-Item -LiteralPath $ProfilePath
    if ($profile.PSIsContainer -or $profile.Length -gt 65536) { throw 'ADB profiles must be files no larger than 64 KiB; use UI import for larger files.' }
    $Values = $Values.Clone()
    $Values['json'] = [System.IO.File]::ReadAllText($profile.FullName, [System.Text.Encoding]::UTF8)
}
$remoteArguments = @('content','call','--uri','content://io.github.pesterevnikita.focusgate.control','--method',$Method)
$arguments = [ordered]@{}
foreach ($key in ($Values.Keys | Sort-Object)) {
    if ($key -notmatch '^[A-Za-z][A-Za-z0-9]*$') { throw 'Invalid argument name.' }
    $arguments[$key] = [string]$Values[$key]
}
# Android's --extra binding parser rejects colons inside values, including JSON
# and URL regexes. A single JSON argument preserves every named string value.
if ($arguments.Count -gt 0) {
    $remoteArguments += @('--arg', ($arguments | ConvertTo-Json -Compress -Depth 4))
}
$remoteCommand = ($remoteArguments | ForEach-Object { Quote-AndroidArgument $_ }) -join ' '
# Send the quoted command over stdin: Windows PowerShell 5.1 strips embedded
# double quotes when passing native argv, which would corrupt POSIX quoting.
if ([System.Text.Encoding]::UTF8.GetByteCount($remoteCommand) -gt 98304) { throw 'Command exceeds the bounded shell transport; use UI import for larger profiles.' }
$previousEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    # The final comment absorbs PowerShell's appended CRLF; the command ends with LF.
    $output = ((($remoteCommand + [char]10) + '#') | & adb shell sh -s 2>&1 | Out-String).Trim()
    $adbExitCode = $LASTEXITCODE
} finally { $OutputEncoding = $previousEncoding }
if ($adbExitCode -ne 0) { throw $output }

# Android's content CLI wraps the JSON string in Result: Bundle[{json=...}].
# Return a PowerShell object so callers can select IDs without copying from UI.
$match = [regex]::Match($output, '(?s)json=(\{.*\})(?=\}\]$)')
if (-not $match.Success) { throw ('No FocusGate JSON response: ' + $output) }
$result = $match.Groups[1].Value | ConvertFrom-Json
if (-not $result.ok) { throw $result.error }
$result
