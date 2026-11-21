package com.conxist.agents.application;

import com.conxist.agents.domain.Language;

/** The language of the person making the request, for what ConXist writes itself, such as built-in skills. */
@FunctionalInterface
public interface PersonLanguage {

    Language language();
}
