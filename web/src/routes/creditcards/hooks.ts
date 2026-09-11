import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  createCreditCard,
  deleteCharge,
  deleteCreditCard,
  getInvoice,
  listCreditCards,
  listInvoices,
  payInvoice,
  registerCharge,
  updateCreditCard,
  type CreateChargePayload,
  type CreateCreditCardPayload,
  type PayInvoicePayload,
  type UpdateCreditCardPayload,
} from "@/api/creditCards";
import { accountsQueryKey } from "@/hooks/useAccounts";
import { getErrorMessage } from "@/lib/errors";

const cardsKey = ["credit-cards"] as const;
const invoicesKey = (cardId: string) => ["credit-cards", cardId, "invoices"] as const;
const invoiceKey = (invoiceId: string) => ["credit-card-invoices", invoiceId] as const;

export function useCreditCards() {
  return useQuery({ queryKey: cardsKey, queryFn: listCreditCards });
}

export function useCreateCreditCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateCreditCardPayload) => createCreditCard(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: cardsKey });
      toast.success("Cartão criado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar o cartão.")),
  });
}

export function useUpdateCreditCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateCreditCardPayload }) => updateCreditCard(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: cardsKey });
      toast.success("Cartão atualizado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível atualizar o cartão.")),
  });
}

export function useDeleteCreditCard() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCreditCard(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: cardsKey });
      toast.success("Cartão excluído.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível excluir o cartão.")),
  });
}

export function useInvoices(cardId: string) {
  return useQuery({ queryKey: invoicesKey(cardId), queryFn: () => listInvoices(cardId) });
}

export function useInvoice(invoiceId: string) {
  return useQuery({ queryKey: invoiceKey(invoiceId), queryFn: () => getInvoice(invoiceId) });
}

export function useRegisterCharge(cardId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateChargePayload) => registerCharge(cardId, payload),
    onSuccess: (charges) => {
      void queryClient.invalidateQueries({ queryKey: invoicesKey(cardId) });
      toast.success(charges.length > 1 ? `${charges.length} parcelas lançadas.` : "Lançamento registrado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível lançar a compra.")),
  });
}

export function usePayInvoice(cardId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: PayInvoicePayload }) => payInvoice(id, payload),
    onSuccess: (invoice) => {
      void queryClient.invalidateQueries({ queryKey: invoiceKey(invoice.id) });
      void queryClient.invalidateQueries({ queryKey: invoicesKey(cardId) });
      void queryClient.invalidateQueries({ queryKey: ["transactions"] });
      void queryClient.invalidateQueries({ queryKey: accountsQueryKey });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível registrar o pagamento.")),
  });
}

export function useDeleteCharge(cardId: string, invoiceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (chargeId: string) => deleteCharge(invoiceId, chargeId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: invoiceKey(invoiceId) });
      void queryClient.invalidateQueries({ queryKey: invoicesKey(cardId) });
      toast.success("Cobrança removida.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível remover a cobrança.")),
  });
}
