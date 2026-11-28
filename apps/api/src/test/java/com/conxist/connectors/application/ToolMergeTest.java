package com.conxist.connectors.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.conxist.connectors.domain.ApiParam;
import com.conxist.connectors.domain.ApiTool;
import com.conxist.connectors.domain.Integration;
import org.junit.jupiter.api.Test;

class ToolMergeTest {

    @Test
    void a_full_import_never_pushes_out_tools_a_person_wrote_or_agents_use() {
        ApiTool handWritten = get("my_report", false, true);
        ApiTool used = get("get_deal", true, true);
        ApiTool unused = get("get_old", true, true);
        List<ApiTool> imported = new ArrayList<>();
        for (int n = 0; n < Integration.MAX_TOOLS; n++) {
            imported.add(get("op_" + n, true, false));
        }

        ToolMerge.Merged merged = ToolMerge.merge(List.of(handWritten, used, unused), imported, Set.of("get_deal"),
                false);

        assertThat(merged.tools()).hasSize(Integration.MAX_TOOLS);
        assertThat(merged.tools()).extracting(ApiTool::name).contains("my_report", "get_deal").doesNotContain("get_old");
        assertThat(merged.kept()).containsExactly("get_deal");
        assertThat(merged.overflow()).containsExactly("op_198", "op_199");
        // Built into an API, it holds: no more than the cap.
        assertThat(merged.tools().size()).isLessThanOrEqualTo(Integration.MAX_TOOLS);
    }

    @Test
    void a_tick_follows_a_tool_that_changes_things_only_while_it_makes_the_same_call() {
        ApiTool create = change("create_note", "/notes", true);
        ApiTool sameAgain = change("create_note", "/notes", false);
        ApiTool elsewhere = change("create_note", "/accounts/remove", false);

        assertThat(ToolMerge.merge(List.of(create), List.of(sameAgain), Set.of(), false).tools().getFirst().allowed())
                .isTrue();
        assertThat(ToolMerge.merge(List.of(create), List.of(elsewhere), Set.of(), false).tools().getFirst().allowed())
                .isFalse();

        // While agents use it, the earlier call stays as it was.
        ToolMerge.Merged inUse = ToolMerge.merge(List.of(create), List.of(elsewhere), Set.of("create_note"), false);
        assertThat(inUse.tools()).singleElement().isEqualTo(create);
        assertThat(inUse.kept()).containsExactly("create_note");
    }

    @Test
    void on_the_first_import_only_new_tools_that_read_come_ticked() {
        ToolMerge.Merged merged = ToolMerge.merge(List.of(), List.of(get("list_deals", false, false),
                change("create_deal", "/deals", false)), Set.of(), true);

        assertThat(merged.tools()).extracting(ApiTool::allowed).containsExactly(true, false);
        assertThat(merged.added()).containsExactly("list_deals", "create_deal");
    }

    private static ApiTool get(String name, boolean imported, boolean allowed) {
        return new ApiTool(name, null, ApiTool.Method.GET, "/" + name, "Reads " + name, List.of(), true, imported,
                allowed);
    }

    private static ApiTool change(String name, String path, boolean allowed) {
        return new ApiTool(name, null, ApiTool.Method.POST, path, "Changes " + name,
                List.of(ApiParam.simple("text", ApiParam.In.BODY, "string", false, "")), false, true, allowed);
    }
}
