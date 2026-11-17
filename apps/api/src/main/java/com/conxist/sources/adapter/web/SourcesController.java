package com.conxist.sources.adapter.web;

import java.util.Arrays;
import java.util.List;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceSummary;
import com.conxist.sources.application.AddSource;
import com.conxist.sources.application.ListSources;
import com.conxist.sources.domain.Source;
import com.conxist.sources.domain.SourceDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class SourcesController {

    private final AddSource addSource;
    private final ListSources listSources;

    SourcesController(AddSource addSource, ListSources listSources) {
        this.addSource = addSource;
        this.listSources = listSources;
    }

    @GetMapping("/providers")
    List<ProviderView> providers() {
        return Arrays.stream(Provider.values()).map(ProviderView::of).toList();
    }

    @GetMapping("/sources")
    List<SourceSummary> sources() {
        return listSources.list();
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    SourceSummary add(@Valid @RequestBody AddSourceRequest request) {
        return addSource.add(new SourceDraft(request.provider(), request.name(), request.accountLabel()));
    }

    record AddSourceRequest(
            @NotNull Provider provider,
            @Size(max = Source.MAX_NAME_LENGTH) String name,
            @Size(max = Source.MAX_ACCOUNT_LABEL_LENGTH) String accountLabel) {
    }

    record ProviderView(Provider provider, String category, String availability, String displayName,
            boolean signInRequired) {

        static ProviderView of(Provider provider) {
            return new ProviderView(provider, provider.category().name(), provider.availability().name(),
                    provider.displayName(), provider.signInRequired());
        }
    }
}
