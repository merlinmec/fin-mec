import { FlaskConical } from "lucide-react";
import { isVisitor } from "@/api/demo";
import { useAuth } from "@/auth/auth-context";

/** Faixa para quem está numa conta de visitante da demonstração pública. */
export function DemoBanner() {
  const { user } = useAuth();
  if (!isVisitor(user)) return null;
  return (
    <div
      role="status"
      className="flex items-center justify-center gap-2 bg-primary/10 px-4 py-1.5 text-center text-xs font-medium text-primary"
    >
      <FlaskConical className="size-3.5 shrink-0" aria-hidden />
      Demonstração: os dados são fictícios e esta conta é apagada em 24 horas. Senha, 2FA e convites
      ficam desligados.
    </div>
  );
}
