import { apiFetch, api } from "./client";
import type { EntryType } from "./transactions";

export type ImportFormat = "OFX" | "CSV";

/** Espelha com.mecfin.importing.application.PreviewStatus. */
export type PreviewStatus = "NEW" | "ALREADY_IMPORTED" | "POSSIBLE_DUPLICATE" | "MATCHES_PENDING";

export interface CsvMapping {
  dateColumn: number;
  descriptionColumn: number;
  amountColumn: number;
  invertSign: boolean;
}

/** Espelha com.mecfin.importing.application.PreviewRow — amount sem sinal + type. */
export interface PreviewRow {
  externalId: string;
  date: string;
  description: string;
  amount: number;
  type: EntryType;
  status: PreviewStatus;
  suggestedCategoryId: string | null;
  suggestedTagId: string | null;
  suggestionSource: "RULE" | "HISTORY" | null;
  matchTransactionId: string | null;
  matchDescription: string | null;
  matchDate: string | null;
  matchAmount: number | null;
}

export interface ImportPreview {
  format: ImportFormat;
  fileName: string;
  columns: string[] | null;
  mapping: CsvMapping | null;
  rows: PreviewRow[];
  newCount: number;
  alreadyImportedCount: number;
  possibleDuplicateCount: number;
  matchCount: number;
}

export type CommitAction = "CREATE" | "MATCH" | "SKIP";

/** amount COM sinal (negativo = saída), como no extrato. */
export interface CommitRow {
  externalId: string;
  date: string;
  description: string;
  amount: number;
  action: CommitAction;
  categoryId?: string | null;
  tagIds?: string[];
  matchTransactionId?: string | null;
}

export interface ImportResult {
  batchId: string;
  created: number;
  matched: number;
  skipped: number;
}

export interface ImportBatch {
  id: string;
  accountId: string;
  fileName: string;
  format: ImportFormat;
  createdCount: number;
  matchedCount: number;
  skippedCount: number;
  undoneAt: string | null;
  createdAt: string;
}

export interface CategorizationRule {
  id: string;
  pattern: string;
  categoryId: string;
  tagId: string | null;
  createdAt: string;
}

export function previewImport(
  file: File,
  accountId: string,
  mapping?: CsvMapping,
): Promise<ImportPreview> {
  const form = new FormData();
  form.append("file", file);
  const params = new URLSearchParams({ accountId });
  if (mapping) {
    params.set("dateColumn", String(mapping.dateColumn));
    params.set("descriptionColumn", String(mapping.descriptionColumn));
    params.set("amountColumn", String(mapping.amountColumn));
    params.set("invertSign", String(mapping.invertSign));
  }
  return apiFetch<ImportPreview>(`/imports/preview?${params.toString()}`, {
    method: "POST",
    body: form,
  });
}

export function commitImport(payload: {
  accountId: string;
  fileName: string;
  format: ImportFormat;
  rows: CommitRow[];
}): Promise<ImportResult> {
  return api.post<ImportResult>("/imports", payload);
}

export function listImports(): Promise<ImportBatch[]> {
  return api.get<ImportBatch[]>("/imports");
}

export function undoImport(id: string): Promise<{ canceled: number }> {
  return api.post<{ canceled: number }>(`/imports/${id}/undo`);
}

export function listRules(): Promise<CategorizationRule[]> {
  return api.get<CategorizationRule[]>("/categorization-rules");
}

export function saveRule(
  payload: { pattern: string; categoryId: string; tagId?: string | null },
  id?: string,
): Promise<CategorizationRule> {
  return id
    ? api.put<CategorizationRule>(`/categorization-rules/${id}`, payload)
    : api.post<CategorizationRule>("/categorization-rules", payload);
}

export function deleteRule(id: string): Promise<void> {
  return api.del<void>(`/categorization-rules/${id}`);
}
