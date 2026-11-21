package com.conxist.agents.adapter.web;

import java.util.Locale;
import com.conxist.agents.application.PersonLanguage;
import com.conxist.agents.domain.Language;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** The language the app asks for in the request's Accept-Language header: Spanish when it asks for it, else English. */
@Component
class RequestLanguage implements PersonLanguage {

    @Override
    public Language language() {
        Locale locale = LocaleContextHolder.getLocale();
        return "es".equals(locale.getLanguage()) ? Language.ES : Language.EN;
    }
}
