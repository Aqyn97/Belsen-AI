import { LosslessNumber, parse, stringify } from 'lossless-json';
export { LosslessNumber };
export function parseExact(text: string): unknown { return parse(text); }
export function exact(value: string): LosslessNumber { return new LosslessNumber(value); }
export function decimalInput(value: string): LosslessNumber {
  if (!/^(0|[1-9]\d{0,14})(\.\d{1,4})?$/.test(value) || !/[1-9]/.test(value)) throw new Error('Enter a positive quantity with at most 15 integer and four decimal digits.');
  return exact(value);
}
export function version(value: string): LosslessNumber {
  if (!/^(0|[1-9]\d*)$/.test(value) || BigInt(value) > 9223372036854775807n) throw new Error('Invalid version token.');
  return exact(value);
}
function validate(value: unknown): void {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) throw new Error('Use an exact numeric token instead of a floating-point or unsafe number.');
  if (value instanceof LosslessNumber) return;
  if (Array.isArray(value)) value.forEach(validate);
  else if (value && typeof value === 'object') Object.values(value).forEach(validate);
}
export function stringifyExact(value: unknown): string {
  validate(value); const result = stringify(value);
  if (result === undefined) throw new Error('Request body is not JSON.'); return result;
}
