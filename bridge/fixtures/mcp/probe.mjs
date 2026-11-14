// Deliberately tiny local MCP fixture. No Android, network or model access.
import { randomUUID } from 'node:crypto';
import { appendFileSync } from 'node:fs';
import { createInterface } from 'node:readline';
const [log, owner] = process.argv.slice(2);
function record(entry) {
  appendFileSync(log, `${JSON.stringify({ owner, pid: process.pid, ...entry })}\n`);
}
const tool = {
  name: 'regie_probe', description: 'Return a fresh contract receipt using the global instruction marker.',
  inputSchema: { type: 'object', properties: { marker: { type: 'string' } }, required: ['marker'], additionalProperties: false },
  annotations: { readOnlyHint: false, destructiveHint: false },
};
record({ method: 'started' });
const lines = createInterface({ input: process.stdin });
lines.on('line', (line) => {
  const request = JSON.parse(line);
  if (request.id === undefined) return;
  let result;
  switch (request.method) {
    case 'initialize':
      result = { protocolVersion: request.params.protocolVersion, capabilities: { tools: {} }, serverInfo: { name: 'regie-contract', version: '1.0.0' } };
      break;
    case 'tools/list': result = { tools: [tool] }; break;
    case 'tools/call':
      if (request.params.name !== tool.name) throw new Error('Unexpected tool');
      result = { content: [{ type: 'text', text: `RECEIPT-${randomUUID()}` }] };
      break;
    case 'resources/list': result = { resources: [] }; break;
    case 'prompts/list': result = { prompts: [] }; break;
    case 'ping': result = {}; break;
    default:
      process.stdout.write(`${JSON.stringify({ jsonrpc: '2.0', id: request.id, error: { code: -32601, message: 'Method not found' } })}\n`);
      return;
  }
  record({ method: request.method, params: request.method === 'tools/call' ? { name: request.params.name, arguments: request.params.arguments } : undefined, result });
  process.stdout.write(`${JSON.stringify({ jsonrpc: '2.0', id: request.id, result })}\n`);
});
lines.on('close', () => { record({ method: 'stopped' }); process.exit(0); });
