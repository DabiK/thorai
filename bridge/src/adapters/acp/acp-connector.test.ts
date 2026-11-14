import * as acp from '@agentclientprotocol/sdk/unstable';
import { describe, expect, it } from 'vitest';
import type { AcpUpdate, PermissionRequest } from '../../domain/model.ts';
import type { AgentHandlers } from '../../domain/ports.ts';
import { MemoryLogger } from '../fake/memory.ts';
import { AcpConnector, type AcpTransport, authenticatedFrom, type Exit } from './acp-connector.ts';

const EMAIL = 'someone@example.com';

/**
 * A fake ACP agent, in process, speaking the protocol through the SDK: no real agent is started.
 * It behaves like the adapters recorded on the Thor (bridge/fixtures/acp-raw).
 */
function fakeAgent(): { transport: AcpTransport; received: string[] } {
  const received: string[] = [];
  const toAgent = new TransformStream<Uint8Array, Uint8Array>();
  const toClient = new TransformStream<Uint8Array, Uint8Array>();
  let exit!: (value: Exit) => void;
  const exited = new Promise<Exit>((resolve) => {
    exit = resolve;
  });
  const app = acp
    .agent({ name: 'fake-agent' })
    .onRequest('initialize', async () => {
      received.push('initialize');
      return { protocolVersion: acp.PROTOCOL_VERSION, agentCapabilities: { loadSession: true } };
    })
    .onRequest('session/new', async ({ client }) => {
      received.push('session/new');
      await client.notify('_auth/status_update', {
        authStatus: { kind: 'account', account: { email: EMAIL } },
      });
      return {
        sessionId: 'fake-1',
        configOptions: [
          {
            id: 'model',
            name: 'Model',
            category: 'model',
            type: 'select',
            currentValue: 'from-agent',
            options: [{ value: 'from-agent', name: 'Agent model' }],
          },
        ],
      };
    })
    .onRequest('session/set_mode', async ({ params }) => {
      received.push(`set_mode ${params.modeId}`);
      return {};
    })
    .onRequest('session/set_config_option', async ({ params }) => {
      received.push(`config ${params.sessionId} ${params.configId} ${params.value}`);
      return { configOptions: [] };
    })
    .onRequest('session/prompt', async ({ params, client }) => {
      received.push('session/prompt');
      await client.notify('session/update', {
        sessionId: params.sessionId,
        update: {
          sessionUpdate: 'tool_call',
          toolCallId: 'exec-1',
          title: 'cat notes.md',
          kind: 'execute',
          status: 'in_progress',
          _meta: { terminal_info: { terminal_id: 'exec-1' } },
        },
      });
      const answer = await client.request('session/request_permission', {
        sessionId: params.sessionId,
        toolCall: { toolCallId: 'exec-1', title: 'cat notes.md', kind: 'execute' },
        options: [
          { optionId: 'allow_once', name: 'Yes, proceed', kind: 'allow_once' },
          { optionId: 'cancel', name: 'No', kind: 'reject_once' },
        ],
      });
      received.push(`answer ${JSON.stringify(answer.outcome)}`);
      return { stopReason: 'end_turn' };
    })
    .onRequest(
      '_session/steering',
      (raw: unknown) => raw,
      async () => {
        received.push('steering');
        return { outcome: 'injected' };
      },
    )
    .onNotification('session/cancel', () => {
      received.push('cancel');
    });
  const connection = app.connect(acp.ndJsonStream(toClient.writable, toAgent.readable));
  return {
    received,
    transport: {
      stream: acp.ndJsonStream(toAgent.writable, toClient.readable),
      exited,
      close: async () => {
        connection.close();
        exit({ code: 0, signal: null });
      },
    },
  };
}

function connector(transport: AcpTransport) {
  return new AcpConnector({
    agent: 'codex',
    open: () => transport,
    logger: new MemoryLogger(),
    versions: () => Promise.reject(new Error('not used')),
  });
}

function recordingHandlers() {
  const updates: AcpUpdate[] = [];
  const permissions: PermissionRequest[] = [];
  const auth: boolean[] = [];
  const handlers: AgentHandlers = {
    update: (_sessionId, update) => updates.push(update),
    permission: async (request) => {
      permissions.push(request);
      return { outcome: 'selected', optionId: 'allow_once' };
    },
    authenticated: (value) => auth.push(value),
  };
  return { handlers, updates, permissions, auth };
}

describe('AcpConnector', () => {
  it('drives a session: new, mode, prompt with its updates and its approval', async () => {
    const agent = fakeAgent();
    const recorded = recordingHandlers();
    const connection = await connector(agent.transport).connect(recorded.handlers);

    const sessionId = await connection.newSession('/home/dev/projects/regie-demo');
    await connection.setMode(sessionId, 'workspace-write');
    const result = await connection.prompt(sessionId, 'Lis notes.md');

    expect(result).toEqual({ stopReason: 'end_turn', usage: null });
    expect(agent.received).toEqual([
      'initialize',
      'session/new',
      'set_mode workspace-write',
      'session/prompt',
      'answer {"outcome":"selected","optionId":"allow_once"}',
    ]);
    expect(recorded.updates).toEqual([
      expect.objectContaining({
        sessionUpdate: 'config_option_update',
        configOptions: [expect.objectContaining({ id: 'model', currentValue: 'from-agent' })],
      }),
      expect.objectContaining({
        sessionUpdate: 'tool_call',
        toolCallId: 'exec-1',
        _meta: { terminal_info: { terminal_id: 'exec-1' } },
      }),
    ]);
    expect(recorded.permissions[0]?.options.map((o) => o.optionId)).toEqual([
      'allow_once',
      'cancel',
    ]);
  });

  it('transports the requested collaboration configuration to ACP', async () => {
    const agent = fakeAgent();
    const recorded = recordingHandlers();
    const connection = await connector(agent.transport).connect(recorded.handlers);
    await connection.setConfig('fake-1', 'collaboration_mode', 'plan');
    expect(agent.received).toContain('config fake-1 collaboration_mode plan');
    expect(recorded.updates).toEqual([
      { sessionUpdate: 'config_option_update', configOptions: [] },
    ]);
    await connection.close();
  });

  it('passes on the login state only, never the account', async () => {
    const agent = fakeAgent();
    const recorded = recordingHandlers();
    const connection = await connector(agent.transport).connect(recorded.handlers);
    await connection.newSession('/tmp');

    expect(recorded.auth).toEqual([true]);
    expect(JSON.stringify(recorded)).not.toContain(EMAIL);
  });

  it('steers a running turn and cancels it', async () => {
    const agent = fakeAgent();
    const connection = await connector(agent.transport).connect(recordingHandlers().handlers);

    await expect(connection.steer('fake-1', 'Arrête')).resolves.toBe(true);
    await connection.cancel('fake-1');
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(agent.received).toContain('steering');
    expect(agent.received).toContain('cancel');
  });

  it('reads the logged-in state of both auth notifications', () => {
    expect(authenticatedFrom('auth/status_update', { state: 'authenticated' })).toBe(true);
    expect(authenticatedFrom('auth/status_update', { state: 'unauthenticated' })).toBe(false);
    expect(authenticatedFrom('_auth/status_update', { authStatus: { kind: 'account' } })).toBe(
      true,
    );
    expect(authenticatedFrom('_auth/status_update', { authStatus: {} })).toBe(false);
    expect(authenticatedFrom('_other', {})).toBeNull();
  });
});
