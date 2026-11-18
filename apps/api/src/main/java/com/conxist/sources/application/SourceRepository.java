package com.conxist.sources.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.sources.domain.Source;

public interface SourceRepository {

    void save(Source source);

    Optional<Source> find(UUID workspaceId, UUID sourceId);

    List<Source> findAll(UUID workspaceId);

    boolean nameTaken(UUID workspaceId, String name);
}
