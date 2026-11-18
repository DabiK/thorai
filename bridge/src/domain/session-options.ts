/** ACP selections kept as agent-owned identifiers, never a local model catalogue. */
export interface SessionOption {
  readonly id: string;
  readonly name: string;
  readonly category: string | null;
  readonly currentValue: string | null;
  readonly options: readonly { readonly value: string; readonly name: string }[];
}
function object(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
}
export function sessionOptions(raw: unknown, agent?: string): readonly SessionOption[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry) => {
    const o = object(entry);
    if (o.type !== 'select' || typeof o.id !== 'string' || typeof o.name !== 'string') return [];
    const values = Array.isArray(o.options)
      ? o.options.flatMap((value: unknown) => {
          const group = object(value);
          return Array.isArray(group.options) ? group.options : [value];
        })
      : [];
    return [
      {
        id: o.id,
        name: agent === 'opencode' && o.id === 'mode' ? 'Agent opencode' : o.name,
        category:
          agent === 'opencode' && o.id === 'mode'
            ? 'agent'
            : typeof o.category === 'string'
              ? o.category
              : null,
        currentValue: typeof o.currentValue === 'string' ? o.currentValue : null,
        options: values.flatMap((value: unknown) => {
          const v = object(value);
          return typeof v.value === 'string' && typeof v.name === 'string'
            ? [{ value: v.value, name: v.name }]
            : [];
        }),
      },
    ];
  });
}
/** Permission and collaboration selectors use the product's explicit mode instead. */
export function editableOptions(
  options: readonly SessionOption[],
  agent?: string,
): readonly SessionOption[] {
  if (agent === 'opencode') return options;
  return options.filter(
    (o) =>
      o.category !== 'mode' &&
      o.category !== 'collaboration_mode' &&
      o.id !== 'mode' &&
      o.id !== 'collaboration_mode',
  );
}
