import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * <select> nativo estilizado — alternativa mais leve ao Select do shadcn
 * (components/ui/select.tsx, Radix por baixo). Pros combos pequenos, fixos e
 * so-texto do fin-mec (tipo de conta, arquivada/nao...) o nativo ja da
 * teclado/acessibilidade de graca sem trazer Radix junto. Usar o Select rico
 * quando a opcao precisar de icone/cor/swatch (ver CategorySelect).
 */
export type NativeSelectProps = React.SelectHTMLAttributes<HTMLSelectElement>;

const NativeSelect = React.forwardRef<HTMLSelectElement, NativeSelectProps>(
  ({ className, children, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        "flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...props}
    >
      {children}
    </select>
  ),
);
NativeSelect.displayName = "NativeSelect";

export { NativeSelect };
