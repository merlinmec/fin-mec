# Demonstração pública (custo zero)

A demonstração deixa qualquer pessoa testar o fin-mec sem cadastro: o link `/demo` cria uma conta
de visitante temporária, já com seis meses de dados fictícios, e entra direto no dashboard. É o
destino do botão "Testar" do portfólio.

## Como funciona

- **Perfil `demo`** (`SPRING_PROFILES_ACTIVE=prod,demo`): liga o módulo `demo`. Fora dele, nada
  disso existe (`/api/demo/session` responde 404).
- **Uma conta por visitante**, criada pelo cadastro normal e preenchida pelos serviços de cada
  módulo (`DemoDataSeeder`), com datas relativas a hoje. Um visitante nunca vê nem bagunça os dados
  de outro.
- **Apagada depois de 24 h** pelo mesmo caminho da exclusão de conta (LGPD), com as sessões
  derrubadas na hora. A limpeza roda a cada hora e também antes de cada visitante novo, porque o
  servidor gratuito pode passar horas sem rodar job.
- **Bloqueado na demo**: cadastro de conta real, troca e recuperação de senha, 2FA, convites por
  e-mail, upload de comprovante e conexão bancária (`DemoModeGuard`).
- **Proteções do banco gratuito**: no máximo 300 visitantes ativos e 10 contas por IP por hora.
- **Aquecimento**: ao subir, o servidor cria e apaga uma conta de visitante, para o primeiro
  recrutador de verdade não pagar a JVM "fria".

Medido com os limites do plano gratuito do Render (512 MB, 0,1 CPU): a aplicação usa cerca de
400 MB, sobe em cerca de 3 minutos, e cada visitante novo leva cerca de 5 a 9 segundos para ficar
pronto.

## Passo a passo

### 1. Banco no Neon (gratuito)

1. Crie uma conta em https://neon.tech e um projeto chamado `fin-mec-demo`, na região
   **AWS US West 2 (Oregon)**, a mesma do serviço no Render.
2. Em **Connect**, desligue "Connection pooling" e copie os dados da conexão **direta** (o host
   **não** pode ter `-pooler`).
3. Monte a URL JDBC:
   `jdbc:postgresql://<host>/<banco>?sslmode=require`
   (ex.: `jdbc:postgresql://ep-xxx-123.us-west-2.aws.neon.tech/neondb?sslmode=require`).
   Guarde também o usuário e a senha.

O Flyway cria o schema sozinho na primeira subida.

### 2. Serviço no Render (gratuito)

1. Crie uma conta em https://render.com entrando com o GitHub.
2. **New → Blueprint**, escolha o repositório `fin-mec`. O Render lê o `render.yaml` da raiz.
3. Ele pede os valores marcados como segredo:
   - `SPRING_DATASOURCE_URL`: a URL JDBC do passo 1;
   - `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`: do Neon;
   - `MECFIN_BASE_URL`: `https://fin-mec-demo.onrender.com` (confira o endereço que o Render
     mostrar; se o nome estiver ocupado, ele acrescenta um sufixo).
4. Aguarde o primeiro deploy (o build leva alguns minutos e a subida cerca de 3). Abra
   `https://<seu-endereço>/demo`.

Depois disso, todo merge na `main` publica a demo sozinho (`autoDeploy`).

### 3. Manter a demo acordada (gratuito)

O plano gratuito do Render "dorme" após 15 minutos sem acesso, e acordar leva cerca de 3 minutos,
tempo demais para quem clicou em "Testar". Um ping a cada 10 minutos evita isso. As 750 horas
gratuitas do Render cobrem o mês inteiro de um serviço.

1. Crie uma conta em https://cron-job.org.
2. Crie um cron job para `https://<seu-endereço>/api/demo/status`, a cada 10 minutos.

Esse endereço não consulta o banco, então o Neon continua dormindo entre um uso e outro e não gasta
as horas de computação gratuitas.

### 4. Botão no portfólio

Preencha `demoUrl` do fin-mec em `src/data/projects.js` do repositório `portfolio` com
`https://<seu-endereço>/demo`.
