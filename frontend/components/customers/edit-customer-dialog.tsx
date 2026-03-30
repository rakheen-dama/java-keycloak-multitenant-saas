"use client";

import { useState, useEffect } from "react";
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
  editCustomerSchema,
  type EditCustomerFormData,
} from "@/lib/schemas/customer";
import {
  updateCustomer,
  type CustomerResponse,
} from "@/app/(app)/customers/actions";

interface EditCustomerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  customer: CustomerResponse;
}

export function EditCustomerDialog({
  open,
  onOpenChange,
  customer,
}: EditCustomerDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  const form = useForm<EditCustomerFormData>({
    resolver: zodResolver(editCustomerSchema),
    defaultValues: {
      name: customer.name,
      email: customer.email,
      company: customer.company ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({
        name: customer.name,
        email: customer.email,
        company: customer.company ?? "",
      });
      setError(null);
    }
  }, [open, customer, form]);

  async function handleSubmit(data: EditCustomerFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await updateCustomer(
        customer.id,
        data.name,
        data.email,
        data.company,
      );
      if (result.success) {
        toast.success(`Customer "${data.name}" updated.`);
        onOpenChange(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to update customer.");
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
          <DialogTitle>Edit Customer</DialogTitle>
          <DialogDescription>
            Update customer information.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-customer-name">Name</Label>
            <Input
              id="edit-customer-name"
              placeholder="e.g. Jane Smith"
              data-testid="edit-customer-name-input"
              {...form.register("name")}
            />
            {form.formState.errors.name && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.name.message}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-customer-email">Email</Label>
            <Input
              id="edit-customer-email"
              type="email"
              placeholder="e.g. jane@example.com"
              data-testid="edit-customer-email-input"
              {...form.register("email")}
            />
            {form.formState.errors.email && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-customer-company">Company (optional)</Label>
            <Input
              id="edit-customer-company"
              placeholder="e.g. Acme Corp"
              data-testid="edit-customer-company-input"
              {...form.register("company")}
            />
            {form.formState.errors.company && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.company.message}
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
              data-testid="update-customer-btn"
            >
              {isSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
