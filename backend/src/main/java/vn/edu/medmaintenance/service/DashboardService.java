package vn.edu.medmaintenance.service;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.response.DashboardResponse;
import vn.edu.medmaintenance.api.dto.response.DashboardResponse.*;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String ACTIVE_PLANS = "('APPROVED','IN_PROGRESS','AWAITING_REPORT')";
    private static final String ACTIVE_ITEMS = "('IN_MAINTENANCE','AWAITING_TECHNICAL_ACCEPTANCE','AWAITING_HANDOVER','REWORK_REQUIRED')";
    // Latest execution and event per item: counts do not multiply by historical attempts.
    private static final String ITEMS = """
        WITH tracked AS (
          SELECT i.*,p.status plan_status,e.equipment_code,e.name equipment_name,s.name provider,
            x.id execution_id,x.started_at,x.ended_at,l.event_at,l.work_note,l.damage_note,u.display_name actor,
            a.observed_at technical_at,
            CASE WHEN i.status='COMPLETED' THEN 'WORK_DONE' WHEN i.status='REPAIR_REQUIRED' THEN 'DAMAGE_DETECTED'
              WHEN x.id IS NULL OR i.status='REWORK_REQUIRED' THEN 'NOT_STARTED'
              WHEN i.status IN ('AWAITING_TECHNICAL_ACCEPTANCE','AWAITING_HANDOVER') AND x.ended_at IS NOT NULL THEN 'WORK_DONE'
              WHEN split_part(coalesce(l.work_note,''),E'\\n',1) IN ('Bảo trì xong','Đã xử lý xong') THEN 'WORK_DONE'
              WHEN split_part(coalesce(l.work_note,''),E'\\n',1)='Có hỏng hóc' THEN 'DAMAGE_DETECTED'
              ELSE 'IN_PROGRESS' END stage
          FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id JOIN equipment e ON e.id=i.equipment_id
          LEFT JOIN service_provider s ON s.id=i.assigned_provider_id
          LEFT JOIN LATERAL (SELECT v.* FROM maintenance_execution v WHERE v.plan_item_id=i.id ORDER BY v.attempt_no DESC LIMIT 1) x ON true
          LEFT JOIN LATERAL (SELECT v.* FROM maintenance_progress_log v WHERE v.execution_id=x.id ORDER BY v.event_at DESC,v.id DESC LIMIT 1) l ON true
          LEFT JOIN user_account u ON u.id=l.recorded_by_user_id
          LEFT JOIN acceptance_record a ON a.execution_id=x.id AND a.acceptance_type='TECHNICAL_ACCEPTANCE'
          WHERE %s
        )
        """;

    public DashboardService(JdbcTemplate jdbc, CurrentUser current) { this.jdbc = jdbc; this.current = current; }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DashboardResponse get() {
        LocalDate today = LocalDate.now(ZONE);
        Instant generated = Instant.now();
        // No role/department supplied by the caller is used to select or widen this response.
        return switch (current.get().role()) {
            case PHONG_VTYT -> vtyt(today, generated);
            case BAN_GIAM_DOC -> bgd(today, generated);
            case KHOA_PHONG -> khoa(today, generated);
            case ADMIN -> admin(today, generated);
        };
    }

    private Vtyt vtyt(LocalDate today, Instant generated) {
        int year = today.getYear();
        MaintenanceQuarter quarter = MaintenanceQuarter.values()[(today.getMonthValue()-1)/3];
        LocalDate reference = quarter.start(year);
        var schedule = jdbc.queryForObject("""
            SELECT count(*) total,count(*) FILTER (WHERE EXISTS (
              SELECT 1 FROM maintenance_contract_equipment m JOIN maintenance_contract c ON c.id=m.contract_id
              JOIN service_provider s ON s.id=c.provider_id
              WHERE m.equipment_id=e.id AND c.active AND s.active AND ? BETWEEN c.start_date AND c.end_date)) covered
            FROM equipment e JOIN equipment_maintenance_schedule q ON q.equipment_id=e.id
            WHERE e.active AND q.quarter=?
            """, (r,n)->new long[]{r.getLong("total"),r.getLong("covered")},reference,quarter.name());
        var contractSummary = jdbc.queryForObject("""
            SELECT count(*) FILTER (WHERE c.active AND s.active AND ? BETWEEN c.start_date AND c.end_date) valid,
              count(*) FILTER (WHERE c.active AND s.active AND ? BETWEEN c.start_date AND c.end_date AND c.end_date<=?) expiring,
              count(*) FILTER (WHERE c.end_date<?) expired
            FROM maintenance_contract c JOIN service_provider s ON s.id=c.provider_id
            """,(r,n)->new ContractSummary(r.getLong("valid"),r.getLong("expiring"),r.getLong("expired")),today,today,today.plusDays(30),today);
        var progress = jdbc.queryForObject(ITEMS.formatted("true")+"""
            SELECT count(*) FILTER (WHERE stage='NOT_STARTED') pending,
              count(*) FILTER (WHERE stage='IN_PROGRESS') ongoing,
              count(*) FILTER (WHERE stage='WORK_DONE') done,
              count(*) FILTER (WHERE stage='DAMAGE_DETECTED') damaged,
              count(*) FILTER (WHERE status='AWAITING_TECHNICAL_ACCEPTANCE') technical,
              count(*) FILTER (WHERE status='AWAITING_HANDOVER') handover
            FROM tracked WHERE plan_status IN
            """+ACTIVE_PLANS,(r,n)->new Progress(r.getLong("pending"),r.getLong("ongoing"),r.getLong("done"),r.getLong("damaged"),r.getLong("technical"),r.getLong("handover")));
        long maintaining = count("SELECT count(DISTINCT i.equipment_id) FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id WHERE i.status='IN_MAINTENANCE' AND p.status IN "+ACTIVE_PLANS);
        long awaitingReport = count("SELECT count(*) FROM maintenance_plan WHERE status='AWAITING_REPORT'");
        var quarterPlans = jdbc.query("""
            SELECT p.id,p.title,p.status,coalesce(p.plan_year,extract(year FROM p.period_start)::integer) AS plan_year_value,
              coalesce(p.plan_quarter,'Q'||extract(quarter FROM p.period_start)::integer) AS plan_quarter_value,
              count(i.id) equipment_count,count(i.id) FILTER (WHERE i.assignment_route='UNDER_CONTRACT') covered,
              count(i.id) FILTER (WHERE i.status='IN_MAINTENANCE') ongoing,
              count(i.id) FILTER (WHERE i.status='COMPLETED') completed
            FROM maintenance_plan p LEFT JOIN maintenance_plan_item i ON i.plan_id=p.id
            WHERE (p.plan_year=? AND p.plan_quarter=?) OR (p.plan_year IS NULL AND p.period_start=? AND p.period_end=?)
            GROUP BY p.id ORDER BY (p.status NOT IN ('CLOSED','REPORTED')) DESC,p.created_at DESC,p.id DESC LIMIT 1
            """,(r,n)->new Quarter(year,quarter.name(),schedule[0],r.getLong("covered"),r.getLong("equipment_count")-r.getLong("covered"),r.getLong("ongoing"),r.getLong("completed"),new PlanRow(r.getLong("id"),r.getString("title"),r.getString("status"),r.getInt("plan_year_value"),r.getString("plan_quarter_value"),r.getLong("equipment_count"))),year,quarter.name(),reference,quarter.end(year));
        Quarter currentQuarter = quarterPlans.isEmpty()?new Quarter(year,quarter.name(),schedule[0],schedule[1],schedule[0]-schedule[1],0,0,null):quarterPlans.get(0);
        var attention = jdbc.query("""
            WITH work AS (
              SELECT p.title,'Xem ý kiến BGĐ và cập nhật kế hoạch' detail,p.status,coalesce(h.at,p.created_at) at,
                '/plans/'||p.id||'/edit' href,'Xem ý kiến BGĐ' cta,0 priority
              FROM maintenance_plan p LEFT JOIN LATERAL (SELECT max(action_timestamp) at FROM status_history WHERE plan_id=p.id) h ON true WHERE p.status='REVISION_REQUIRED'
              UNION ALL SELECT p.title,'Bản nháp chưa gửi phê duyệt',p.status,p.created_at,'/plans/'||p.id,'Kiểm tra & gửi duyệt',3 FROM maintenance_plan p WHERE p.status='DRAFT'
              UNION ALL SELECT p.title,'Thiết bị ngoài hợp đồng cần đơn vị đề xuất','PENDING_PROPOSAL',p.created_at,'/plans/'||p.id||'/edit','Bổ sung đơn vị',2 FROM maintenance_plan p
                WHERE p.status IN ('DRAFT','REVISION_REQUIRED') AND EXISTS (SELECT 1 FROM maintenance_plan_item i WHERE i.plan_id=p.id AND i.assignment_route IS DISTINCT FROM 'UNDER_CONTRACT'
                  AND NOT EXISTS (SELECT 1 FROM approval_request r WHERE r.plan_item_id=i.id AND r.request_type='VENDOR_SELECTION' AND r.proposed_provider_id IS NOT NULL AND nullif(btrim(r.rationale),'') IS NOT NULL AND r.status<>'CANCELLED'))
              UNION ALL SELECT e.equipment_code||' · '||e.name,p.title,i.status,x.ended_at,'/plans/'||p.id||'/items/'||i.id||'/execution','Nghiệm thu kỹ thuật',1
                FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id JOIN equipment e ON e.id=i.equipment_id
                LEFT JOIN LATERAL (SELECT ended_at FROM maintenance_execution WHERE plan_item_id=i.id ORDER BY attempt_no DESC LIMIT 1) x ON true
                WHERE i.status='AWAITING_TECHNICAL_ACCEPTANCE' AND p.status IN ('APPROVED','IN_PROGRESS','AWAITING_REPORT')
              UNION ALL SELECT p.title,'Thiết bị đang bảo trì cần cập nhật kết quả',p.status,p.created_at,'/maintenance-progress/plans/'||p.id,'Cập nhật tiến độ',4
                FROM maintenance_plan p WHERE p.status='IN_PROGRESS' AND EXISTS(SELECT 1 FROM maintenance_plan_item i WHERE i.plan_id=p.id AND i.status='IN_MAINTENANCE')
              UNION ALL SELECT p.title,'Hoàn tất và gửi báo cáo bảo trì',p.status,p.created_at,'/plans/'||p.id||'/report','Mở báo cáo',2 FROM maintenance_plan p WHERE p.status='AWAITING_REPORT'
            ), unique_work AS (SELECT DISTINCT ON (href) * FROM work ORDER BY href,priority)
            SELECT * FROM unique_work ORDER BY priority,at DESC NULLS LAST,href LIMIT 5
            """,(r,n)->new Attention(r.getString("title"),r.getString("detail"),r.getString("status"),instant(r,"at"),r.getString("href"),r.getString("cta")));
        var expiring = jdbc.query("""
            SELECT c.id,c.contract_code,s.name provider,c.end_date,(SELECT count(*) FROM maintenance_contract_equipment m WHERE m.contract_id=c.id) equipment_count
            FROM maintenance_contract c JOIN service_provider s ON s.id=c.provider_id
            WHERE c.active AND s.active AND ? BETWEEN c.start_date AND c.end_date AND c.end_date<=? ORDER BY c.end_date,c.id LIMIT 5
            """,(r,n)->new ContractRow(r.getLong("id"),r.getString("contract_code"),r.getString("provider"),r.getObject("end_date",LocalDate.class),r.getLong("equipment_count")),today,today.plusDays(30));
        var recent = jdbc.query("""
            SELECT i.id,i.plan_id,i.equipment_id,e.equipment_code,e.name equipment_name,s.name provider,l.event_at at,l.work_note note,u.display_name actor,
              CASE split_part(l.work_note,E'\\n',1) WHEN 'Bảo trì xong' THEN 'WORK_DONE' WHEN 'Đã xử lý xong' THEN 'WORK_DONE' WHEN 'Có hỏng hóc' THEN 'DAMAGE_DETECTED' ELSE 'IN_PROGRESS' END status
            FROM maintenance_progress_log l JOIN maintenance_execution x ON x.id=l.execution_id JOIN maintenance_plan_item i ON i.id=x.plan_item_id
            JOIN equipment e ON e.id=i.equipment_id JOIN service_provider s ON s.id=x.provider_id JOIN user_account u ON u.id=l.recorded_by_user_id
            ORDER BY l.event_at DESC,l.id DESC LIMIT 5
            """,this::item);
        return new Vtyt(UserRole.PHONG_VTYT,generated,new VtytSummary(schedule[0],contractSummary.valid(),maintaining,progress.technical(),awaitingReport),attention,currentQuarter,progress,contractSummary,expiring,recent,reports(false),List.of(
            new Action("Tạo kế hoạch bảo trì",quarterLink(year,quarter.name())),new Action("Danh sách thiết bị","/equipment"),new Action("Danh sách hợp đồng","/contracts"),new Action("Cập nhật tiến độ","/maintenance-progress?status=IN_PROGRESS"),new Action("Lập báo cáo","/reports")));
    }

    private Bgd bgd(LocalDate today, Instant generated) {
        LocalDate month = today.withDayOfMonth(1);
        var summary = jdbc.queryForObject("""
            SELECT (SELECT count(*) FROM approval_request WHERE status='PENDING' AND request_type='PLAN_APPROVAL') plans,
              (SELECT count(*) FROM approval_request WHERE status='PENDING' AND request_type='VENDOR_SELECTION') providers,
              (SELECT count(*) FROM approval_action WHERE outcome='APPROVE' AND action_at>=? AND action_at<?) approved,
              (SELECT count(*) FROM approval_action WHERE outcome='REVISION_REQUIRED' AND action_at>=? AND action_at<?) revised,
              (SELECT count(*) FROM maintenance_report_delivery WHERE department_id IS NULL AND sent_at>=? AND sent_at<?) reports
            """,(r,n)->new BgdSummary(r.getLong("plans"),r.getLong("providers"),r.getLong("approved"),r.getLong("revised"),r.getLong("reports")),month.atStartOfDay(ZONE).toOffsetDateTime(),month.plusMonths(1).atStartOfDay(ZONE).toOffsetDateTime(),month.atStartOfDay(ZONE).toOffsetDateTime(),month.plusMonths(1).atStartOfDay(ZONE).toOffsetDateTime(),month.atStartOfDay(ZONE).toOffsetDateTime(),month.plusMonths(1).atStartOfDay(ZONE).toOffsetDateTime());
        var pending = jdbc.query("""
            SELECT r.id,r.request_type,p.title,e.equipment_code,u.display_name sender,r.submitted_at at
            FROM approval_request r LEFT JOIN maintenance_plan_item i ON i.id=r.plan_item_id JOIN maintenance_plan p ON p.id=coalesce(r.plan_id,i.plan_id)
            LEFT JOIN equipment e ON e.id=i.equipment_id JOIN user_account u ON u.id=r.created_by_user_id
            WHERE r.status='PENDING' ORDER BY r.submitted_at DESC,r.id DESC LIMIT 5
            """,(r,n)->new ApprovalRow(r.getLong("id"),r.getString("request_type"),r.getString("title"),r.getString("equipment_code"),r.getString("sender"),instant(r,"at")));
        var decisions = jdbc.query("""
            SELECT a.request_id,p.title,e.equipment_code,a.outcome,a.comment,u.display_name actor,a.action_at at
            FROM approval_action a JOIN approval_request r ON r.id=a.request_id LEFT JOIN maintenance_plan_item i ON i.id=r.plan_item_id
            JOIN maintenance_plan p ON p.id=coalesce(r.plan_id,i.plan_id) LEFT JOIN equipment e ON e.id=i.equipment_id JOIN user_account u ON u.id=a.actor_user_id
            ORDER BY a.action_at DESC,a.id DESC LIMIT 5
            """,(r,n)->new DecisionRow(r.getLong("request_id"),r.getString("title"),r.getString("equipment_code"),r.getString("outcome"),r.getString("comment"),r.getString("actor"),instant(r,"at")));
        return new Bgd(UserRole.BAN_GIAM_DOC,generated,summary,pending,decisions,reports(true),List.of(new Action("Phê duyệt","/approvals"),new Action("Xem báo cáo","/reports")));
    }

    private List<ReportRow> reports(boolean board) {
        // Board overview follows explicit delivery, matching its existing received-reports inbox.
        return jdbc.query("""
            SELECT p.id,p.title,coalesce(p.plan_year,extract(year FROM p.period_start)::integer) AS plan_year_value,
              coalesce(p.plan_quarter,'Q'||extract(quarter FROM p.period_start)::integer) AS plan_quarter_value,r.report_date,r.status,
              coalesce(v.sent_at,r.finalized_at) at,
              (SELECT count(*) FROM maintenance_plan_item i WHERE i.plan_id=p.id AND i.status='COMPLETED') completed,
              (SELECT count(*) FROM maintenance_plan_item i WHERE i.plan_id=p.id AND i.status='REPAIR_REQUIRED') repair
            FROM maintenance_report r JOIN maintenance_plan p ON p.id=r.plan_id
            LEFT JOIN maintenance_report_delivery v ON v.report_id=r.id AND v.department_id IS NULL
            WHERE
            """+(board?"r.status='FINAL' AND v.id IS NOT NULL":"true")+" ORDER BY coalesce(v.sent_at,r.finalized_at,p.created_at) DESC,r.id DESC LIMIT 5",
            (r,n)->new ReportRow(r.getLong("id"),r.getString("title"),r.getInt("plan_year_value"),r.getString("plan_quarter_value"),r.getObject("report_date",LocalDate.class),instant(r,"at"),r.getLong("completed"),r.getLong("repair"),r.getString("status")));
    }

    private Khoa khoa(LocalDate today, Instant generated) {
        Long department = current.get().departmentId();
        if (department == null) denyScope();
        var names = jdbc.queryForList("SELECT name FROM department WHERE id=? AND active",String.class,department);
        if (names.isEmpty()) denyScope();
        long equipment = count("SELECT count(*) FROM equipment WHERE department_id=?",department);
        var summary = jdbc.queryForObject(ITEMS.formatted("i.department_id_at_plan=?")+"""
            SELECT count(DISTINCT equipment_id) FILTER (WHERE status='IN_MAINTENANCE' AND plan_status IN ('APPROVED','IN_PROGRESS','AWAITING_REPORT')) maintaining,
              count(*) FILTER (WHERE status='AWAITING_HANDOVER' AND plan_status IN ('APPROVED','IN_PROGRESS','AWAITING_REPORT')) handover,
              count(*) FILTER (WHERE status='COMPLETED' AND ended_at>=?) completed FROM tracked
            """,(r,n)->new KhoaSummary(equipment,r.getLong("maintaining"),r.getLong("handover"),r.getLong("completed")),department,today.minusDays(30).atStartOfDay(ZONE).toOffsetDateTime());
        String projection = " SELECT id,plan_id,equipment_id,equipment_code,equipment_name,provider,status,coalesce(technical_at,event_at,ended_at,started_at) at,work_note note,actor FROM tracked WHERE ";
        var handover = jdbc.query(ITEMS.formatted("i.department_id_at_plan=?")+projection+"status='AWAITING_HANDOVER' AND plan_status IN "+ACTIVE_PLANS+" ORDER BY technical_at DESC NULLS LAST,id DESC LIMIT 5",this::item,department);
        var active = jdbc.query(ITEMS.formatted("i.department_id_at_plan=?")+projection+"status IN "+ACTIVE_ITEMS+" AND plan_status IN "+ACTIVE_PLANS+" ORDER BY coalesce(event_at,ended_at,started_at) DESC NULLS LAST,id DESC LIMIT 5",this::item,department);
        var history = jdbc.query(ITEMS.formatted("i.department_id_at_plan=?")+" SELECT id,plan_id,equipment_id,equipment_code,equipment_name,provider,status,ended_at at,work_note note,actor FROM tracked WHERE status IN ('COMPLETED','REPAIR_REQUIRED') AND ended_at IS NOT NULL ORDER BY ended_at DESC,id DESC LIMIT 5",this::item,department);
        return new Khoa(UserRole.KHOA_PHONG,generated,department,names.get(0),summary,handover,active,history,List.of(new Action("Thiết bị chờ bàn giao","/execution"),new Action("Lịch sử bảo trì","/equipment"),new Action("Báo cáo đã nhận","/reports")));
    }

    private Admin admin(LocalDate today, Instant generated) {
        var summary = jdbc.queryForObject("""
            SELECT count(*) FILTER(WHERE active) active,count(*) FILTER(WHERE NOT active) inactive,
              (SELECT count(*) FROM department) departments,(SELECT count(*) FROM service_provider) providers,
              (SELECT count(*) FROM maintenance_contract) contracts FROM user_account
            """,(r,n)->new AdminSummary(r.getLong("active"),r.getLong("inactive"),r.getLong("departments"),r.getLong("providers"),r.getLong("contracts")));
        var roles = jdbc.query("SELECT role_code,count(*) total FROM user_account GROUP BY role_code ORDER BY role_code",(r,n)->new RoleCount(UserRole.valueOf(r.getString("role_code")),r.getLong("total")));
        var quality = jdbc.queryForObject("""
            SELECT (SELECT count(*) FROM equipment e WHERE e.active AND NOT EXISTS(SELECT 1 FROM equipment_maintenance_schedule q WHERE q.equipment_id=e.id)) missing_schedule,
              (SELECT count(*) FROM equipment e WHERE e.active AND NOT EXISTS(SELECT 1 FROM maintenance_contract_equipment m WHERE m.equipment_id=e.id)) missing_contract,
              (SELECT count(*) FROM maintenance_contract WHERE end_date<?) expired,
              (SELECT count(*) FROM service_provider s WHERE NOT s.active AND (EXISTS(SELECT 1 FROM maintenance_contract c WHERE c.provider_id=s.id AND c.active) OR EXISTS(SELECT 1 FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id WHERE i.assigned_provider_id=s.id AND p.status NOT IN ('REPORTED','CLOSED')))) inactive_provider,
              (SELECT count(*) FROM user_account u LEFT JOIN department d ON d.id=u.department_id WHERE u.role_code='KHOA_PHONG' AND (d.id IS NULL OR NOT d.active)) invalid_department
            """,(r,n)->List.of(new Quality("Thiết bị chưa có lịch quý",r.getLong("missing_schedule"),"/admin/equipment"),new Quality("Thiết bị chưa liên kết hợp đồng",r.getLong("missing_contract"),"/admin/equipment"),new Quality("Hợp đồng đã hết hạn",r.getLong("expired"),"/contracts"),new Quality("Đơn vị ngừng hoạt động còn được tham chiếu",r.getLong("inactive_provider"),"/admin/catalogs/providers"),new Quality("Tài khoản khoa có phạm vi không hợp lệ",r.getLong("invalid_department"),"/admin/accounts?role=KHOA_PHONG")),today);
        return new Admin(UserRole.ADMIN,generated,summary,roles,quality,List.of(new Action("Quản lý tài khoản","/admin/accounts"),new Action("Khoa / Phòng","/admin/catalogs/departments"),new Action("Đơn vị bảo trì","/admin/catalogs/providers"),new Action("Hợp đồng","/contracts")));
    }

    private ItemRow item(ResultSet r, int index) throws SQLException { return new ItemRow(r.getLong("id"),r.getLong("plan_id"),r.getLong("equipment_id"),r.getString("equipment_code"),r.getString("equipment_name"),r.getString("provider"),r.getString("status"),instant(r,"at"),r.getString("note"),r.getString("actor")); }
    private Instant instant(ResultSet r,String column) throws SQLException { Timestamp at=r.getTimestamp(column);return at==null?null:at.toInstant(); }
    private long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    private String quarterLink(int year,String quarter) { return "/plans/new?year="+year+"&quarter="+quarter; }
    private void denyScope() { throw new BusinessRuleException(HttpStatus.FORBIDDEN,"DEPARTMENT_SCOPE_VIOLATION","Tài khoản cần thuộc một khoa/phòng đang hoạt động."); }
}
