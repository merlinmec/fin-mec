/** aria-describedby do campo (par do FieldError): só aponta para a mensagem enquanto ela existe. */
export function errorDescribedBy(fieldId: string, error: unknown): string | undefined {
  return error ? `${fieldId}-error` : undefined;
}
