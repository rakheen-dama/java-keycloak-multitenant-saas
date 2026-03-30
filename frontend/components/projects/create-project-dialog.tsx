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
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  createProjectSchema,
  type CreateProjectFormData,
} from "@/lib/schemas/project";
import {
  createProject,
  type CustomerResponse,
} from "@/app/(app)/projects/actions";

interface CreateProjectDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  customers: CustomerResponse[];
}

export function CreateProjectDialog({
  open,
  onOpenChange,
  customers,
}: CreateProjectDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  const form = useForm<CreateProjectFormData>({
    resolver: zodResolver(createProjectSchema),
    defaultValues: { title: "", description: "", customerId: "" },
  });

  async function handleSubmit(data: CreateProjectFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createProject(
        data.title,
        data.customerId,
        data.description,
      );
      if (result.success) {
        toast.success(`Project "${data.title}" created.`);
        form.reset();
        onOpenChange(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to create project.");
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
          <DialogTitle>New Project</DialogTitle>
          <DialogDescription>
            Create a new project linked to a customer.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="project-title">Title</Label>
            <Input
              id="project-title"
              placeholder="e.g. Website Redesign"
              data-testid="project-title-input"
              {...form.register("title")}
            />
            {form.formState.errors.title && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.title.message}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="project-description">Description</Label>
            <Textarea
              id="project-description"
              placeholder="Optional description..."
              data-testid="project-description-input"
              {...form.register("description")}
            />
            {form.formState.errors.description && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.description.message}
              </p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="project-customer">Customer</Label>
            <Select
              value={form.watch("customerId")}
              onValueChange={(value) => form.setValue("customerId", value, { shouldValidate: true })}
            >
              <SelectTrigger
                id="project-customer"
                className="w-full"
                data-testid="project-customer-select"
              >
                <SelectValue placeholder="Select a customer" />
              </SelectTrigger>
              <SelectContent>
                {customers.map((customer) => (
                  <SelectItem key={customer.id} value={customer.id}>
                    {customer.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {form.formState.errors.customerId && (
              <p className="text-sm text-destructive" role="alert">
                {form.formState.errors.customerId.message}
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
              data-testid="create-project-btn"
            >
              {isSubmitting ? "Creating..." : "Create Project"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
