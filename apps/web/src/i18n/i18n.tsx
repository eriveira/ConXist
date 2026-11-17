import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { MESSAGES, type Language, type MessageKey } from './messages';

export type Translate = (key: MessageKey, values?: Record<string, string | number>) => string;

export function translator(language: Language): Translate {
  return (key, values = {}) =>
    MESSAGES[language][key].replace(/\{(\w+)\}/g, (match, name: string) =>
      name in values ? String(values[name]) : match);
}

const I18nContext = createContext<Translate>(translator('en'));

export function I18nProvider({ language, children }: { language: Language; children: ReactNode }) {
  const translate = useMemo(() => translator(language), [language]);
  return <I18nContext.Provider value={translate}>{children}</I18nContext.Provider>;
}

export function useT(): Translate {
  return useContext(I18nContext);
}
