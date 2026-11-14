import { z } from 'zod';
import { AGENT_KINDS, APPROVAL_MODES } from '../../domain/model.ts';

/**
 * The messages a client sends (docs/protocole-pont.md). Every request has an `id`, echoed in
 * its response.
 */
export const requestSchema = z.object({
  id: z.union([z.string().max(100), z.number().int()]),
  method: z.string().max(100),
  params: z.record(z.string(), z.unknown()).optional(),
});

const sessionId = z.string().min(1).max(200);
const configValues = z.record(z.string().min(1).max(200), z.string().min(1).max(500));
const seq = z.number().int().min(0);

export const paramsSchemas = {
  hello: z.object({
    since: seq.nullish(),
    journal: z.string().max(100).nullish(),
    client: z.string().max(100).optional(),
  }),
  'agent.options': z.object({ agent: z.enum(AGENT_KINDS), configValues: configValues.optional() }),
  'session.configure': z
    .object({
      sessionId,
      approvalMode: z.enum(APPROVAL_MODES).optional(),
      configId: z.string().min(1).max(200).optional(),
      value: z.string().min(1).max(500).optional(),
      configValues: configValues.optional(),
    })
    .refine((p) =>
      p.approvalMode !== undefined
        ? p.configId === undefined && p.value === undefined && p.configValues === undefined
        : p.configValues !== undefined
          ? Object.keys(p.configValues).length > 0 &&
            p.configId === undefined &&
            p.value === undefined
          : p.configId !== undefined && p.value !== undefined,
    ),
  'sessions.list': z.object({}),
  'session.create': z.object({
    agent: z.enum(AGENT_KINDS),
    project: z.string().max(200),
    newProject: z.boolean().optional(),
    configValues: configValues.optional(),
    branch: z.string().min(1).max(200).nullish(),
    approvalMode: z.enum(APPROVAL_MODES),
    prompt: z.string().max(100_000).nullish(),
  }),
  'session.prompt': z.object({ sessionId, text: z.string().min(1).max(100_000) }),
  'session.cancel': z.object({ sessionId }),
  'session.resume': z.object({ sessionId }),
  'session.vocabulary': z.object({ sessionId }),
  'session.events': z.object({ sessionId, since: seq.optional() }),
  'approval.answer': z.object({
    approvalId: z.string().min(1).max(200),
    optionId: z.string().min(1).max(200),
  }),
  'approvals.list': z.object({}),
  'agents.versions': z.object({}),
  'projects.list': z.object({}),
  'bridge.shutdown': z.object({}),
} as const;

export type Method = keyof typeof paramsSchemas;
export type Params<M extends Method> = z.infer<(typeof paramsSchemas)[M]>;

export function isMethod(method: string): method is Method {
  return Object.hasOwn(paramsSchemas, method);
}
