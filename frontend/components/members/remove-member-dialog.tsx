"use client";

import { useState } from "react";
import { toast } from "sonner";
import { useRouter } from "next/navigation";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { removeMember } from "@/app/(app)/members/actions";
import type { MemberResponse } from "@/app/(app)/members/actions";

interface RemoveMemberDialogProps {
  member: MemberResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function RemoveMemberDialog({
  member,
  open,
  onOpenChange,
  onSuccess,
}: RemoveMemberDialogProps) {
  const [isRemoving, setIsRemoving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  async function handleRemove() {
    setError(null);
    setIsRemoving(true);
    try {
      const result = await removeMember(member.id);
      if (result.success) {
        toast.success("Member removed");
        onOpenChange(false);
        onSuccess();
        router.refresh();
      } else {
        setError(result.error ?? "Failed to remove member.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsRemoving(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <AlertDialogTitle>Remove Member</AlertDialogTitle>
          <AlertDialogDescription>
            Remove{" "}
            <span className="font-semibold text-foreground">
              {member.displayName ?? member.email}
            </span>{" "}
            ({member.email}) from the organisation? This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isRemoving}>
            Cancel
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleRemove}
            disabled={isRemoving}
            data-testid="confirm-remove-btn"
          >
            {isRemoving ? "Removing..." : "Remove"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
