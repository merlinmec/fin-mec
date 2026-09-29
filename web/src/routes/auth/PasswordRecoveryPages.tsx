import { useEffect, useState, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle2, MailCheck } from "lucide-react";
import { forgotPassword, resetPassword } from "@/api/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getErrorMessage } from "@/lib/errors";

/** Casca das telas públicas de recuperação (mesmo visual de login/cadastro). */
function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh place-items-center bg-muted/40 p-6">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
            f
          </span>
          <span className="text-lg font-semibold tracking-tight">fin-mec</span>
        </div>
        <div className="rounded-2xl border border-border/60 bg-card p-6 text-card-foreground shadow-sm">
          <h1 className="text-lg font-semibold tracking-tight">{title}</h1>
          {children}
        </div>
      </div>
    </div>
  );
}

/**
 * "Esqueci minha senha". A resposta da tela é a mesma exista a conta ou não — o backend também
 * responde igual, para ninguém descobrir quais e-mails têm conta.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      setError(getErrorMessage(err, "Não foi possível enviar agora. Tente de novo em instantes."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Esqueci minha senha">
      {sent ? (
        <div className="mt-4 space-y-4 text-center">
          <MailCheck className="mx-auto size-10 text-primary" />
          <p className="text-sm text-muted-foreground">
            Se houver uma conta com{" "}
            <strong className="font-semibold text-foreground">{email}</strong>, enviamos um link
            para criar uma nova senha. Ele vale por 30 minutos — confira também o spam.
          </p>
          <Link
            to="/login"
            className="inline-block text-sm font-medium text-primary hover:underline"
          >
            Voltar para o login
          </Link>
        </div>
      ) : (
        <>
          <p className="mt-1 text-sm text-muted-foreground">
            Informe o e-mail da conta e enviaremos um link de redefinição.
          </p>
          <form className="mt-5 space-y-4" onSubmit={(e) => void submit(e)} noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="forgot-email">E-mail</Label>
              <Input
                id="forgot-email"
                type="email"
                autoComplete="email"
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            {error && (
              <p
                role="alert"
                className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {error}
              </p>
            )}
            <Button type="submit" className="w-full" disabled={busy || !email.includes("@")}>
              {busy ? "Enviando…" : "Enviar link"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Lembrou?{" "}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Entrar
            </Link>
          </p>
        </>
      )}
    </AuthCard>
  );
}

/** Destino do link do e-mail: /redefinir-senha?token=... */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  // O token sai da barra de endereço assim que é lido: não fica no histórico nem em prints.
  const [token] = useState(() => searchParams.get("token") ?? "");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (searchParams.has("token")) {
      window.history.replaceState(null, "", "/redefinir-senha");
    }
  }, [searchParams]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password.length < 10) return setError("A senha precisa de pelo menos 10 caracteres.");
    if (password !== confirm) return setError("A confirmação não confere.");
    setBusy(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(getErrorMessage(err, "Não foi possível redefinir a senha."));
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthCard title="Link incompleto">
        <p className="mt-2 text-sm text-muted-foreground">
          Abra o link exatamente como veio no e-mail, ou peça um novo.
        </p>
        <Link
          to="/esqueci-senha"
          className="mt-4 inline-block text-sm font-medium text-primary hover:underline"
        >
          Pedir um novo link
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title={done ? "Senha redefinida" : "Criar nova senha"}>
      {done ? (
        <div className="mt-4 space-y-4 text-center">
          <CheckCircle2 className="mx-auto size-10 text-success" />
          <p className="text-sm text-muted-foreground">
            Pronto. Por segurança, todas as sessões abertas foram encerradas. Entre com a nova
            senha.
          </p>
          <Button className="w-full" onClick={() => void navigate("/login", { replace: true })}>
            Entrar
          </Button>
        </div>
      ) : (
        <form className="mt-5 space-y-4" onSubmit={(e) => void submit(e)} noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="reset-password">Nova senha</Label>
            <Input
              id="reset-password"
              type="password"
              autoComplete="new-password"
              autoFocus
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="reset-confirm">Confirmar nova senha</Label>
            <Input
              id="reset-confirm"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            Mínimo de 10 caracteres. Uma frase com palavras aleatórias é forte e fácil de lembrar.
          </p>
          {error && (
            <p
              role="alert"
              className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {error}{" "}
              {error.includes("expirou") && (
                <Link to="/esqueci-senha" className="font-medium underline">
                  Pedir outro link
                </Link>
              )}
            </p>
          )}
          <Button type="submit" className="w-full" disabled={busy}>
            {busy ? "Salvando…" : "Salvar nova senha"}
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
