import * as acp from '@agentclientprotocol/sdk/unstable';
import { expect, it } from 'vitest';
import type { AgentKind } from '../../domain/model.ts';
import { MemoryLogger } from '../fake/memory.ts';
import { AcpConnector } from './acp-connector.ts';

it.each<AgentKind>(['claude', 'codex'])(
  '%s keeps agent-owned configuration on creation and reload',
  async (kind) => {
    const requests: unknown[] = [];
    const outbound = new TransformStream<Uint8Array, Uint8Array>();
    const inbound = new TransformStream<Uint8Array, Uint8Array>();
    const server = acp
      .agent({ name: 'configuration-contract' })
      .onRequest('initialize', () => ({ protocolVersion: acp.PROTOCOL_VERSION }))
      .onRequest('session/new', ({ params }) => {
        requests.push(params);
        return { sessionId: 'configured' };
      })
      .onRequest('session/load', ({ params }) => {
        requests.push(params);
        return {};
      })
      .connect(acp.ndJsonStream(inbound.writable, outbound.readable));
    const connector = new AcpConnector({
      agent: kind,
      logger: new MemoryLogger(),
      versions: async () => {
        throw new Error('not used');
      },
      open: () => ({
        stream: acp.ndJsonStream(outbound.writable, inbound.readable),
        exited: new Promise(() => {}),
        close: async () => server.close(),
      }),
    });
    const client = await connector.connect({
      update: () => {},
      authenticated: () => {},
      permission: async () => ({ outcome: 'cancelled' }),
    });
    try {
      const sessionId = await client.newSession('/projects/demo');
      await client.loadSession(sessionId, '/projects/demo');
      const expected = {
        cwd: '/projects/demo',
        // These are client additions, not the agent's configured servers.
        mcpServers: [],
        ...(kind === 'claude'
          ? {
              _meta: {
                claudeCode: {
                  options: {
                    settingSources: ['user', 'project', 'local'],
                    strictMcpConfig: false,
                  },
                },
              },
            }
          : {}),
      };
      expect(requests).toEqual([expected, { ...expected, sessionId: 'configured' }]);
    } finally {
      await client.close();
    }
  },
);
