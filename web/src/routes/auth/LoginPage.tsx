import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAuth } from "@/auth/auth-context";
import { getErrorMessage } from "@/lib/errors";
import { nextQuery, safeNext } from "@/lib/safe-next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { loginSchema, type LoginFormValues } from "./login-schema";

/** POST /api/auth/login. Redireciona para ?next= (se houver) ou para a raiz. */
export function LoginPage() {
  const { login, verifyMfa } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [formError, setFormError] = useState<string | null>(null);
  const [mfaStep, setMfaStep] = useState(false);
  const [code, setCode] = useState("");
  const [verifying, setVerifying] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  function goNext() {
    void navigate(safeNext(searchParams.get("next")), { replace: true });
  }

  async function onSubmit(values: LoginFormValues) {
    setFormError(null);
    try {
      const result = await login(values);
      if (result === "mfa") {
        setMfaStep(true);
        return;
      }
      goNext();
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível entrar. Tente novamente."));
    }
  }

  async function onVerify(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setVerifying(true);
    try {
      await verifyMfa(code.trim());
      goNext();
    } catch (err) {
      setFormError(getErrorMessage(err, "Código inválido. Tente de novo."));
      setCode("");
    } finally {
      setVerifying(false);
    }
  }

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
          {mfaStep ? (
            <>
              <h1 className="text-lg font-semibold tracking-tight">Verificação em duas etapas</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Digite o código de 6 dígitos do seu app autenticador. Sem o celular? Use um código
                de recuperação.
              </p>
              <form className="mt-5 space-y-4" onSubmit={(e) => void onVerify(e)} noValidate>
                <div className="space-y-1.5">
                  <Label htmlFor="mfa-code">Código</Label>
                  <Input
                    id="mfa-code"
                    autoFocus
                    autoComplete="one-time-code"
                    inputMode="text"
                    maxLength={20}
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    className="num h-12 text-center text-xl tracking-[0.3em]"
                    placeholder="000000"
                  />
                </div>
                {formError && (
                  <p
                    role="alert"
                    className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
                  >
                    {formError}
                  </p>
                )}
                <Button
                  type="submit"
                  className="w-full"
                  disabled={verifying || code.trim().length < 6}
                >
                  {verifying ? "Verificando…" : "Verificar e entrar"}
                </Button>
                <button
                  type="button"
                  onClick={() => {
                    setMfaStep(false);
                    setFormError(null);
                    setCode("");
                  }}
                  className="w-full text-center text-sm text-muted-foreground hover:text-foreground"
                >
                  Voltar
                </button>
              </form>
            </>
          ) : (
            <>
              <h1 className="text-lg font-semibold tracking-tight">Entrar</h1>
              <p className="mt-1 text-sm text-muted-foreground">Controle financeiro pessoal.</p>

              <form
                className="mt-5 space-y-4"
                onSubmit={(e) => void handleSubmit(onSubmit)(e)}
                noValidate
              >
                <div className="space-y-1.5">
                  <Label htmlFor="email">E-mail</Label>
                  <Input
                    id="email"
                    type="email"
                    autoComplete="email"
                    aria-invalid={!!errors.email}
                    {...register("email")}
                  />
                  {errors.email && (
                    <p className="text-sm text-destructive">{errors.email.message}</p>
                  )}
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="password">Senha</Label>
                    <Link
                      to="/esqueci-senha"
                      className="text-xs font-medium text-primary hover:underline"
                    >
                      Esqueci minha senha
                    </Link>
                  </div>
                  <Input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    aria-invalid={!!errors.password}
                    {...register("password")}
                  />
                  {errors.password && (
                    <p className="text-sm text-destructive">{errors.password.message}</p>
                  )}
                </div>

                {formError && (
                  <p
                    role="alert"
                    className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
                  >
                    {formError}
                  </p>
                )}

                <Button type="submit" className="w-full" disabled={isSubmitting}>
                  {isSubmitting ? "Entrando…" : "Entrar"}
                </Button>
              </form>

              <p className="mt-4 text-center text-sm text-muted-foreground">
                Ainda não tem conta?{" "}
                <Link
                  to={`/register${nextQuery(searchParams.get("next"))}`}
                  className="font-medium text-primary hover:underline"
                >
                  Criar conta
                </Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
