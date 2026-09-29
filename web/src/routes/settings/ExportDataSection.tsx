import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { FileDown } from "lucide-react";
import { toast } from "sonner";
import { exportMyData } from "@/api/account-security";
import { saveBlob } from "@/api/client";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Panel } from "@/components/ui/panel";
import { getErrorMessage } from "@/lib/errors";

/**
 * Portabilidade (LGPD art. 18, V — Fase 20). Pede a senha de novo: o arquivo tem todo o histórico
 * financeiro, e sessão aberta num computador destrancado não pode bastar para levá-lo.
 */
export function ExportDataSection() {
  const { user } = useAuth();
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () => exportMyData(password, code),
    onSuccess: ({ blob, fileName }) => {
      saveBlob(blob, fileName ?? "fin-mec-meus-dados.json");
      setPassword("");
      setCode("");
      toast.success("Arquivo gerado. Guarde-o em lugar seguro: ele tem todo o seu histórico.");
    },
    onError: (e) => setError(getErrorMessage(e, "Não foi possível exportar.")),
  });

  return (
    <Panel title="Baixar meus dados">
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Um arquivo JSON com tudo o que o fin-mec guarda sobre você e o seu espaço: contas,
          lançamentos, cartões, orçamentos, metas, regras, conexões bancárias e o histórico de
          segurança. Senhas, segredos do 2FA e tokens nunca saem. Os comprovantes vêm listados; os
          arquivos em si você baixa em cada lançamento. É o seu direito de portabilidade (LGPD, art.
          18).
        </p>
        <form
          className="grid gap-3 sm:max-w-md"
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            mutation.mutate();
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="export-password">Senha</Label>
            <Input
              id="export-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {user?.mfaEnabled && (
            <div className="space-y-1.5">
              <Label htmlFor="export-code">Código do app ou de recuperação</Label>
              <Input
                id="export-code"
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
            </div>
          )}
          {error && (
            <p
              role="alert"
              className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {error}
            </p>
          )}
          <Button type="submit" className="w-fit" disabled={mutation.isPending || !password}>
            <FileDown className="size-4" />
            {mutation.isPending ? "Gerando…" : "Baixar meus dados"}
          </Button>
        </form>
      </div>
    </Panel>
  );
}
