import { ApiError } from '../api/client';
import type { Translate } from './i18n';
import { MESSAGES, type MessageKey } from './messages';

/** Says what went wrong in the person's language, from the stable code the API sends with each refusal. */
export function problemMessage(error: unknown, t: Translate): string {
  const key = error instanceof ApiError && error.code ? (`api.${error.code}` as MessageKey) : null;
  return key && key in MESSAGES.en ? t(key) : t('error.generic');
}
