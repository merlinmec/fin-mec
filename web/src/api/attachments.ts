import { api, apiBlob, apiFetch } from "./client";

/** Espelha AttachmentController.AttachmentResponse. */
export interface Attachment {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  createdBy: string | null;
  createdAt: string;
}

export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
export const MAX_ATTACHMENTS = 5;
export const ATTACHMENT_ACCEPT = "application/pdf,image/png,image/jpeg,image/webp";

export function listAttachments(transactionId: string): Promise<Attachment[]> {
  return api.get<Attachment[]>(`/transactions/${transactionId}/attachments`);
}

export function uploadAttachment(transactionId: string, file: File): Promise<Attachment> {
  const form = new FormData();
  form.append("file", file);
  return apiFetch<Attachment>(`/transactions/${transactionId}/attachments`, {
    method: "POST",
    body: form,
  });
}

export function downloadAttachment(transactionId: string, attachmentId: string) {
  return apiBlob(`/transactions/${transactionId}/attachments/${attachmentId}`);
}

export function deleteAttachment(transactionId: string, attachmentId: string): Promise<void> {
  return api.del<void>(`/transactions/${transactionId}/attachments/${attachmentId}`);
}
