import { createHash, timingSafeEqual } from 'node:crypto';
import { createServer, type IncomingMessage } from 'node:http';
import type { AddressInfo } from 'node:net';
import { type WebSocket, WebSocketServer } from 'ws';
import type { Bridge } from '../../domain/bridge.ts';
import { BridgeError, type BridgeEvent } from '../../domain/model.ts';
import type { Logger } from '../../domain/ports.ts';
import { isMethod, type Method, type Params, paramsSchemas, requestSchema } from './protocol.ts';

/** The bridge listens on the loopback only: never on 0.0.0.0. */
export const HOST = '127.0.0.1';

export interface WsServerOptions {
  readonly port: number;
  readonly token: string;
  readonly bridge: Bridge;
  readonly logger: Logger;
  readonly info: { readonly version: string; readonly pid: number; readonly startedAt: string };
  /** Called after answering `bridge.shutdown`. */
  readonly onShutdown: () => void;
  readonly heartbeatMs?: number;
}

export interface RunningServer {
  readonly port: number;
  close(): Promise<void>;
}

const MAX_PAYLOAD = 1024 * 1024;

/**
 * The WebSocket the app talks to. The token is checked on the HTTP upgrade, before any data:
 * a client without it gets a 401 and the socket is closed.
 */
export function startWsServer(options: WsServerOptions): Promise<RunningServer> {
  const { logger } = options;
  const expected = digest(options.token);
  const http = createServer((request, response) => {
    if (request.url === '/health') {
      response.writeHead(200, { 'content-type': 'application/json' }).end('{"ok":true}');
    } else {
      response.writeHead(426).end();
    }
  });
  const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_PAYLOAD });
  const alive = new WeakSet<WebSocket>();

  http.on('upgrade', (request, socket, head) => {
    if (!authorized(request, expected)) {
      logger.warn('connection refused: missing or wrong token');
      socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');
      return;
    }
    wss.handleUpgrade(request, socket, head, (ws) => {
      alive.add(ws);
      ws.on('pong', () => alive.add(ws));
      serve(ws, options);
    });
  });

  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!alive.has(ws)) {
        ws.terminate();
        continue;
      }
      alive.delete(ws);
      ws.ping();
    }
  }, options.heartbeatMs ?? 20_000);

  return new Promise((resolve, reject) => {
    http.once('error', reject);
    http.listen(options.port, HOST, () => {
      const port = (http.address() as AddressInfo).port;
      logger.info(`listening on ws://${HOST}:${port}`);
      resolve({
        port,
        close: () =>
          new Promise((done) => {
            clearInterval(heartbeat);
            for (const ws of wss.clients) ws.close(1001, 'bridge stopping');
            wss.close();
            http.close(() => done());
            http.closeAllConnections();
          }),
      });
    });
  });
}

function serve(ws: WebSocket, options: WsServerOptions): void {
  const { bridge, logger } = options;
  let unsubscribe: (() => void) | null = null;
  const send = (frame: unknown) => {
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(frame));
  };
  const sendEvent = (event: BridgeEvent) => send({ event });
  ws.on('close', () => unsubscribe?.());

  const handlers: { [M in Method]: (params: Params<M>) => unknown } = {
    hello: (params) => {
      if (unsubscribe !== null) throw new BridgeError('bad_request', 'Déjà abonné aux événements.');
      const lastSeq = bridge.lastSeq;
      const since = params.since ?? lastSeq;
      const resync = since > lastSeq;
      return {
        result: {
          bridge: options.info,
          lastSeq,
          resync,
          sessions: bridge.listSessions(),
          approvals: bridge.pendingApprovals(),
          agents: bridge.agentStates(),
        },
        // Sent right after the response: the events after `since`, then the live ones.
        after: () => {
          unsubscribe = bridge.subscribe(resync ? lastSeq : since, sendEvent);
        },
      };
    },
    'sessions.list': () => ({ sessions: bridge.listSessions() }),
    'session.create': async (params) => ({
      session: await bridge.createSession({
        agent: params.agent,
        project: params.project,
        branch: params.branch ?? undefined,
        approvalMode: params.approvalMode,
        prompt: params.prompt ?? undefined,
      }),
    }),
    'session.prompt': async (params) => ({
      delivery: await bridge.prompt(params.sessionId, params.text),
    }),
    'session.cancel': async (params) => {
      await bridge.cancel(params.sessionId);
      return {};
    },
    'session.resume': async (params) => ({ session: await bridge.resume(params.sessionId) }),
    'session.events': (params) => ({
      events: bridge.sessionEvents(params.sessionId, params.since),
    }),
    'approval.answer': (params) => {
      bridge.answerApproval(params.approvalId, params.optionId);
      return {};
    },
    'approvals.list': () => ({ approvals: bridge.pendingApprovals() }),
    'agents.versions': async () => ({ agents: await bridge.versions() }),
    'projects.list': async () => ({ projects: await bridge.listProjects() }),
    'bridge.shutdown': () => ({ result: {}, after: options.onShutdown }),
  };

  ws.on('message', async (data, isBinary) => {
    let id: string | number | null = null;
    try {
      if (isBinary) throw new BridgeError('bad_request', 'Message binaire refusé.');
      const request = requestSchema.parse(JSON.parse(data.toString()));
      id = request.id;
      if (!isMethod(request.method)) {
        throw new BridgeError('bad_request', `Méthode inconnue : ${request.method}.`);
      }
      const method = request.method;
      const params = paramsSchemas[method].parse(request.params ?? {});
      const handler = handlers[method] as (params: unknown) => unknown;
      const outcome = await handler(params);
      if (isDeferred(outcome)) {
        send({ id, result: outcome.result });
        outcome.after();
      } else {
        send({ id, result: outcome });
      }
    } catch (error) {
      const failure = toError(error);
      if (failure.code === 'internal') logger.error(`request failed: ${String(error)}`);
      send({ id, error: failure });
    }
  });
}

interface Deferred {
  readonly result: unknown;
  readonly after: () => void;
}

function isDeferred(value: unknown): value is Deferred {
  return typeof value === 'object' && value !== null && 'after' in value && 'result' in value;
}

function toError(error: unknown): { code: string; message: string } {
  if (error instanceof BridgeError) return { code: error.code, message: error.message };
  if (error instanceof SyntaxError) return { code: 'bad_request', message: 'JSON invalide.' };
  if (typeof error === 'object' && error !== null && 'issues' in error) {
    return { code: 'bad_request', message: `Paramètres invalides : ${String(error)}` };
  }
  return { code: 'internal', message: 'Erreur interne du pont.' };
}

function authorized(request: IncomingMessage, expected: Buffer): boolean {
  const header = request.headers.authorization ?? '';
  const match = /^Bearer (.+)$/.exec(header);
  if (match?.[1] === undefined) return false;
  // Same length digests: the comparison takes the same time whatever the token sent.
  return timingSafeEqual(digest(match[1]), expected);
}

function digest(value: string): Buffer {
  return createHash('sha256').update(value).digest();
}
