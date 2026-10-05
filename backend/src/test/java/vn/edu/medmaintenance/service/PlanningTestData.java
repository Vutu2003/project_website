package vn.edu.medmaintenance.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

final class PlanningTestData {
    static Map<Long,String> archiveFixturePlans(JdbcTemplate jdbc){
        var statuses=new HashMap<Long,String>();
        jdbc.query("SELECT id,status FROM maintenance_plan WHERE id<=8",(org.springframework.jdbc.core.RowCallbackHandler) rs->statuses.put(rs.getLong(1),rs.getString(2)));
        jdbc.update("UPDATE maintenance_plan SET status='CLOSED' WHERE id<=8");return statuses;
    }
    static void restoreFixturePlans(JdbcTemplate jdbc,Map<Long,String> statuses){statuses.forEach((id,status)->jdbc.update("UPDATE maintenance_plan SET status=? WHERE id=?",status,id));}

    static Map<String, Object> complete(JdbcTemplate jdbc, long equipmentId) {
        var row=new HashMap<String, Object>();
        row.put("equipmentId", equipmentId);
        var free=jdbc.queryForList("SELECT id FROM maintenance_coverage WHERE equipment_id=? AND classification='FREE' AND verified_by_user_id IS NOT NULL ORDER BY id LIMIT 1", Long.class, equipmentId);
        if (!free.isEmpty()) {
            row.put("classification", "FREE");
            row.put("coverageId", free.get(0));
        }
        else {
            row.put("classification", "NOT_FREE");
            row.put("proposedProviderId", jdbc.queryForObject("SELECT id FROM service_provider WHERE active=true ORDER BY id LIMIT 1", Long.class));
            row.put("rationale", "Năng lực phù hợp với thiết bị thử nghiệm");
        }
        return row;
    }
    static void cleanNotifications(JdbcTemplate jdbc, long plan) {
        jdbc.update("DELETE FROM user_notification WHERE target_url=? OR target_url LIKE ? OR target_url IN (SELECT '/approvals/'||id FROM approval_request WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?))", "/plans/"+plan, "/plans/"+plan+"/%", plan, plan);
    }
}
