import { reportFixture } from './analyticsFixtures';
export function coachFixture(submissionId = '66666666-6666-4666-8666-666666666666') {
  return { schemaVersion: 1, submissionId, source: 'AI_PRIORITIZED', preparedAt: '2026-11-02T12:00:01Z', expiresAt: '2099-11-03T12:00:00Z',
    evidence: { schemaVersion: 1, period: reportFixture().period,
      facts: [{ id: 'tasks', actual: '1', target: '3', percentage: '33.3', reason: null }, { id: 'habits', actual: '1', target: '2', percentage: '50.0', reason: null }, { id: 'g1', actual: '12.3456', target: '20.0000', percentage: '61.7', reason: null }],
      candidates: [{ id: 'c1', order: 0, action: 'REVIEW_TASKS', destination: 'TASKS', condition: 'INCOMPLETE_TASKS', title: 'Review task commitments', nextAction: 'Review current tasks and choose a feasible commitment.', explanationTemplate: 'Review current commitments; original reporting obligations remain.', evidenceIds: ['tasks'] }],
      coverage: { includedGoals: 1, totalGoals: 1, truncated: false } }, selectedIds: ['c1'] };
}
