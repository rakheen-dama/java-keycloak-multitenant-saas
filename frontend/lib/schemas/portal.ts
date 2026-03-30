import { z } from "zod";

export const requestLinkSchema = z.object({
  email: z.string().email("Valid email is required"),
  orgId: z.string().min(1, "Organization is required"),
});

export type RequestLinkFormData = z.infer<typeof requestLinkSchema>;

export const exchangeTokenSchema = z.object({
  token: z.string().min(1, "Token is required"),
});

export type ExchangeTokenFormData = z.infer<typeof exchangeTokenSchema>;
