import { describe, expect, it } from 'vitest';
import { decimalInput, parseExact, stringifyExact, version, LosslessNumber } from './numbers';
describe('exact wire values', () => {
  it.each(['9007199254740993','9223372036854775807'])('round trips integer %s', value => { const data = parseExact(`{"version":${value}}`) as { version: LosslessNumber }; expect(data.version.toString()).toBe(value); expect(stringifyExact(data)).toBe(`{"version":${value}}`); expect(version(value).toString()).toBe(value); });
  it.each(['0.0001','123.4567','999999999999999.9999'])('preserves decimal %s', value => { expect(stringifyExact({ quantity: decimalInput(value) })).toBe(`{"quantity":${value}}`); });
  it('preserves large aggregate and null percentages', () => { expect(stringifyExact(parseExact('{"units":1999999999999999.9998,"percentage":null}'))).toBe('{"units":1999999999999999.9998,"percentage":null}'); });
  it.each(['0','-1','1.00001','1000000000000000','1e2','1,2','01.2',''])('rejects invalid precision %s', value => expect(() => decimalInput(value)).toThrow());
  it('rejects unsafe native numbers and invalid versions', () => { expect(() => stringifyExact({ version: 9007199254740992 })).toThrow(); expect(() => stringifyExact({ quantity: .1 })).toThrow(); expect(() => version('9223372036854775808')).toThrow(); });
});
