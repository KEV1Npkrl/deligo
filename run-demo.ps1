$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'C:/Program Files/Java/jdk-25/bin/java.exe')) {
    $env:JAVA_HOME = 'C:/Program Files/Java/jdk-25'
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
& mvn -B -ntp package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar. Verifica Java 25 y Maven.' }
Write-Host 'Demo local: http://localhost:8080. Usa solo datos ficticios. Ctrl+C para detener.'
& java -jar target/deligo-0.1.0.jar --spring.profiles.active=demo
