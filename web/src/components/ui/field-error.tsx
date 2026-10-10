import { cn } from "@/lib/utils";

interface FieldErrorProps {
  /** Convenção: `${idDoCampo}-error`, referenciado pelo aria-describedby do campo (errorDescribedBy). */
  id: string;
  error?: { message?: string };
  className?: string;
}

/**
 * Mensagem de validação de um campo. O id existe para o campo apontar para ela via
 * aria-describedby: sem isso o leitor de tela anuncia só "inválido", sem dizer o porquê.
 */
export function FieldError({ id, error, className }: FieldErrorProps) {
  if (!error?.message) return null;
  return (
    <p id={id} className={cn("text-sm text-destructive", className)}>
      {error.message}
    </p>
  );
}
