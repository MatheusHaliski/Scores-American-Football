"use client";
import { useT } from "@/i18n/provider";
import { Button } from "./Button";
import { Dialog, type ConfirmDialogProps } from "./Dialog";

export function ConfirmDialog({ open, onClose, onConfirm, title, body, confirmLabel, danger, loading }: ConfirmDialogProps) {
  const t = useT("common");
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            {t("actions.cancel")}
          </Button>
          <Button variant={danger ? "danger" : "primary"} onClick={onConfirm} loading={loading} data-autofocus>
            {confirmLabel ?? t("actions.confirm")}
          </Button>
        </>
      }
    >
      <div className="text-text-soft text-[0.95rem] leading-relaxed">{body}</div>
    </Dialog>
  );
}
