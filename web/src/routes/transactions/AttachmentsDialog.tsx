import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Camera, Download, FileText, Paperclip, Trash2 } from "lucide-react";
import { toast } from "sonner";
import {
  ATTACHMENT_ACCEPT,
  MAX_ATTACHMENTS,
  MAX_ATTACHMENT_BYTES,
  deleteAttachment,
  downloadAttachment,
  listAttachments,
  uploadAttachment,
  type Attachment,
} from "@/api/attachments";
import { saveBlob } from "@/api/client";
import type { Transaction } from "@/api/transactions";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";

function formatSize(bytes: number): string {
  return bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}

/**
 * Comprovantes do lançamento (Fase 20). No celular o botão "Fotografar" abre a câmera direto
 * (capture), que é como comprovante de verdade entra no app. Imagens ganham miniatura gerada a
 * partir do blob (o servidor sempre entrega como download, nunca renderizado na origem do app).
 */
export function AttachmentsDialog({
  transaction,
  open,
  onOpenChange,
}: {
  transaction: Transaction;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const cameraInput = useRef<HTMLInputElement>(null);
  const queryKey = ["attachments", transaction.id];
  const { data: attachments, isPending } = useQuery({
    queryKey,
    queryFn: () => listAttachments(transaction.id),
    enabled: open,
  });
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey });
    void queryClient.invalidateQueries({ queryKey: ["transactions"] });
  };

  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(transaction.id, file),
    onSuccess: () => {
      refresh();
      toast.success("Comprovante anexado.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível anexar.")),
  });
  const remove = useMutation({
    mutationFn: (id: string) => deleteAttachment(transaction.id, id),
    onSuccess: refresh,
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível remover.")),
  });

  const pick = (file: File | undefined) => {
    if (!file) return;
    if (file.size > MAX_ATTACHMENT_BYTES) {
      toast.error("O comprovante pode ter até 5 MB.");
      return;
    }
    upload.mutate(file);
  };

  const full = (attachments?.length ?? 0) >= MAX_ATTACHMENTS;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Comprovantes</DialogTitle>
          <DialogDescription>
            {transaction.description} · PDF ou foto, até 5 MB, no máximo {MAX_ATTACHMENTS}.
          </DialogDescription>
        </DialogHeader>

        {isPending ? (
          <Skeleton className="h-24 rounded-xl" />
        ) : attachments && attachments.length > 0 ? (
          <ul className="divide-y divide-border/60 rounded-xl border border-border/60">
            {attachments.map((a) => (
              <AttachmentRow
                key={a.id}
                transactionId={transaction.id}
                attachment={a}
                onDelete={() => remove.mutate(a.id)}
                deleting={remove.isPending && remove.variables === a.id}
              />
            ))}
          </ul>
        ) : (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-border py-6 text-center">
            <Paperclip className="size-6 text-muted-foreground/60" />
            <p className="text-sm text-muted-foreground">Nenhum comprovante ainda.</p>
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            disabled={full || upload.isPending}
            onClick={() => fileInput.current?.click()}
          >
            <Paperclip className="size-4" />
            {upload.isPending ? "Enviando…" : "Anexar arquivo"}
          </Button>
          <Button
            variant="outline"
            className="sm:hidden"
            disabled={full || upload.isPending}
            onClick={() => cameraInput.current?.click()}
          >
            <Camera className="size-4" /> Fotografar
          </Button>
        </div>
        <input
          ref={fileInput}
          type="file"
          accept={ATTACHMENT_ACCEPT}
          className="hidden"
          onChange={(e) => {
            pick(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
        <input
          ref={cameraInput}
          type="file"
          accept="image/*"
          capture="environment"
          className="hidden"
          onChange={(e) => {
            pick(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
      </DialogContent>
    </Dialog>
  );
}

function AttachmentRow({
  transactionId,
  attachment,
  onDelete,
  deleting,
}: {
  transactionId: string;
  attachment: Attachment;
  onDelete: () => void;
  deleting: boolean;
}) {
  const isImage = attachment.contentType.startsWith("image/");
  const [preview, setPreview] = useState<string | null>(null);

  // Miniatura a partir do blob: o blob: URL é revogado quando a linha sai da tela.
  useEffect(() => {
    if (!isImage) return;
    let url: string | null = null;
    let alive = true;
    downloadAttachment(transactionId, attachment.id)
      .then(({ blob }) => {
        if (!alive) return;
        url = URL.createObjectURL(blob);
        setPreview(url);
      })
      .catch(() => undefined);
    return () => {
      alive = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [isImage, transactionId, attachment.id]);

  const download = async () => {
    try {
      const { blob } = await downloadAttachment(transactionId, attachment.id);
      saveBlob(blob, attachment.fileName);
    } catch (err) {
      toast.error(getErrorMessage(err, "Não foi possível baixar."));
    }
  };

  return (
    <li className="flex items-center gap-3 px-3 py-2">
      <span className="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-muted">
        {preview ? (
          <img src={preview} alt="" className="size-full object-cover" />
        ) : (
          <FileText className="size-5 text-muted-foreground" />
        )}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{attachment.fileName}</p>
        <p className="text-xs text-muted-foreground">
          {formatSize(attachment.sizeBytes)} · {formatDate(attachment.createdAt.slice(0, 10))}
        </p>
      </div>
      <Button
        size="sm"
        variant="ghost"
        onClick={() => void download()}
        aria-label={`Baixar ${attachment.fileName}`}
      >
        <Download className="size-4" />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        className="text-destructive hover:text-destructive"
        disabled={deleting}
        onClick={onDelete}
        aria-label={`Remover ${attachment.fileName}`}
      >
        <Trash2 className="size-4" />
      </Button>
    </li>
  );
}
