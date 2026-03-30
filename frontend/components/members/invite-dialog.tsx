"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { useRouter } from "next/navigation";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  inviteMemberSchema,
  type InviteMemberFormData,
} from "@/lib/schemas/member";
import { inviteMember } from "@/app/(app)/members/actions";

interface InviteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function InviteDialog({
  open,
  onOpenChange,
  onSuccess,
}: InviteDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  const form = useForm<InviteMemberFormData>({
    resolver: zodResolver(inviteMemberSchema),
    defaultValues: { email: "" },
  });

  async function handleSubmit(data: InviteMemberFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await inviteMember(data.email);
      if (result.success) {
        toast.success(`Invitation sent to ${data.email}`);
        form.reset();
        onOpenChange(false);
        onSuccess();
        router.refresh();
      } else {
        setError(result.error ?? "Failed to send invitation.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Invite Member</DialogTitle>
          <DialogDescription>
            Send a Keycloak invitation email. The invited user will be added as a
            member on their first login.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="invite-email">Email address</Label>
            <Input
              id="invite-email"
              type="email"
              placeholder="colleague@company.com"
              data-testid="invite-email-input"
              {...form.register("email")}
            />
            {form.formState.errors.email && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>
          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="accent"
              disabled={isSubmitting}
              data-testid="send-invite-btn"
            >
              {isSubmitting ? "Sending..." : "Send Invite"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
