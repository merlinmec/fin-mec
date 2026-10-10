# Registra a atualização automática do fin-mec que roda no próprio computador (Windows).
# Cria uma tarefa agendada do usuário atual (sem administrador) que roda deploy/local-update.ps1
# no login e a cada 15 minutos. Rodar de novo apenas atualiza a tarefa.
#
# Uso, de dentro do clone de produção (a pasta que acompanha só a main):
#   powershell -NoProfile -ExecutionPolicy Bypass -File deploy\install-local-autoupdate.ps1
# Para remover:  Unregister-ScheduledTask -TaskName "fin-mec atualizacao" -Confirm:$false
param([int]$IntervalMinutes = 15)

$ErrorActionPreference = "Stop"
$prodDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$script = Join-Path $prodDir "deploy\local-update.ps1"
$dataDir = Join-Path $env:LOCALAPPDATA "fin-mec"
New-Item -ItemType Directory -Force $dataDir | Out-Null

# Chamar o powershell.exe direto pelo agendador abre uma janela preta a cada execução, mesmo com
# -WindowStyle Hidden. O wscript roda o comando com a janela oculta (estilo 0).
$launcher = Join-Path $dataDir "atualizar-oculto.vbs"
$command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File ""$script"" -ProdDir ""$prodDir"""
@"
' Gerado por deploy\install-local-autoupdate.ps1: roda a atualizacao do fin-mec sem abrir janela.
CreateObject("WScript.Shell").Run "$($command.Replace('"', '""'))", 0, True
"@ | Set-Content -Path $launcher -Encoding ASCII

$action = New-ScheduledTaskAction -Execute "wscript.exe" -Argument "`"$launcher`""
# Dois gatilhos: no login (com 1 minuto para o Docker Desktop começar a subir) e um relógio que
# repete o intervalo sem fim. A repetição presa ao gatilho de login só começaria no próximo login.
$atLogOn = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$atLogOn.Delay = "PT1M"
$every = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes)
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 1)
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName "fin-mec atualizacao" -Action $action -Trigger @($atLogOn, $every) `
    -Settings $settings -Principal $principal -Force | Out-Null

Write-Output "Tarefa 'fin-mec atualizacao' registrada: no login e a cada $IntervalMinutes min."
Write-Output "Clone de produção: $prodDir"
Write-Output "Log: $(Join-Path $dataDir 'update.log')"
