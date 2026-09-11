import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAuth } from "@/auth/auth-context";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { registerSchema, type RegisterFormValues } from "./register-schema";

/**
 * POST /api/auth/register — o backend ja autentica na mesma chamada (sessao
 * criada junto com o usuario), entao um registro bem-sucedido leva direto pro
 * app, sem passar por /login.
 */
export function RegisterPage() {
  const { register: doRegister } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null);
    try {
      await doRegister(values);
      void navigate("/", { replace: true });
    } catch (err) {
      setFormError(getErrorMessage(err, "Não foi possível criar a conta. Tente novamente."));
    }
  }

  return (
    <div className="grid min-h-dvh place-items-center p-6">
      <div className="w-full max-w-sm rounded-xl border border-border bg-card p-6 text-card-foreground shadow-sm">
        <h1 className="text-lg font-semibold tracking-tight">Criar conta</h1>
        <p className="mt-1 text-sm text-muted-foreground">fin-mec — controle financeiro pessoal.</p>

        <form className="mt-5 space-y-4" onSubmit={(e) => void handleSubmit(onSubmit)(e)} noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="email">E-mail</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              aria-invalid={!!errors.email}
              {...register("email")}
            />
            {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Senha</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              aria-invalid={!!errors.password}
              {...register("password")}
            />
            {errors.password ? (
              <p className="text-sm text-destructive">{errors.password.message}</p>
            ) : (
              <p className="text-sm text-muted-foreground">Mínimo de 10 caracteres.</p>
            )}
          </div>

          {formError && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {formError}
            </p>
          )}

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Criando conta…" : "Criar conta"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Já tem conta?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Entrar
          </Link>
        </p>
      </div>
    </div>
  );
}
