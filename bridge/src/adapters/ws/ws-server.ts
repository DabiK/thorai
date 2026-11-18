import { createHash, timingSafeEqual } from 'node:crypto';
import { createServer, type IncomingMessage } from 'node:http';
import type { AddressInfo } from 'node:net';
import { type WebSocket, WebSocketServer } from 'ws';
import type { Authentication } from '../../domain/authentication.ts';
import type { Bridge } from '../../domain/bridge.ts';
import { BridgeError, type BridgeEvent } from '../../domain/model.ts';
import type { Logger } from '../../domain/ports.ts';
import { isMethod, type Method, type Params, paramsSchemas, requestSchema } from './protocol.ts';

/** The bridge listens on the loopback only: never on 0.0.0.0. */
export const HOST = '127.0.0.1';

export interface ListenOptions {
  readonly port: number;
  readonly token: string;
  readonly logger: Logger;
  readonly info: { readonly version: string; readonly pid: number; readonly startedAt: string };
  readonly heartbeatMs?: number;
}

export interface ServeOptions {
  readonly bridge: Bridge;
  readonly authentication?: Authentication;
  /** Called after answering `bridge.shutdown`. */
  readonly onShutdown: () => void;
}

export type WsServerOptions = ListenOptions & ServeOptions;

export interface RunningServer {
  readonly port: number;
  /** The address the server really listens on. */
  readonly address: string;
  close(): Promise<void>;
}

/** A server that holds its port, and serves once the bridge is attached. */
export interface ListeningServer extends RunningServer {
  attach(serve: ServeOptions): void;
}

const MAX_PAYLOAD = 1024 * 1024;

/**
 * The WebSocket the app talks to. The token is checked on the HTTP upgrade, before any data:
 * a client without it gets a 401 and the socket is closed.
 */
export async function startWsServer(options: WsServerOptions): Promise<RunningServer> {
  const server = await listenWs(options);
  server.attach(options);
  return server;
}

/**
 * Takes the port first: a second bridge fails here, before reading or writing anything. Until
 * `attach`, connections are refused with a 503.
 */
export function listenWs(options: ListenOptions): Promise<ListeningServer> {
  const { logger } = options;
  let served: (ListenOptions & ServeOptions) | null = null;
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
    if (served === null) {
      socket.end(
        'HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n',
      );
      return;
    }
    const serving = served;
    if (!authorized(request, expected)) {
      logger.warn('connection refused: missing or wrong token');
      socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');
      return;
    }
    wss.handleUpgrade(request, socket, head, (ws) => {
      alive.add(ws);
      ws.on('pong', () => alive.add(ws));
      serve(ws, serving);
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
      const { port, address } = http.address() as AddressInfo;
      logger.info(`listening on ws://${address}:${port}`);
      resolve({
        port,
        address,
        attach: (serve) => {
          served = { ...options, ...serve };
        },
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
  let greeted = false;
  const send = (frame: unknown) => {
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(frame));
  };
  const sendEvent = (event: BridgeEvent) => send({ event });
  ws.on('close', () => {
    unsubscribe?.();
    unsubscribe = null;
  });

  const authentication = () => {
    if (!options.authentication)
      throw new BridgeError('bad_request', 'Connexion des agents indisponible sur ce pont.');
    return options.authentication;
  };
  const handlers: { [M in Method]: (params: Params<M>) => unknown } = {
    'auth.status': async () => ({ agents: await authentication().list() }),
    'auth.start': async (params) => ({
      authentication: await authentication().start(params.agent),
    }),
    'auth.logout': async (params) => ({
      authentication: await authentication().logout(params.agent),
    }),
    'auth.cancel': async (params) => ({
      authentication: await authentication().cancel(params.agent),
    }),
    hello: (params) => {
      // Set at once: a second `hello`, even sent right after the first, is refused.
      if (greeted) throw new BridgeError('bad_request', 'Déjà abonné aux événements.');
      greeted = true;
      const lastSeq = bridge.lastSeq;
      const since = params.since ?? lastSeq;
      // Another journal (deleted, restored): the client's numbers mean nothing here.
      const otherJournal = params.journal != null && params.journal !== bridge.journalId;
      const resync = since > lastSeq || otherJournal;
      return {
        result: {
          bridge: options.info,
          journal: bridge.journalId,
          lastSeq,
          resync,
          sessions: bridge.listSessions(),
          approvals: bridge.pendingApprovals(),
          agents: bridge.agentStates(),
        },
        // Sent right after the response: the events after `since`, then the live ones.
        after: () => {
          if (ws.readyState === ws.OPEN) {
            unsubscribe = bridge.subscribe(resync ? lastSeq : since, sendEvent);
          }
        },
      };
    },
    'agent.options': async (params) => ({
      configOptions: await bridge.agentOptions(params.agent, params.configValues),
    }),
    'session.configure': async (params) => ({
      session: await bridge.configureSession(params.sessionId, params),
    }),
    'sessions.list': () => ({ sessions: bridge.listSessions() }),
    'session.create': async (params) => ({
      session: await bridge.createSession({
        agent: params.agent,
        project: params.project,
        newProject: params.newProject,
        configValues: params.configValues,
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
    'session.vocabulary': async (params) => ({
      terms: await bridge.sessionVocabulary(params.sessionId),
    }),
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
