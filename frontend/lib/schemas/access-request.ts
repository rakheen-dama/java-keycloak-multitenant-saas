import { z } from "zod";

export const accessRequestSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Invalid email address")
    .max(255),
  fullName: z.string().min(1, "Full name is required").max(255),
  organizationName: z.string().min(1, "Organisation name is required").max(255),
  country: z.string().min(1, "Country is required"),
  industry: z.string().min(1, "Industry is required"),
});

export type AccessRequestFormData = z.infer<typeof accessRequestSchema>;

export const otpVerifySchema = z.object({
  otp: z
    .string()
    .length(6, "Verification code must be 6 digits")
    .regex(/^\d{6}$/, "Verification code must contain only digits"),
});

export type OtpVerifyFormData = z.infer<typeof otpVerifySchema>;
