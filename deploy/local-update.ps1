# Atualiza o fin-mec que roda no próprio computador (ver docs/DEPLOY.md, "Rodar no próprio
# computador"). Feito para uma tarefa agendada do Windows: no login e a cada poucos minutos.
#
# - Só age quando a main do GitHub mudou. A main só recebe código com a CI verde (ruleset), então
#   o que chega aqui já passou por testes, lint e build da imagem.
# - Antes de trocar a versão, faz um backup extra do banco (as migrations novas rodam no boot).
# - Sempre termina com "up -d": se algum container tiver parado, volta.
#
# Uso: powershell -NoProfile -ExecutionPolicy Bypass -File local-update.ps1 -ProdDir <clone da main>
param(
  [Parameter(Mandatory = $true)][string]$ProdDir,
  [string]$LogFile = "$env:LOCALAPPDATA\fin-mec\update.log"
)

# "Continue", não "Stop": no PowerShell 5.1, com "Stop", qualquer linha que git/docker escrevem no
# stderr (avisos, progresso) vira exceção. Falha de verdade é decidida pelo código de saída (Run).
$ErrorActionPreference = "Continue"
New-Item -ItemType Directory -Force (Split-Path $LogFile) | Out-Null
function Log($msg) { "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg" | Add-Content -Path $LogFile -Encoding utf8 }

function Run([string]$exe, [string[]]$argv) {
  $out = & $exe @argv 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0) { throw "$exe $($argv -join ' ') falhou ($LASTEXITCODE): $out" }
  return $out.Trim()
}

$compose = @("compose", "-f", "docker-compose.prod.yml", "-f", "docker-compose.local.yml", "--env-file", ".env.prod")

try {
  Set-Location $ProdDir -ErrorAction Stop

  # No login o Docker Desktop ainda está iniciando: espera até 5 minutos.
  $ready = $false
  for ($i = 0; $i -lt 60; $i++) {
    & docker info *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 5
  }
  if (-not $ready) { Log "Docker não respondeu em 5 minutos; tento de novo na próxima execução."; exit 0 }

  Run git @("fetch", "--quiet", "origin", "main") | Out-Null
  $local = Run git @("rev-parse", "HEAD")
  $remote = Run git @("rev-parse", "origin/main")

  if ($local -ne $remote) {
    Log "Nova versão: $($local.Substring(0, 7)) -> $($remote.Substring(0, 7))"
    $stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
    Run docker ($compose + @("exec", "-T", "backup", "pg_dump", "-Fc", "-f", "/backups/fin-mec-$stamp-antes-update.dump")) | Out-Null
    Log "Backup antes da atualização: fin-mec-$stamp-antes-update.dump"
    Run git @("merge", "--ff-only", "--quiet", "origin/main") | Out-Null
    Run docker ($compose + @("up", "-d", "--build", "--remove-orphans")) | Out-Null
    Log "Atualizado para $($remote.Substring(0, 7))."
  } else {
    # Sem versão nova: garante que tudo está de pé (barato quando já está).
    Run docker ($compose + @("up", "-d")) | Out-Null
  }
} catch {
  Log "ERRO: $($_.Exception.Message)"
  exit 1
}
