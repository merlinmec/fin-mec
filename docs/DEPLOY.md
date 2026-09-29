# Deploy do fin-mec

Guia para colocar o fin-mec no ar numa VPS com Docker. Tudo roda num `docker compose`:
Caddy (HTTPS automático) → aplicação (jar único: API + SPA) → Postgres, mais um serviço de
backup diário. Testado de ponta a ponta localmente (build, HTTPS, cadastro, recuperação de
senha, backup e restauração).

## Arquitetura

```
Internet ──443──▶ Caddy ──8080──▶ app (Spring Boot, perfil prod) ──▶ Postgres
                  (TLS)            actuator/métricas: 9090 (só rede interna)   ▲
                                                                              │
                                                    backup (pg_dump diário) ──┘
```

- Só o Caddy publica portas (80/443). Banco e aplicação ficam na rede interna do compose.
- Sessões ficam no Postgres (Spring Session JDBC): deploy/restart não desloga ninguém, e dá para
  rodar mais de uma réplica da aplicação (o job diário usa ShedLock para rodar em uma só).
- O IP real do usuário chega via `X-Forwarded-For` do Caddy; a aplicação só confia nesse cabeçalho
  vindo da rede interna (`server.forward-headers-strategy: native`).

## Requisitos

- VPS Linux com Docker e o plugin Compose (1 vCPU / 2 GB de RAM bastam para uso pessoal).
- Um domínio com registro A/AAAA apontando para a VPS (o Caddy precisa dele para emitir o HTTPS).
- Portas 80 e 443 liberadas no firewall.
- Uma conta num provedor de e-mail transacional com SMTP (Brevo, Resend, Amazon SES, Mailgun…),
  para a recuperação de senha e os alertas de acesso.

## Passo a passo

```bash
git clone https://github.com/merlinmec/fin-mec.git && cd fin-mec
cp .env.prod.example .env.prod
```

Preencha o `.env.prod`:

| Variável | Como obter |
|---|---|
| `DOMAIN` / `MECFIN_BASE_URL` | seu domínio / `https://seu-dominio` |
| `DB_PASSWORD` | `openssl rand -base64 24` |
| `MECFIN_ENCRYPTION_KEY` | `openssl rand -base64 32` — **guarde uma cópia fora da VPS**: ela cifra os segredos do 2FA; perdê-la tranca fora quem usa 2FA |
| `MAIL_*` | credenciais SMTP do provedor; o remetente de `MAIL_FROM` precisa estar verificado nele |

Suba:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod ps     # app deve ficar "healthy"
```

Na primeira subida o Flyway cria todo o schema. Abra `https://seu-dominio`, crie sua conta e ative a
verificação em duas etapas em Configurações → Segurança.

## Atualizar

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build app
```

As migrations novas rodam sozinhas no boot. Sessões sobrevivem à troca do container.

## Backup e restauração

- O serviço `backup` faz `pg_dump` diário em `/backups` (volume `backups`), confere cada arquivo com
  `pg_restore --list` e mantém `BACKUP_KEEP_DAYS` dias.
- **Prove que o backup restaura** (depois do primeiro deploy e periodicamente):

  ```bash
  docker compose -f docker-compose.prod.yml --env-file .env.prod exec backup bash /scripts/restore-check.sh
  # [restore-check] OK — migrations até V18, N usuário(s)
  ```

- Backup só na própria VPS não protege contra perder a VPS. Copie para fora, por exemplo com
  `rclone` para um bucket S3/B2 via cron no host:

  ```bash
  docker run --rm -v fin-mec_backups:/backups:ro -v ~/.config/rclone:/config/rclone \
    rclone/rclone copy /backups remote:fin-mec-backups --max-age 48h
  ```

- Restauração de verdade (desastre): pare o `app`, `dropdb` + `createdb` e `pg_restore` do dump no
  serviço `db`, suba o `app` de novo.

## Observabilidade

- Logs em JSON no stdout (`docker compose logs -f app`), cada linha com o `requestId` da requisição
  — o mesmo que volta no cabeçalho `X-Request-Id` para o navegador.
- Métricas Prometheus em `http://app:9090/api/actuator/prometheus`, alcançável só da rede interna
  (aponte um Prometheus/Grafana Agent na mesma rede). Para alertar em erro 5xx, a métrica é
  `http_server_requests_seconds_count{status=~"5.."}`.
- Health: `/api/actuator/health` (porta 9090) — usado pelo healthcheck do container.

## Checklist de segurança antes de abrir para outras pessoas

- [ ] `.env.prod` fora do git (já está no `.gitignore`) e com permissão `600`.
- [ ] `MECFIN_ENCRYPTION_KEY` copiada para um cofre de senhas.
- [ ] SMTP configurado e testado ("Esqueci minha senha" chegando).
- [ ] `restore-check.sh` executado com sucesso e cópia externa do backup funcionando.
- [ ] Firewall: só 22 (SSH com chave), 80 e 443.
