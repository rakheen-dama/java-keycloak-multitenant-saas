import { z } from "zod";

export const createCustomerSchema = z.object({
  name: z
    .string()
    .min(1, "Name is required")
    .max(255, "Name must be 255 characters or fewer"),
  email: z
    .string()
    .min(1, "Email is required")
    .email("Please enter a valid email address")
    .max(255, "Email must be 255 characters or fewer"),
  company: z.string().max(255, "Company must be 255 characters or fewer").optional(),
});

export type CreateCustomerFormData = z.infer<typeof createCustomerSchema>;

export const editCustomerSchema = z.object({
  name: z
    .string()
    .min(1, "Name is required")
    .max(255, "Name must be 255 characters or fewer"),
  email: z
    .string()
    .min(1, "Email is required")
    .email("Please enter a valid email address")
    .max(255, "Email must be 255 characters or fewer"),
  company: z.string().max(255, "Company must be 255 characters or fewer").optional(),
});

export type EditCustomerFormData = z.infer<typeof editCustomerSchema>;
