package com.conxist.connectors.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;

/**
 * Brings a fresh import of an OpenAPI description together with an API's tools (ADR 0027 §3). Tools a person wrote
 * stay and win over an imported one of the same name; an imported tool agents use stays too. Room for the tools that
 * stay is kept first, so an import never pushes them out. A tick follows a tool only while it makes the same kind of
 * call: a tool that changes things and now calls something else starts unticked, and while agents use it the earlier
 * version stays.
 */
final class ToolMerge {

    static final String FULL = "ConXist keeps up to " + Integration.MAX_TOOLS + " tools per API.";

    private ToolMerge() {
    }

    /**
     * @param added    the imported tools new to the API
     * @param kept     imported tools that stay as they were, since agents use them
     * @param overflow imported tools left out because the API is full
     */
    record Merged(List<ApiTool> tools, List<String> added, List<String> kept, List<String> overflow) {
    }

    /**
     * @param first whether this is the API's first import: then the new tools that only read come ticked
     * @param used  the names of the API's tools agents use
     */
    static Merged merge(List<ApiTool> current, List<ApiTool> imported, Set<String> used, boolean first) {
        Map<String, ApiTool> before = new LinkedHashMap<>();
        current.forEach(tool -> before.put(tool.name(), tool));
        List<ApiTool> stays = current.stream().filter(tool -> !tool.imported() || used.contains(tool.name())).toList();
        int room = Integration.MAX_TOOLS - stays.size();
        List<ApiTool> merged = new ArrayList<>();
        Set<String> replaced = new LinkedHashSet<>();
        List<String> added = new ArrayList<>();
        List<String> overflow = new ArrayList<>();
        for (ApiTool tool : imported) {
            ApiTool earlier = before.get(tool.name());
            if (earlier != null && !earlier.imported()) {
                continue;
            }
            boolean inUse = earlier != null && used.contains(tool.name());
            if (inUse && !tool.reads() && (earlier.reads() || !earlier.sameCall(tool))) {
                continue;
            }
            if (!inUse) {
                if (room <= 0) {
                    overflow.add(tool.name());
                    continue;
                }
                room--;
            }
            if (earlier == null) {
                added.add(tool.name());
            }
            merged.add(tool.withAllowed(earlier == null ? first && tool.reads() : keepsTick(earlier, tool)));
            replaced.add(tool.name());
        }
        List<String> kept = new ArrayList<>();
        for (ApiTool tool : stays) {
            if (!replaced.contains(tool.name())) {
                merged.add(tool);
                if (tool.imported()) {
                    kept.add(tool.name());
                }
            }
        }
        return new Merged(merged, added, kept, overflow);
    }

    /**
     * Whether {@code next} keeps {@code earlier}'s tick: a tool that reads keeps it, and so does one that changes
     * things only when it changed things before and still makes the same call.
     */
    static boolean keepsTick(ApiTool earlier, ApiTool next) {
        return earlier.allowed() && (next.reads() || (!earlier.reads() && earlier.sameCall(next)));
    }
}
