/** Parse one RFC 9110 byte range. Multiple ranges are intentionally unsupported. */
export function parseDownloadRange(
  header: string | undefined,
  size: number,
): { start: number; end: number } | "invalid" | undefined {
  if (!header) return undefined;
  const match = /^bytes=(\d*)-(\d*)$/.exec(header);
  if (!match || (!match[1] && !match[2])) return "invalid";
  if (!match[1]) {
    const suffix = Number(match[2]);
    if (!Number.isSafeInteger(suffix) || suffix <= 0) return "invalid";
    return { start: Math.max(0, size - suffix), end: size - 1 };
  }
  const start = Number(match[1]);
  const end = match[2] ? Number(match[2]) : size - 1;
  if (
    !Number.isSafeInteger(start) || !Number.isSafeInteger(end) ||
    start >= size || end < start
  ) return "invalid";
  return { start, end: Math.min(end, size - 1) };
}
