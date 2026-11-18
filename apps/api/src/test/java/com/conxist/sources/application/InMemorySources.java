package com.conxist.sources.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.sources.domain.Source;

class InMemorySources implements SourceRepository {

    private final List<Source> saved = new ArrayList<>();

    @Override
    public void save(Source source) {
        saved.add(source);
    }

    @Override
    public Optional<Source> find(UUID workspaceId, UUID sourceId) {
        return saved.stream().filter(s -> s.workspaceId().equals(workspaceId) && s.id().equals(sourceId)).findFirst();
    }

    @Override
    public List<Source> findAll(UUID workspaceId) {
        return saved.stream().filter(s -> s.workspaceId().equals(workspaceId)).toList();
    }

    @Override
    public boolean nameTaken(UUID workspaceId, String name) {
        return findAll(workspaceId).stream().anyMatch(s -> s.name().equalsIgnoreCase(name));
    }
}
