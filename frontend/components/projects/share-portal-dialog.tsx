"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Mail, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { apiPost, ApiError } from "@/lib/api";

interface SharePortalDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  customerEmail: string;
  orgId: string;
}

export function SharePortalDialog({
  open,
  onOpenChange,
  customerEmail,
  orgId,
}: SharePortalDialogProps) {
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSend() {
    setError(null);
    setIsSending(true);
    try {
      await apiPost("/api/portal/auth/request-link", {
        email: customerEmail,
        orgId,
      });
      toast.success(`Magic link sent to ${customerEmail}`);
      onOpenChange(false);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("An unexpected error occurred. Please try again.");
      }
    } finally {
      setIsSending(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) setError(null);
    onOpenChange(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Share Portal Link</DialogTitle>
          <DialogDescription>
            Send a magic link to the customer so they can access the portal.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
              Sending to
            </p>
            <p className="mt-0.5 text-sm font-medium text-slate-900 dark:text-slate-100">
              {customerEmail}
            </p>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            The customer will receive an email with a link valid for 15 minutes.
            They can use it to view their projects and add comments.
          </p>
          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isSending}
          >
            Cancel
          </Button>
          <Button
            variant="accent"
            onClick={handleSend}
            disabled={isSending}
            data-testid="send-portal-link-btn"
          >
            {isSending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Mail className="size-4" />
            )}
            Send Link
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
