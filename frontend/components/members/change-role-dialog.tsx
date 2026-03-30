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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { changeMemberRole } from "@/app/(app)/members/actions";
import type { MemberResponse } from "@/app/(app)/members/actions";

interface ChangeRoleDialogProps {
  member: MemberResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function ChangeRoleDialog({
  member,
  open,
  onOpenChange,
  onSuccess,
}: ChangeRoleDialogProps) {
  const [selectedRole, setSelectedRole] = useState<"owner" | "member">(
    member.role as "owner" | "member",
  );
  const [isChanging, setIsChanging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  async function handleConfirm() {
    setError(null);
    setIsChanging(true);
    try {
      const result = await changeMemberRole(member.id, selectedRole);
      if (result.success) {
        toast.success("Role updated");
        onOpenChange(false);
        onSuccess();
        router.refresh();
      } else {
        setError(result.error ?? "Failed to update role.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsChanging(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Change Role</AlertDialogTitle>
          <AlertDialogDescription>
            Change the role for{" "}
            <span className="font-semibold text-foreground">
              {member.displayName ?? member.email}
            </span>
            .
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="py-2">
          <Select
            value={selectedRole}
            onValueChange={(v) => setSelectedRole(v as "owner" | "member")}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="owner">Owner</SelectItem>
              <SelectItem value="member">Member</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isChanging}>
            Cancel
          </AlertDialogCancel>
          <Button
            variant="accent"
            onClick={handleConfirm}
            disabled={isChanging}
            data-testid="confirm-change-role-btn"
          >
            {isChanging ? "Updating..." : "Update Role"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
