import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * <select> nativo estilizado, nao o Select do shadcn (que e Radix por baixo).
 * Pros combos pequenos e fixos do fin-mec (tipo de conta, categoria, ...) o
 * nativo ja da teclado/acessibilidade de graca; reavaliar pra Radix se uma
 * fase futura precisar de busca ou opcoes ricas (icone + cor, por exemplo).
 */
export type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement>;

const Select = React.forwardRef<HTMLSelectElement, SelectProps>(({ className, children, ...props }, ref) => (
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
));
Select.displayName = "Select";

export { Select };
