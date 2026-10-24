param(
    [string]$AdminUrl = 'jdbc:postgresql://127.0.0.1:5099/postgres',
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '../.env'),
    [string]$Tests = 'DurableJobsIntegrationTest,PostgresDurableJobsTest,PostgresMigrationTest',
    [string]$TestIncludes = 'com/mercato/jobs/*.java'
)
$ErrorActionPreference = 'Stop'
if ($AdminUrl -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):\d+/[a-zA-Z0-9_]+$') {
    throw 'Only a loopback PostgreSQL administrative database is accepted.'
}
$credentials = @{}
foreach ($line in Get-Content -LiteralPath $EnvironmentFile) {
    if ($line -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $credentials[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
}
if (-not $credentials.ContainsKey('DB_USERNAME') -or -not $credentials.ContainsKey('DB_PASSWORD')) {
    throw 'DB_USERNAME and DB_PASSWORD are required in the local environment file.'
}
$previous = @{}
foreach ($name in @('MERCATO_TEST_POSTGRES_URL','MERCATO_TEST_POSTGRES_USERNAME','MERCATO_TEST_POSTGRES_PASSWORD')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
}
try {
    $env:MERCATO_TEST_POSTGRES_URL = $AdminUrl
    $env:MERCATO_TEST_POSTGRES_USERNAME = $credentials['DB_USERNAME']
    $env:MERCATO_TEST_POSTGRES_PASSWORD = $credentials['DB_PASSWORD']
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        $mavenArgs = @('-q',"-Dtest=$Tests","-Dmercato.testIncludes=$TestIncludes")
        $agentPath = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar'
        if (Test-Path -LiteralPath $agentPath) { $mavenArgs += "-DargLine=-javaagent:$agentPath" }
        & ./mvnw.cmd @mavenArgs test
        if ($LASTEXITCODE -ne 0) { throw "Foundation tests failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
