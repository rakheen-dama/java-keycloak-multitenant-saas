export function formatDate(date: string | Date): string {
  return new Date(date).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const k = 1024;
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const value = bytes / Math.pow(k, i);
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`;
}

/**
 * Maps currency codes to their natural locale so Intl.NumberFormat
 * renders the correct symbol (e.g. ZAR -> "R", GBP -> "£").
 */
const currencyLocaleMap: Record<string, string> = {
  ZAR: "en-ZA",
  USD: "en-US",
  GBP: "en-GB",
  EUR: "de-DE",
};

/**
 * Formats a number as currency (e.g. "$125.00" or "R125.00").
 */
export function formatCurrency(amount: number, currency: string): string {
  const code = currency || "USD";
  const locale = currencyLocaleMap[code] ?? "en-US";
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: code,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount ?? 0);
}

/**
 * Null-safe currency formatter. Returns "N/A" if amount or currency is missing.
 */
export function formatCurrencySafe(
  amount: number | null | undefined,
  currency: string | null | undefined,
): string {
  if (amount == null || !currency) return "N/A";
  return formatCurrency(amount, currency);
}
