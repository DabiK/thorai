import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';

/** The official v2 API owns credential storage. No credential file is ever read here. */
export interface OpenCodeCredentials {
  save(provider: string, key: string): Promise<void>;
  logout(provider: string): Promise<void>;
}

export class OpenCodeApiCredentials implements OpenCodeCredentials {
  private readonly binary: string;
  constructor(binary: string) {
    this.binary = binary;
  }

  save(provider: string, key: string): Promise<void> {
    return this.withServer(async (request) => {
      await request(`/api/integration/${encodeURIComponent(provider)}/connect/key`, 'POST', {
        key,
      });
    });
  }

  logout(provider: string): Promise<void> {
    return this.withServer(async (request) => {
      // integration.get contains public connection ids, not credential values.
      const integration = (await request(
        `/api/integration/${encodeURIComponent(provider)}`,
        'GET',
      )) as { connections?: { type: string; id?: string }[] };
      for (const connection of integration.connections ?? []) {
        if (connection.type === 'credential' && connection.id)
          await request(`/api/credential/${encodeURIComponent(connection.id)}`, 'DELETE');
      }
    });
  }

  private async withServer(
    action: (
      request: (path: string, method: string, body?: unknown) => Promise<unknown>,
    ) => Promise<void>,
  ): Promise<void> {
    const password = randomBytes(32).toString('base64url');
    const child = spawn(
      this.binary,
      ['serve', '--stdio', '--hostname', '127.0.0.1', '--port', '0'],
      {
        env: { ...process.env, OPENCODE_PASSWORD: password, OPENCODE_PRINT_LOGS: '0' },
        stdio: ['pipe', 'pipe', 'ignore'],
      },
    );
    let exited = false;
    const closed = new Promise<void>((resolve) => {
      child.once('close', () => {
        exited = true;
        resolve();
      });
    });
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const url = await new Promise<URL>((resolve, reject) => {
        let pending = '';
        let settled = false;
        timer = setTimeout(() => reject(new Error('OpenCode startup timeout')), 30_000);
        child.once('error', () => reject(new Error('OpenCode unavailable')));
        child.once('close', () => reject(new Error('OpenCode closed')));
        child.stdout.setEncoding('utf8').on('data', (chunk: string) => {
          if (settled) return;
          pending += chunk;
          if (pending.length > 8192) {
            settled = true;
            reject(new Error('Invalid readiness'));
            return;
          }
          const line = pending.split('\n')[0];
          if (!pending.includes('\n') || !line) return;
          try {
            const url = new URL((JSON.parse(line) as { url: string }).url);
            if (
              url.protocol !== 'http:' ||
              url.hostname !== '127.0.0.1' ||
              url.username ||
              url.password
            )
              throw new Error('Invalid endpoint');
            settled = true;
            pending = '';
            resolve(url);
          } catch {
            reject(new Error('Invalid readiness'));
          }
        });
      });
      clearTimeout(timer);
      const request = async (path: string, method: string, body?: unknown): Promise<unknown> => {
        const response = await fetch(new URL(path, url), {
          method,
          headers: {
            authorization: `Basic ${Buffer.from(`opencode:${password}`).toString('base64')}`,
            'content-type': 'application/json',
          },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
          signal: AbortSignal.timeout(30_000),
        });
        // Never read an error body, which could echo the key.
        if (!response.ok) {
          await response.body?.cancel();
          throw new Error(`OpenCode credential operation failed (HTTP ${response.status})`);
        }
        if (response.status === 204) return null;
        const result: unknown = await response.json();
        return typeof result === 'object' && result !== null && 'data' in result
          ? result.data
          : result;
      };
      // Like `opencode auth login`: initial provider plugins settle at model.default.
      await request('/api/model/default', 'GET');
      await action(request);
    } finally {
      clearTimeout(timer);
      child.stdin.end();
      if (!exited) {
        const kill = setTimeout(() => child.kill('SIGKILL'), 3000);
        await closed;
        clearTimeout(kill);
      }
    }
  }
}
