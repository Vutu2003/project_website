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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.api.dto.request.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class MaintenanceAutomationIntegrationTest {
 @Autowired MaintenanceAutomationService automation; @Autowired JdbcTemplate jdbc; @Autowired QuarterlyPlanningService quarterly; @Autowired PlanningService planning;
 @Autowired PlanApprovalService approval; @Autowired MockMvc mvc; @Autowired vn.edu.medmaintenance.security.jwt.JwtService jwt;
 long q1,q2,provider,department,vtyt,bgd,contract; String token;
 @BeforeEach void setup(){
  department=jdbc.queryForObject("SELECT id FROM department ORDER BY id LIMIT 1",Long.class);
  vtyt=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='PHONG_VTYT' ORDER BY id LIMIT 1",Long.class);
  bgd=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='BAN_GIAM_DOC' ORDER BY id LIMIT 1",Long.class);
  provider=jdbc.queryForObject("SELECT id FROM service_provider WHERE active ORDER BY id LIMIT 1",Long.class);
  q1=device("Q1");q2=device("Q2");
  contract=contract(LocalDate.of(2027,1,1),LocalDate.of(2027,12,31));
  role(vtyt,UserRole.PHONG_VTYT);
 }
 long device(String quarter){long id=jdbc.queryForObject("INSERT INTO equipment(department_id,equipment_code,name) VALUES (?,?,'Máy kiểm tra lịch quý') RETURNING id",Long.class,department,"QUARTER-"+quarter+"-"+UUID.randomUUID());jdbc.update("INSERT INTO equipment_maintenance_schedule(equipment_id,quarter) VALUES (?,?)",id,quarter);return id;}
 long contract(LocalDate from,LocalDate to){long id=jdbc.queryForObject("INSERT INTO maintenance_contract(contract_code,contract_name,provider_id,start_date,end_date) VALUES (?,'Bảo trì theo quý',?,?,?) RETURNING id",Long.class,"HD-QUARTER-"+UUID.randomUUID(),provider,from,to);jdbc.update("INSERT INTO maintenance_contract_equipment(contract_id,equipment_id) VALUES (?,?)",id,q1);return id;}
 void role(long id,UserRole role){var principal=new AuthenticatedUser(id,"test",role,department);token=jwt.issue(principal);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role.name()))));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @SuppressWarnings("unchecked") List<Map<String,Object>> rows(MaintenanceQuarter quarter){return (List<Map<String,Object>>)quarterly.preview(2027,quarter).get("equipment");}
 Map<String,Object> row(){return rows(MaintenanceQuarter.Q1).stream().filter(r->((Number)r.get("equipment_id")).longValue()==q1).findFirst().orElseThrow();}
 QuarterlyPlanRequest input(){return new QuarterlyPlanRequest(2027,MaintenanceQuarter.Q1,List.of());}
 @Test void fixedQuarterLookupSupportsMultipleMemberships(){jdbc.update("INSERT INTO equipment_maintenance_schedule(equipment_id,quarter) VALUES (?,'Q3')",q1);assertThat(jdbc.queryForList("SELECT quarter FROM equipment_maintenance_schedule WHERE equipment_id=? ORDER BY quarter",String.class,q1)).containsExactly("Q1","Q3");}
 @Test void q1PreviewContainsItsDevicesWithoutCreatingPlans(){long before=jdbc.queryForObject("SELECT count(*) FROM maintenance_plan",Long.class);assertThat(rows(MaintenanceQuarter.Q1)).anyMatch(r->((Number)r.get("equipment_id")).longValue()==q1).noneMatch(r->((Number)r.get("equipment_id")).longValue()==q2);assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan",Long.class)).isEqualTo(before);}
 @Test void q2ExcludesQ1OnlyEquipment(){assertThat(rows(MaintenanceQuarter.Q2)).anyMatch(r->((Number)r.get("equipment_id")).longValue()==q2).noneMatch(r->((Number)r.get("equipment_id")).longValue()==q1);}
 @Test void validContractIsAutomaticAtInclusiveQuarterStart(){assertThat(row()).containsEntry("classification","FREE").containsEntry("contract_status","VALID").containsEntry("contract_id",contract);}
 @Test void expiredAndMissingContractsAreOutsideContract(){jdbc.update("UPDATE maintenance_contract SET start_date='2026-01-01',end_date='2026-12-31' WHERE id=?",contract);assertThat(row()).containsEntry("classification","NOT_FREE").containsEntry("contract_status","EXPIRED");assertThat(rows(MaintenanceQuarter.Q2).get(0).get("classification")).isEqualTo("NOT_FREE");}
 @Test void validContractDerivesProviderWithoutCoverage(){assertThat(automation.provider(provider).get("contracts")).isInstanceOf(List.class);assertThat(automation.contractEquipment(contract)).hasSize(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_coverage WHERE equipment_id=?",Long.class,q1)).isZero();assertThat(row()).containsEntry("provider_id",provider);var p=quarterly.create(input());assertThat(jdbc.queryForObject("SELECT assigned_provider_id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?",Long.class,p.id(),q1)).isEqualTo(provider);}
 @Test void quarterCreationGeneratesTitlePeriodAndAllItems(){var p=quarterly.create(input());var r=jdbc.queryForMap("SELECT title,period_start,period_end,plan_year,plan_quarter FROM maintenance_plan WHERE id=?",p.id());assertThat(r.get("title")).isEqualTo("Kế hoạch bảo trì Quý I năm 2027");assertThat(r.get("period_start").toString()).isEqualTo("2027-01-01");assertThat(r.get("period_end").toString()).isEqualTo("2027-03-31");assertThat(r).containsEntry("plan_year",2027).containsEntry("plan_quarter","Q1");assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=?",Integer.class,p.id())).isEqualTo(rows(MaintenanceQuarter.Q1).size());assertThat(MaintenanceQuarter.Q2.end(2027)).isEqualTo(LocalDate.of(2027,6,30));assertThat(MaintenanceQuarter.Q3.end(2027)).isEqualTo(LocalDate.of(2027,9,30));assertThat(MaintenanceQuarter.Q4.end(2027)).isEqualTo(LocalDate.of(2027,12,31));}
 @Test void duplicateActiveQuarterPlanIsBlocked(){quarterly.create(input());assertThatThrownBy(()->quarterly.create(input())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("đã tồn tại");}
 @Test void approvalStillWorks(){var p=quarterly.create(input());var submitted=planning.submit(p.id(),new SubmitPlanRequest(p.version()));role(bgd,UserRole.BAN_GIAM_DOC);assertThat(approval.decide(submitted.approvalRequestId(),new DecidePlanRequest(submitted.version(),ApprovalOutcome.APPROVE,null)).planStatus()).isEqualTo(PlanStatus.APPROVED);}
 @Test void revisionCommentRemainsVisible() throws Exception {var p=quarterly.create(input());var submitted=planning.submit(p.id(),new SubmitPlanRequest(p.version()));role(bgd,UserRole.BAN_GIAM_DOC);approval.decide(submitted.approvalRequestId(),new DecidePlanRequest(submitted.version(),ApprovalOutcome.REVISION_REQUIRED,"Bổ sung căn cứ chọn đơn vị."));role(vtyt,UserRole.PHONG_VTYT);mvc.perform(get("/api/plans/"+p.id()+"/review-comments").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].comment").value("Bổ sung căn cứ chọn đơn vị."));}
 @Test void conflictingContractsRemainBlocked(){contract(LocalDate.of(2026,1,1),LocalDate.of(2028,1,1));assertThat(row()).containsEntry("conflict",true);assertThatThrownBy(()->quarterly.create(input())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("nhiều hợp đồng");}
}
