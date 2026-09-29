import { api } from "./client";

/** Espelha com.mecfin.tag.api.TagResponse. */
export interface Tag {
  id: string;
  name: string;
  color: string | null;
  usageCount: number;
}

export interface TagPayload {
  name: string;
  color?: string | null;
}

export function listTags(): Promise<Tag[]> {
  return api.get<Tag[]>("/tags");
}

export function createTag(payload: TagPayload): Promise<Tag> {
  return api.post<Tag>("/tags", payload);
}

export function updateTag(id: string, payload: TagPayload): Promise<Tag> {
  return api.put<Tag>(`/tags/${id}`, payload);
}

export function deleteTag(id: string): Promise<void> {
  return api.del<void>(`/tags/${id}`);
}
