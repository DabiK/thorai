const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;

/**
 * Hides e-mail addresses. The agents name the account in some messages: it is never logged nor
 * relayed (docs/adr/02).
 */
export function redact(text: string): string {
  return text.replace(EMAIL, '<e-mail>');
}
