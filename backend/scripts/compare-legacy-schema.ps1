param(
    [Parameter(Mandatory=$true)][string]$CurrentDump,
    [Parameter(Mandatory=$true)][string]$ReferenceDump
)
$ErrorActionPreference = 'Stop'
function Read-NormalizedDump([string]$Path) {
    return @(Get-Content -LiteralPath $Path | Where-Object {
        $_ -notmatch '^\s*--' -and $_ -notmatch '^\s*$' -and $_ -notmatch '^\\' -and
        $_ -notmatch '^SET ' -and $_ -notmatch '^SELECT pg_catalog.set_config'
    } | ForEach-Object { $_.Trim() })
}
$expected=Read-NormalizedDump $ReferenceDump
$actual=Read-NormalizedDump $CurrentDump
if (($expected -join "`n") -cne ($actual -join "`n")) {
    throw 'Schema differs from the selected recorded OCI baseline. Do not baseline or start migration; reconcile the schema explicitly.'
}
Write-Output 'Current schema matches the selected recorded OCI baseline. This read-only check did not adopt or migrate the database.'
