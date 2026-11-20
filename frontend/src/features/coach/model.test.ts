import { expect, it } from 'vitest';
import { analysis, status } from './model';
import { coachFixture } from '../../test/coachFixtures';
import { parseExact, stringifyExact } from '../../shared/lib/numbers';
const decode = (v: unknown) => analysis(parseExact(stringifyExact(v)));
it('decodes exact metrics without arithmetic or source text', () => { const f = coachFixture(); f.evidence.facts[2]!.actual = '999999999999999999999.1234'; expect(decode(f).evidence.facts[2]!.actual).toBe('999999999999999999999.1234'); });
it.each(['unknown', 'duplicate', 'too many', 'destination', 'evidence', 'source'])('rejects invalid %s responses', mode => {
  const f = coachFixture(); if (mode === 'unknown') f.selectedIds = ['other']; if (mode === 'duplicate') f.selectedIds = ['c1', 'c1']; if (mode === 'too many') f.selectedIds = ['c1', 'c1', 'c1', 'c1']; if (mode === 'destination') f.evidence.candidates[0]!.destination = 'https://evil.test'; if (mode === 'evidence') f.evidence.candidates[0]!.evidenceIds = ['other']; if (mode === 'source') f.source = 'invented'; expect(() => decode(f)).toThrow();
});
it('requires supported status and notice versions', () => { expect(status(parseExact('{"schemaVersion":1,"noticeVersion":1,"goalLimit":20,"status":"AVAILABLE"}'))).toBe('AVAILABLE'); expect(() => status(parseExact('{"schemaVersion":2,"noticeVersion":1,"goalLimit":20,"status":"AVAILABLE"}'))).toThrow(); });
it('keeps null percentage distinct from zero', () => { const f = coachFixture(); const parsed = decode(f); expect(parsed.evidence.facts[0]!.percentage).toBe('33.3'); });
