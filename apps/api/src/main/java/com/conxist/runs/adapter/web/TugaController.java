package com.conxist.runs.adapter.web;

import java.time.Instant;
import java.util.List;
import com.conxist.runs.application.ModelStart;
import com.conxist.runs.application.Tuga;
import com.conxist.runs.application.TugaAnswer;
import com.conxist.runs.application.TugaSettingsRepository;
import com.conxist.runs.domain.TugaPrompt;
import com.conxist.runs.domain.TugaSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tuga (ADR 0027): its settings in Settings › Tuga, and the chat. A chat is not kept: the app sends the messages so far
 * with each new one, and the answer comes back when it is done.
 */
@RestController
@RequestMapping("/api/tuga")
class TugaController {

    private final Tuga tuga;

    TugaController(Tuga tuga) {
        this.tuga = tuga;
    }

    @GetMapping
    TugaView view() {
        return view(tuga.saved());
    }

    /**
     * Saves everything at once, if nothing changed since {@code revision} was read (409 otherwise); a {@code prompt}
     * that is missing or empty restores ConXist's prompt.
     */
    @PutMapping
    TugaView change(@Valid @RequestBody TugaRequest request) {
        return view(tuga.change(new TugaSettings(request.provider(), request.model(), request.effort(),
                request.prompt(), null), request.revision()));
    }

    @PostMapping("/chat")
    TugaAnswer ask(@Valid @RequestBody ChatRequest request) {
        return tuga.ask(request.messages().stream()
                .map(message -> new Tuga.ChatMessage(message.from() == From.PERSON, message.text())).toList());
    }

    private TugaView view(TugaSettingsRepository.Saved saved) {
        TugaSettings settings = saved.settings();
        ModelStart model = tuga.modelOf(settings);
        return new TugaView(saved.revision(), tuga.ready(settings), new Model(model.provider(), model.model(),
                model.effort().name()), settings.modelPicked(),
                settings.promptEdited() ? settings.prompt() : TugaPrompt.DEFAULT_PROMPT, TugaPrompt.DEFAULT_PROMPT,
                settings.promptEdited(), settings.promptChangedAt(), TugaPrompt.MAX_PROMPT, Tuga.MAX_MESSAGES,
                Tuga.MAX_PERSON_MESSAGE);
    }

    /** @param prompt the workspace's version of Tuga's prompt, or null or empty for ConXist's */
    record TugaRequest(@NotNull Long revision, String provider, String model, String effort, String prompt) {
    }

    record ChatRequest(@NotEmpty @Size(max = Tuga.MAX_MESSAGES) List<@Valid @NotNull ChatMessage> messages) {

        @Override
        public String toString() {
            return "ChatRequest[" + (messages == null ? 0 : messages.size()) + " messages]";
        }
    }

    record ChatMessage(@NotNull From from, @NotNull @Size(max = Tuga.MAX_TUGA_MESSAGE) String text) {

        @Override
        public String toString() {
            return "ChatMessage[from=" + from + ", " + (text == null ? 0 : text.length()) + " characters]";
        }
    }

    enum From { PERSON, TUGA }

    record Model(String provider, String model, String effort) {
    }

    /**
     * @param ready         whether Tuga can answer now
     * @param model         what Tuga answers on: the workspace's pick, else where new agents start
     * @param modelPicked   whether the workspace picked it
     * @param prompt        the part a person can edit: the workspace's version, or ConXist's when it has none
     * @param conxistPrompt ConXist's current prompt, which Restore puts back
     * @param revision      what a change must send back, so a stale tab cannot undo a newer change
     */
    record TugaView(long revision, boolean ready, Model model, boolean modelPicked, String prompt,
            String conxistPrompt, boolean promptEdited, Instant promptChangedAt, int maxPrompt, int maxMessages,
            int maxMessage) {
    }
}
