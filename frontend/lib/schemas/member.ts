import { z } from "zod";

export const inviteMemberSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Invalid email address")
    .max(255),
});

export type InviteMemberFormData = z.infer<typeof inviteMemberSchema>;

export const changeRoleSchema = z.object({
  role: z.enum(["owner", "member"], {
    message: "Role must be owner or member",
  }),
});

export type ChangeRoleFormData = z.infer<typeof changeRoleSchema>;
