import { z } from "zod";

export const createProjectSchema = z.object({
  title: z
    .string()
    .min(1, "Title is required")
    .max(255, "Title must be 255 characters or fewer"),
  description: z.string().max(2000, "Description must be 2000 characters or fewer").optional(),
  customerId: z.string().min(1, "Customer is required"),
});

export type CreateProjectFormData = z.infer<typeof createProjectSchema>;
