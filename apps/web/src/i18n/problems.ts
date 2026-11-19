import { ApiError } from '../api/client';
import type { Translate } from './i18n';
import { MESSAGES, type MessageKey } from './messages';

/** Says what went wrong in the person's language, from the stable code the API sends with each refusal. */
export function problemMessage(error: unknown, t: Translate): string {
  return error instanceof ApiError && error.code ? problemText(error.code, t) : t('error.generic');
}

/** The person's-language text for a problem code the API stored, such as a failed check in the import history. */
export function problemText(code: string, t: Translate): string {
  const key = `api.${code}` as MessageKey;
  return key in MESSAGES.en ? t(key) : t('error.generic');
}
