package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.response.DashboardResponse.*;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;
import vn.edu.medmaintenance.security.jwt.JwtService;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class DashboardIntegrationTest {
    @Autowired DashboardService dashboards;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    long vtyt,provider,own,foreign,plan,equipment,item;
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    MaintenanceQuarter quarter=MaintenanceQuarter.values()[(today.getMonthValue()-1)/3];
    @BeforeEach void setup() {
        vtyt=count("SELECT id FROM user_account WHERE role_code='PHONG_VTYT' AND active ORDER BY id LIMIT 1");
        provider=count("SELECT id FROM service_provider WHERE active ORDER BY id LIMIT 1");
        var deps=jdbc.queryForList("SELECT DISTINCT department_id FROM user_account WHERE role_code='KHOA_PHONG' AND active ORDER BY department_id LIMIT 2",Long.class);
        own=deps.get(0);foreign=deps.get(1);
        plan=newPlan("DASH-current-quarter","DRAFT");
        equipment=newEquipment(own,"DASH-OWN");item=newItem(equipment,own,"PLANNED");
        jdbc.update("INSERT INTO equipment_maintenance_schedule(equipment_id,quarter) VALUES (?,?)",equipment,quarter.name());
        role(UserRole.PHONG_VTYT,null);
    }
    long newPlan(String name,String status) {return jdbc.queryForObject("INSERT INTO maintenance_plan(title,period_start,period_end,status,created_by_user_id) VALUES (?,?,?,?,?) RETURNING id",Long.class,name,quarter.start(today.getYear()),quarter.end(today.getYear()),status,vtyt);}
    long newEquipment(long dep,String code) {return jdbc.queryForObject("INSERT INTO equipment(equipment_code,name,department_id) VALUES (?, ?,?) RETURNING id",Long.class,code+UUID.randomUUID(),code,dep);}
    long newItem(long eq,long dep,String status) {return jdbc.queryForObject("INSERT INTO maintenance_plan_item(plan_id,equipment_id,department_id_at_plan,status) VALUES (?,?,?,?) RETURNING id",Long.class,plan,eq,dep,status);}
    long execution(long target) {return jdbc.queryForObject("INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at,ended_at) VALUES (?,?,?,1,now()-interval '1 hour',now()) RETURNING id",Long.class,target,provider,vtyt);}
    long count(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}
    String role(UserRole role,Long dep){long id=count("SELECT id FROM user_account WHERE role_code=? AND active ORDER BY id LIMIT 1",role.name());var u=new AuthenticatedUser(id,"dashboard-test",role,dep);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role.name()))));return jwt.issue(u);}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void vtytSummaryUsesQuarterScheduleAndOpenExecutionStates(){var d=(Vtyt)dashboards.get();assertThat(d.summary().scheduled()).isEqualTo(count("SELECT count(*) FROM equipment_maintenance_schedule s JOIN equipment e ON e.id=s.equipment_id WHERE s.quarter=? AND e.active",quarter.name()));assertThat(d.summary().awaitingReport()).isEqualTo(count("SELECT count(*) FROM maintenance_plan WHERE status='AWAITING_REPORT'"));assertThat(d.quickActions()).hasSize(5);}
    @Test void attentionReflectsCurrentRevisionRegardlessOfNotificationRead(){jdbc.update("UPDATE maintenance_plan SET status='REVISION_REQUIRED' WHERE id=?",plan);var d=(Vtyt)dashboards.get();assertThat(d.attention()).anySatisfy(a->{assertThat(a.title()).isEqualTo("DASH-current-quarter");assertThat(a.href()).isEqualTo("/plans/"+plan+"/edit");assertThat(a.cta()).isEqualTo("Xem ý kiến BGĐ");});assertThat(d.attention()).hasSizeLessThanOrEqualTo(5);}
    @Test void currentQuarterShowsExistingPlanAndCountsWithoutCreatingRows(){long before=count("SELECT count(*) FROM maintenance_plan");var q=((Vtyt)dashboards.get()).currentQuarter();assertThat(q.year()).isEqualTo(today.getYear());assertThat(q.quarter()).isEqualTo(quarter.name());assertThat(q.plan().id()).isEqualTo(plan);assertThat(q.plan().equipmentCount()).isEqualTo(1);assertThat(count("SELECT count(*) FROM maintenance_plan")).isEqualTo(before);}
    @Test void bgdPendingApprovalsAndRecentDecisionsHaveDirectReviewLinks(){jdbc.update("UPDATE maintenance_plan SET status='SUBMITTED' WHERE id=?",plan);long request=jdbc.queryForObject("INSERT INTO approval_request(plan_id,request_type,status,created_by_user_id,submitted_at) VALUES (?,'PLAN_APPROVAL','PENDING',?,now()) RETURNING id",Long.class,plan,vtyt);String token=role(UserRole.BAN_GIAM_DOC,null);var d=(Bgd)dashboards.get();assertThat(d.summary().pendingPlans()).isEqualTo(count("SELECT count(*) FROM approval_request WHERE status='PENDING' AND request_type='PLAN_APPROVAL'"));assertThat(d.pending()).extracting(ApprovalRow::id).contains(request);long actor=count("SELECT id FROM user_account WHERE role_code='BAN_GIAM_DOC' AND active ORDER BY id LIMIT 1");jdbc.update("INSERT INTO approval_action(request_id,actor_user_id,outcome,action_at) VALUES (?,?,'APPROVE',now())",request,actor);jdbc.update("UPDATE approval_request SET status='DECIDED',resolved_at=now() WHERE id=?",request);d=(Bgd)dashboards.get();assertThat(d.decisions()).extracting(DecisionRow::requestId).contains(request);assertThat(d.summary().approvedThisMonth()).isPositive();assertThat(token).isNotBlank();}
    @Test void boardReportOverviewRequiresFinalizationAndDelivery(){jdbc.update("UPDATE maintenance_plan SET status='REPORTED' WHERE id=?",plan);long report=jdbc.queryForObject("INSERT INTO maintenance_report(plan_id,created_by_user_id,report_date,status,work_done,finalized_at) VALUES (?,?,?,'FINAL','Đã bảo trì',now()) RETURNING id",Long.class,plan,vtyt,today);role(UserRole.BAN_GIAM_DOC,null);assertThat(((Bgd)dashboards.get()).reports()).extracting(ReportRow::planId).doesNotContain(plan);jdbc.update("INSERT INTO maintenance_report_delivery(report_id,sent_by_user_id,sent_at) VALUES (?,?,now())",report,vtyt);assertThat(((Bgd)dashboards.get()).reports()).extracting(ReportRow::planId).contains(plan);}
    @Test void departmentShowsOwnHandoverAndRecentHistoryOnly(){jdbc.update("UPDATE maintenance_plan SET status='IN_PROGRESS' WHERE id=?",plan);jdbc.update("UPDATE maintenance_plan_item SET status='AWAITING_HANDOVER' WHERE id=?",item);execution(item);long otherEq=newEquipment(foreign,"DASH-FOREIGN");long otherItem=newItem(otherEq,foreign,"AWAITING_HANDOVER");execution(otherItem);role(UserRole.KHOA_PHONG,own);var d=(Khoa)dashboards.get();assertThat(d.departmentId()).isEqualTo(own);assertThat(d.summary().equipment()).isEqualTo(count("SELECT count(*) FROM equipment WHERE department_id=?",own));assertThat(d.handover()).extracting(ItemRow::equipmentId).contains(equipment).doesNotContain(otherEq);assertThat(d.active()).extracting(ItemRow::itemId).doesNotContain(otherItem);jdbc.update("UPDATE maintenance_plan_item SET status='COMPLETED' WHERE id IN (?,?)",item,otherItem);d=(Khoa)dashboards.get();assertThat(d.history()).extracting(ItemRow::equipmentId).contains(equipment).doesNotContain(otherEq);assertThat(d.summary().completedRecently()).isPositive();}
    @Test void invalidDepartmentCannotFallBackToHospitalTotals(){role(UserRole.KHOA_PHONG,null);assertThatThrownBy(()->dashboards.get()).isInstanceOf(BusinessRuleException.class).hasMessageContaining("khoa/phòng");}
    @Test void adminIncludesAccountsAndActionableCatalogQuality(){role(UserRole.ADMIN,null);var d=(Admin)dashboards.get();assertThat(d.summary().activeAccounts()).isEqualTo(count("SELECT count(*) FROM user_account WHERE active"));assertThat(d.summary().contracts()).isEqualTo(count("SELECT count(*) FROM maintenance_contract"));assertThat(d.accountsByRole().stream().mapToLong(RoleCount::count).sum()).isEqualTo(count("SELECT count(*) FROM user_account"));assertThat(d.quality()).anySatisfy(q->{assertThat(q.label()).isEqualTo("Thiết bị chưa liên kết hợp đồng");assertThat(q.count()).isPositive();assertThat(q.href()).isEqualTo("/admin/equipment");});}
    @Test void authenticatedRoleCannotBeOverriddenByRequestParameters() throws Exception {for(var role:UserRole.values()){String token=role(role,role==UserRole.KHOA_PHONG?own:null);mvc.perform(get("/api/dashboard?role=ADMIN&departmentId="+foreign).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role.name())).andExpect(jsonPath("$.summary.passwordHash").doesNotExist());}mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());}
}
