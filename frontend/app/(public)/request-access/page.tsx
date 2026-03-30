"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { CheckCircle2, Loader2 } from "lucide-react";
import { submitAccessRequest, verifyAccessRequestOtp } from "./actions";
import { COUNTRIES, INDUSTRIES } from "@/lib/access-request-data";
import {
  accessRequestSchema,
  type AccessRequestFormData,
} from "@/lib/schemas/access-request";

function formatTime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export default function RequestAccessPage() {
  const [step, setStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [otpError, setOtpError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [otp, setOtp] = useState("");
  const [secondsLeft, setSecondsLeft] = useState(0);

  const {
    register,
    handleSubmit: rhfHandleSubmit,
    setValue,
    getValues,
    watch,
    formState: { errors, isValid },
  } = useForm<AccessRequestFormData>({
    resolver: zodResolver(accessRequestSchema),
    mode: "onChange",
    defaultValues: {
      email: "",
      fullName: "",
      organizationName: "",
      country: "",
      industry: "",
    },
  });

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const interval = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) { clearInterval(interval); return 0; }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [secondsLeft]);

  const handleResend = useCallback(async () => {
    setOtpError(null);
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await submitAccessRequest(getValues());
      if (result.success) {
        setSuccessMessage(result.message ?? "Verification code sent.");
        setSecondsLeft((result.expiresInMinutes ?? 10) * 60);
        setOtp("");
      } else {
        setError(result.error ?? "Failed to resend code.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }, [getValues]);

  async function onSubmit(data: AccessRequestFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await submitAccessRequest(data);
      if (result.success) {
        setSuccessMessage(result.message ?? "Verification code sent.");
        setSecondsLeft((result.expiresInMinutes ?? 10) * 60);
        setStep(2);
      } else {
        setError(result.error ?? "Failed to submit request.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    setOtpError(null);
    setIsVerifying(true);
    try {
      const result = await verifyAccessRequestOtp(getValues("email").trim(), otp);
      if (result.success) {
        setStep(3);
      } else {
        setOtpError(result.error ?? "Verification failed.");
      }
    } catch {
      setOtpError("An unexpected error occurred.");
    } finally {
      setIsVerifying(false);
    }
  }

  // Step 3: Confirmation
  if (step === 3) {
    return (
      <Card data-testid="success-message">
        <CardHeader>
          <CardTitle className="font-display text-lg">
            <span className="flex items-center gap-2">
              <CheckCircle2 className="size-5 text-green-600" />
              Request Submitted
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Your access request has been submitted for review. We&apos;ll notify
            you by email once it&apos;s been reviewed.
          </p>
          <Link
            href="/"
            className="inline-block text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            Back to home
          </Link>
        </CardContent>
      </Card>
    );
  }

  // Step 2: OTP
  if (step === 2) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="font-display text-lg">Check Your Email</CardTitle>
          <CardDescription>{successMessage}</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Enter the verification code sent to{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">
              {getValues("email")}
            </span>{" "}
            to continue.
          </p>
          <form onSubmit={handleVerify} className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="otp">Verification Code</Label>
              <Input
                id="otp"
                data-testid="otp-input"
                type="text"
                inputMode="numeric"
                maxLength={6}
                pattern="[0-9]*"
                placeholder="000000"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                className="text-center text-2xl tracking-[0.5em] font-mono"
                aria-invalid={otpError ? true : undefined}
                autoFocus
              />
              {otpError && (
                <p className="text-sm text-red-600" role="alert">{otpError}</p>
              )}
            </div>

            {secondsLeft > 0 ? (
              <p className="text-center text-xs text-slate-500">
                Code expires in {formatTime(secondsLeft)}
              </p>
            ) : (
              <p className="text-center text-xs text-red-600">
                Code expired.{" "}
                <button
                  type="button"
                  onClick={handleResend}
                  disabled={isSubmitting}
                  className="font-medium text-teal-600 hover:text-teal-700 underline disabled:opacity-50"
                >
                  Resend code
                </button>
              </p>
            )}

            {secondsLeft > 0 && (
              <p className="text-center text-xs text-slate-500">
                Didn&apos;t receive it?{" "}
                <button
                  type="button"
                  onClick={handleResend}
                  disabled={isSubmitting}
                  className="font-medium text-teal-600 hover:text-teal-700 underline disabled:opacity-50"
                >
                  Resend code
                </button>
              </p>
            )}

            {error && (
              <div
                role="alert"
                className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
              >
                {error}
              </div>
            )}

            <Button
              type="submit"
              data-testid="verify-otp-btn"
              variant="accent"
              size="lg"
              className="w-full"
              disabled={otp.length !== 6 || isVerifying || secondsLeft <= 0}
            >
              {isVerifying ? (
                <><Loader2 className="animate-spin" />Verifying...</>
              ) : (
                "Verify"
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    );
  }

  // Step 1: Form
  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-display text-lg">Request Access</CardTitle>
        <CardDescription>
          Fill in your details to request access to the platform.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={rhfHandleSubmit(onSubmit)}
          data-testid="request-access-form"
          className="space-y-4"
        >
          <div className="space-y-2">
            <Label htmlFor="email">Work Email</Label>
            <Input
              id="email"
              data-testid="email-input"
              type="email"
              placeholder="you@company.com"
              {...register("email")}
              aria-invalid={errors.email ? true : undefined}
            />
            {errors.email && (
              <p className="text-sm text-red-600" role="alert">{errors.email.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="fullName">Full Name</Label>
            <Input
              id="fullName"
              data-testid="full-name-input"
              type="text"
              placeholder="Jane Smith"
              {...register("fullName")}
              aria-invalid={errors.fullName ? true : undefined}
            />
            {errors.fullName && (
              <p className="text-sm text-red-600" role="alert">{errors.fullName.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="organizationName">Organisation Name</Label>
            <Input
              id="organizationName"
              data-testid="org-name-input"
              type="text"
              placeholder="Acme Corp"
              {...register("organizationName")}
              aria-invalid={errors.organizationName ? true : undefined}
            />
            {errors.organizationName && (
              <p className="text-sm text-red-600" role="alert">{errors.organizationName.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="country">Country</Label>
            <Select
              value={watch("country")}
              onValueChange={(value) => setValue("country", value, { shouldValidate: true })}
            >
              <SelectTrigger id="country" data-testid="country-select" className="w-full">
                <SelectValue placeholder="Select a country" />
              </SelectTrigger>
              <SelectContent>
                {COUNTRIES.map((country) => (
                  <SelectItem key={country} value={country}>
                    {country}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.country && (
              <p className="text-sm text-red-600" role="alert">{errors.country.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="industry">Industry</Label>
            <Select
              value={watch("industry")}
              onValueChange={(value) => setValue("industry", value, { shouldValidate: true })}
            >
              <SelectTrigger id="industry" data-testid="industry-select" className="w-full">
                <SelectValue placeholder="Select an industry" />
              </SelectTrigger>
              <SelectContent>
                {INDUSTRIES.map((industry) => (
                  <SelectItem key={industry} value={industry}>
                    {industry}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.industry && (
              <p className="text-sm text-red-600" role="alert">{errors.industry.message}</p>
            )}
          </div>

          {error && (
            <div
              role="alert"
              className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
            >
              {error}
            </div>
          )}

          <Button
            type="submit"
            data-testid="submit-request-btn"
            variant="accent"
            size="lg"
            className="w-full"
            disabled={!isValid || isSubmitting}
          >
            {isSubmitting ? (
              <><Loader2 className="animate-spin" />Submitting...</>
            ) : (
              "Request Access"
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
