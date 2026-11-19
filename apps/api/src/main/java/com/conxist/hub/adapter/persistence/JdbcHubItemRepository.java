package com.conxist.hub.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import com.conxist.hub.application.HubItemRepository;
import com.conxist.hub.domain.HubItem;
import com.conxist.hub.domain.HubItemStatus;
import com.conxist.hub.domain.HubItemType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcHubItemRepository implements HubItemRepository {

    private final JdbcClient jdbc;

    JdbcHubItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveIfAbsent(HubItem item) {
        jdbc.sql("""
                insert into hub_items (item_id, workspace_id, item_type, source_id, title, status, arrived_at)
                values (:itemId, :workspaceId, :type, :sourceId, :title, :status, :arrivedAt)
                on conflict (item_id) do nothing
                """)
                .param("itemId", item.itemId())
                .param("workspaceId", item.workspaceId())
                .param("type", item.type().name())
                .param("sourceId", item.sourceId())
                .param("title", item.title())
                .param("status", item.status().name())
                .param("arrivedAt", Timestamp.from(item.arrivedAt()))
                .update();
    }

    @Override
    public void markRemoved(HubItem marker) {
        jdbc.sql("""
                insert into hub_items (item_id, workspace_id, item_type, source_id, title, status, arrived_at)
                values (:itemId, :workspaceId, :type, :sourceId, '', 'REMOVED', :at)
                on conflict (item_id) do update set status = 'REMOVED', title = ''
                  where hub_items.workspace_id = excluded.workspace_id and hub_items.source_id = excluded.source_id
                    and hub_items.item_type = 'DOCUMENT'
                """)
                .param("itemId", marker.itemId())
                .param("workspaceId", marker.workspaceId())
                .param("type", marker.type().name())
                .param("sourceId", marker.sourceId())
                .param("at", Timestamp.from(marker.arrivedAt()))
                .update();
    }

    @Override
    public void retitle(UUID workspaceId, UUID sourceId, UUID itemId, String title) {
        jdbc.sql("""
                update hub_items set title = :title
                where item_id = :itemId and workspace_id = :workspaceId and source_id = :sourceId
                  and item_type = 'DOCUMENT' and status <> 'REMOVED'
                """)
                .param("title", title)
                .param("itemId", itemId)
                .param("workspaceId", workspaceId)
                .param("sourceId", sourceId)
                .update();
    }

    @Override
    public long count(UUID workspaceId, HubItemType type) {
        return jdbc.sql("select count(*) from hub_items where workspace_id = :workspaceId and item_type = :type and status <> 'REMOVED'")
                .param("workspaceId", workspaceId)
                .param("type", type.name())
                .query(Long.class)
                .single();
    }

    @Override
    public long countWithStatus(UUID workspaceId, HubItemStatus status) {
        return jdbc.sql("select count(*) from hub_items where workspace_id = :workspaceId and status = :status")
                .param("workspaceId", workspaceId)
                .param("status", status.name())
                .query(Long.class)
                .single();
    }

    @Override
    public List<HubItem> latest(UUID workspaceId, int limit) {
        return jdbc.sql("""
                select item_id, workspace_id, item_type, source_id, title, status, arrived_at
                from hub_items where workspace_id = :workspaceId and status <> 'REMOVED'
                order by arrived_at desc limit :limit
                """)
                .param("workspaceId", workspaceId)
                .param("limit", limit)
                .query(JdbcHubItemRepository::toItem)
                .list();
    }

    private static HubItem toItem(ResultSet row, int rowNumber) throws SQLException {
        return new HubItem(
                row.getObject("item_id", UUID.class),
                row.getObject("workspace_id", UUID.class),
                HubItemType.valueOf(row.getString("item_type")),
                row.getObject("source_id", UUID.class),
                row.getString("title"),
                HubItemStatus.valueOf(row.getString("status")),
                row.getTimestamp("arrived_at").toInstant());
    }
}
