import * as acp from '@agentclientprotocol/sdk/unstable';
import type { AcpUpdate, AgentKind, PermissionOption } from '../../domain/model.ts';
import type { AgentConnection, AgentConnector, AgentHandlers, Logger } from '../../domain/ports.ts';

export type Exit = { readonly code: number | null; readonly signal: string | null };

/** A running ACP adapter: its message stream, and how to stop it. */
export interface AcpTransport {
  readonly stream: acp.Stream;
  readonly exited: Promise<Exit>;
  /** Stops the adapter: stdin closed, then SIGTERM, then SIGKILL. */
  close(): Promise<void>;
}

export interface AcpConnectorOptions {
  readonly agent: AgentKind;
  readonly open: () => AcpTransport;
  readonly versions: AgentConnector['versions'];
  readonly logger: Logger;
  /** How long `initialize` may take. */
  readonly initializeTimeoutMs?: number;
}

const CLIENT_INFO = { name: 'regie-bridge', version: '0.1.0' };

/**
 * One agent reached through its ACP adapter (docs/adr/02), with `@agentclientprotocol/sdk`.
 *
 * The account the agent reports (`auth/status_update`, `_auth/status_update`, with the e-mail)
 * goes no further than this class: only the logged-in state is passed on.
 */
export class AcpConnector implements AgentConnector {
  readonly agent: AgentKind;
  private readonly options: AcpConnectorOptions;

  constructor(options: AcpConnectorOptions) {
    this.agent = options.agent;
    this.options = options;
  }

  versions() {
    return this.options.versions();
  }

  async connect(handlers: AgentHandlers): Promise<AgentConnection> {
    const { logger } = this.options;
    const transport = this.options.open();
    const app = acp
      .client({ name: CLIENT_INFO.name })
      .onRequest('session/request_permission', async ({ params }) => {
        const outcome = await handlers.permission({
          sessionId: params.sessionId,
          toolCall: params.toolCall as unknown as Record<string, unknown>,
          options: params.options as readonly PermissionOption[],
        });
        return { outcome };
      })
      .onNotification('session/update', ({ params }) => {
        handlers.update(params.sessionId, params.update as unknown as AcpUpdate);
      })
      .onNotification('*', ({ method, params }) => {
        const authenticated = authenticatedFrom(method, params);
        if (authenticated !== null) handlers.authenticated(authenticated);
        else logger.info(`${this.agent}: notification ignored: ${method}`);
      })
      .onRequest('*', ({ method }) => {
        logger.warn(`${this.agent}: request refused: ${method}`);
        throw acp.RequestError.methodNotFound(method);
      });
    const connection = app.connect(transport.stream);
    const agent = connection.agent;
    const exited = transport.exited.then((exit) => {
      connection.close();
      return exit;
    });
    try {
      await withTimeout(
        agent.request('initialize', {
          protocolVersion: acp.PROTOCOL_VERSION,
          clientCapabilities: {
            fs: { readTextFile: false, writeTextFile: false },
            terminal: false,
          },
          clientInfo: CLIENT_INFO,
        }),
        this.options.initializeTimeoutMs ?? 60_000,
        'initialize',
      );
    } catch (error) {
      await transport.close();
      throw error;
    }
    const prompt = (text: string) => [{ type: 'text' as const, text }];
    return {
      newSession: async (cwd) => {
        const response = await agent.request('session/new', { cwd, mcpServers: [] });
        return response.sessionId;
      },
      loadSession: async (sessionId, cwd) => {
        await agent.request('session/load', { sessionId, cwd, mcpServers: [] });
      },
      setMode: async (sessionId, modeId) => {
        await agent.request('session/set_mode', { sessionId, modeId });
      },
      setConfig: async (sessionId, configId, value) => {
        await agent.request('session/set_config_option', { sessionId, configId, value });
      },
      prompt: async (sessionId, text) => {
        const response = await agent.request<acp.PromptResponse>('session/prompt', {
          sessionId,
          prompt: prompt(text),
        });
        return { stopReason: response.stopReason, usage: response.usage ?? null };
      },
      steer: async (sessionId, text) => {
        const response = await agent.request<{ outcome?: unknown }>('_session/steering', {
          sessionId,
          prompt: prompt(text),
        });
        return response?.outcome === 'injected';
      },
      cancel: async (sessionId) => {
        await agent.notify('session/cancel', { sessionId });
      },
      close: () => transport.close(),
      exited,
    };
  }
}

/**
 * The logged-in state in an auth notification, `null` for any other notification. The rest of
 * the payload (account, e-mail, organisation) is dropped here.
 */
export function authenticatedFrom(method: string, params: unknown): boolean | null {
  if (typeof params !== 'object' || params === null) return null;
  if (method === 'auth/status_update') {
    return (params as { state?: unknown }).state === 'authenticated';
  }
  if (method === '_auth/status_update') {
    const status = (params as { authStatus?: { kind?: unknown } }).authStatus;
    return status?.kind === 'account';
  }
  return null;
}

function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label}: no answer after ${ms} ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}
