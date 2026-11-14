import { expect, it } from 'vitest';
import { withSummaryRequest } from './written-summary.ts';

it('asks for a short factual report in the same turn, preserving the user instruction', () => {
  const sent = withSummaryRequest('Lis notes.md');
  expect(sent).toContain('[Point Régie]');
  expect(sent).toContain('Fait :');
  expect(sent).toContain('Tests :');
  expect(sent).toContain('Suite :');
  expect(sent).toContain('Question :');
  expect(sent).toContain('non communiqués');
  expect(sent.endsWith('\n\nLis notes.md')).toBe(true);
});
