import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Versao sem Radix: um <label> comum nao precisa de asChild/composicao, entao
 * pular @radix-ui/react-label evita uma dependencia so por isso. Reavaliar se
 * uma fase futura precisar de comportamento que so o Radix resolve.
 */
export type LabelProps = React.LabelHTMLAttributes<HTMLLabelElement>;

const Label = React.forwardRef<HTMLLabelElement, LabelProps>(({ className, ...props }, ref) => (
  <label
    ref={ref}
    className={cn(
      "text-sm leading-none font-medium peer-disabled:cursor-not-allowed peer-disabled:opacity-70",
      className,
    )}
    {...props}
  />
));
Label.displayName = "Label";

export { Label };
