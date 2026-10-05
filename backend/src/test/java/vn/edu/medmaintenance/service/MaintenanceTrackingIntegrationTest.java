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
class MaintenanceTrackingIntegrationTest {
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Autowired EquipmentManagementService equipmentManagement;@Autowired PlanDeletionService deletion;@Autowired MaintenanceAutomationService automation;
 @Autowired JdbcTemplate jdbc;@Autowired MaintenanceTrackingService tracking;@Autowired ExecutionAcceptanceService workflow;
 @Autowired MaintenanceReportService reports;@Autowired ReportDeliveryService delivery;@Autowired QuarterlyPlanningService catalog;
 @Autowired MockMvc mvc;@Autowired vn.edu.medmaintenance.security.jwt.JwtService jwt;
 long plan,a,b,eqA,eqB,departmentA,departmentB,provider,vtyt;String token;
 @BeforeEach void setup(){
  vtyt=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='PHONG_VTYT' ORDER BY id LIMIT 1",Long.class);
  var departments=jdbc.queryForList("SELECT DISTINCT department_id FROM user_account WHERE active AND role_code='KHOA_PHONG' ORDER BY department_id LIMIT 2",Long.class);departmentA=departments.get(0);departmentB=departments.get(1);
  provider=jdbc.queryForObject("SELECT id FROM service_provider WHERE active ORDER BY id LIMIT 1",Long.class);
  plan=jdbc.queryForObject("INSERT INTO maintenance_plan(title,period_start,period_end,status,created_by_user_id) VALUES ('Bảo trì kiểm thử cập nhật','2026-01-01','2030-12-31','APPROVED',?) RETURNING id",Long.class,vtyt);
  eqA=equipment(departmentA);eqB=equipment(departmentB);a=item(eqA,departmentA);b=item(eqB,departmentB);role(vtyt,UserRole.PHONG_VTYT,null);
 }
 long equipment(long department){return jdbc.queryForObject("INSERT INTO equipment(equipment_code,name,department_id) VALUES (?,'Thiết bị kiểm thử quyền VTYT',?) RETURNING id",Long.class,"TRACK-"+UUID.randomUUID(),department);}
 long item(long equipment,long department){long coverage=jdbc.queryForObject("INSERT INTO maintenance_coverage(equipment_id,provider_id,classification,verified_by_user_id,verified_at,basis_note,effective_from,effective_to,warranty_expires_on) VALUES (?,?,'FREE',?,now(),'Hồ sơ kiểm thử','2026-01-01','2030-12-31','2028-06-30') RETURNING id",Long.class,equipment,provider,vtyt);return jdbc.queryForObject("INSERT INTO maintenance_plan_item(plan_id,equipment_id,department_id_at_plan,status,assigned_provider_id,assignment_route,coverage_id) VALUES (?,?,?,'UNDER_CONTRACT',?,'UNDER_CONTRACT',?) RETURNING id",Long.class,plan,equipment,department,provider,coverage);}
 int version(long item){return jdbc.queryForObject("SELECT version FROM maintenance_plan_item WHERE id=?",Integer.class,item);}
 int planVersion(){return jdbc.queryForObject("SELECT version FROM maintenance_plan WHERE id=?",Integer.class,plan);}
 long start(long item){return workflow.start(item,new StartExecutionRequest(version(item),planVersion())).executionId();}
 void progress(long item,long execution,MaintenanceProgressStatus status,String note){workflow.addProgress(execution,new ProgressRequest(status,note,version(item),null,null));}
 void role(long id,UserRole role,Long department){var principal=new AuthenticatedUser(id,"test",role,department);token=jwt.issue(principal);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role.name()))));}
 void finished(){long x=start(a);progress(a,x,MaintenanceProgressStatus.WORK_DONE,"Đã kiểm tra và hiệu chuẩn.");long y=start(b);progress(b,y,MaintenanceProgressStatus.DAMAGE_DETECTED,"Bơm có rò rỉ, cần xử lý tiếp.");tracking.complete(plan,new FinalizeReportRequest(planVersion()));}
 void finalized(){finished();reports.finalizeReport(plan,new FinalizeReportRequest(planVersion()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void providerEquipmentHasDistinctDevicesAndSpecificWarrantyDates(){long contract=jdbc.queryForObject("INSERT INTO maintenance_contract(contract_code,contract_name,provider_id,start_date,end_date) VALUES (?,'Thiết bị theo công ty',?,'2026-01-01','2027-12-31') RETURNING id",Long.class,"TRACK-HD-"+UUID.randomUUID(),provider);jdbc.update("INSERT INTO maintenance_contract_equipment(contract_id,equipment_id) VALUES (?,?),(?,?)",contract,eqA,contract,eqB);var rows=catalog.providerEquipment(provider).stream().filter(r->((Number)r.get("equipment_id")).longValue()==eqA).toList();assertThat(rows).hasSize(1);assertThat(rows.get(0).get("warranty_end_date").toString()).isEqualTo("2028-06-30");assertThat(rows.get(0).get("warranty_start_date").toString()).isEqualTo("2026-01-01");}
 @Test void threeProgressLevelsAndDamageDescriptionAreRecorded(){long execution=start(a);assertThat(tracking.tracking(plan).get(0).get("progress_status")).isIn("IN_PROGRESS","NOT_STARTED");progress(a,execution,MaintenanceProgressStatus.DAMAGE_DETECTED,"Mất tín hiệu cảm biến.");assertThat(tracking.tracking(plan).stream().filter(r->((Number)r.get("item_id")).longValue()==a).findFirst().orElseThrow()).containsEntry("progress_status","DAMAGE_DETECTED").containsEntry("damage_note","Mất tín hiệu cảm biến.");assertThatThrownBy(()->progress(a,execution,MaintenanceProgressStatus.DAMAGE_DETECTED,null)).hasMessageContaining("mô tả hỏng hóc");}
 @Test void completionRequiresAResultForEveryDevice(){long execution=start(a);progress(a,execution,MaintenanceProgressStatus.WORK_DONE,null);assertThatThrownBy(()->tracking.complete(plan,new FinalizeReportRequest(planVersion()))).hasMessageContaining("Cần cập nhật kết quả");assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_report WHERE plan_id=?",Long.class,plan)).isZero();assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE id=?",String.class,a)).isEqualTo("IN_MAINTENANCE");}
 @Test void vtytCompletionCreatesDraftWithoutForgedDepartmentSignatures(){finished();assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan WHERE id=?",String.class,plan)).isEqualTo("AWAITING_REPORT");var report=reports.get(plan);assertThat(report.status()).isEqualTo(ReportStatus.DRAFT);assertThat(report.completedCount()).isEqualTo(1);assertThat(report.repairRequiredCount()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record r JOIN maintenance_execution x ON x.id=r.execution_id JOIN maintenance_plan_item i ON i.id=x.plan_item_id WHERE i.plan_id=?",Long.class,plan)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM status_history h JOIN maintenance_plan_item i ON i.id=h.plan_item_id WHERE i.plan_id=? AND h.action='VTYT_COMPLETE_MAINTENANCE' AND h.actor_user_id=?",Long.class,plan,vtyt)).isEqualTo(2);}
 @Test void draftCannotBeSent(){finished();assertThatThrownBy(()->delivery.send(plan,new FinalizeReportRequest(planVersion()))).hasMessageContaining("Hoàn tất báo cáo");assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_report_delivery v JOIN maintenance_report r ON r.id=v.report_id WHERE r.plan_id=?",Long.class,plan)).isZero();}
 @Test void sendingFinalReportCreatesReceiptsAndNotificationsExactlyOnce(){finalized();delivery.send(plan,new FinalizeReportRequest(planVersion()));long before=jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE notification_type='REPORT_SHARED' AND target_url=?",Long.class,"/plans/"+plan+"/report");assertThat(before).isGreaterThanOrEqualTo(3);delivery.send(plan,new FinalizeReportRequest(planVersion()));assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE notification_type='REPORT_SHARED' AND target_url=?",Long.class,"/plans/"+plan+"/report")).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_report_delivery v JOIN maintenance_report r ON r.id=v.report_id WHERE r.plan_id=?",Long.class,plan)).isEqualTo(3);}
 @Test void departmentReceivesOnlyItsDevicesAndCannotEditOrSend() throws Exception {finalized();delivery.send(plan,new FinalizeReportRequest(planVersion()));long user=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='KHOA_PHONG' AND department_id=? ORDER BY id LIMIT 1",Long.class,departmentA);role(user,UserRole.KHOA_PHONG,departmentA);var own=reports.evidence(plan);assertThat(own.items()).hasSize(1);assertThat(own.items().get(0).equipmentCode()).isEqualTo(jdbc.queryForObject("SELECT equipment_code FROM equipment WHERE id=?",String.class,eqA));assertThat(reports.get(plan).workDone()).doesNotContain(jdbc.queryForObject("SELECT equipment_code FROM equipment WHERE id=?",String.class,eqB));assertThat(delivery.received(0,10).content()).anySatisfy(r->{assertThat(r.get("id")).isEqualTo(plan);assertThat(r.get("equipment_count")).isEqualTo(1L);});mvc.perform(post("/api/plans/"+plan+"/report/send").header("Authorization","Bearer "+token).contentType("application/json").content("{\"version\":"+planVersion()+"}")).andExpect(status().isForbidden());}
 @Test void departmentCannotReadBeforeDeliveryAndOnlyVtytMayComplete() throws Exception {finalized();long user=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='KHOA_PHONG' AND department_id=? ORDER BY id LIMIT 1",Long.class,departmentA);role(user,UserRole.KHOA_PHONG,departmentA);assertThatThrownBy(()->reports.get(plan)).isInstanceOf(BusinessRuleException.class);mvc.perform(post("/api/plans/"+plan+"/complete-maintenance").header("Authorization","Bearer "+token).contentType("application/json").content("{\"version\":"+planVersion()+"}")).andExpect(status().isForbidden());}

 @Test void createDeviceWithNewCompanyAndContractUpdatesCatalogAtomically(){
  String suffix=UUID.randomUUID().toString();var request=new CreateEquipmentRequest("NEW-EQ-"+suffix,"Máy mới",departmentA,"M1","S1",Set.of(MaintenanceQuarter.Q1,MaintenanceQuarter.Q3),LocalDate.of(2028,6,30),null,new CreateEquipmentRequest.NewContract("NEW-HD-"+suffix,"Hợp đồng mới",LocalDate.of(2026,1,1),LocalDate.of(2030,12,31),null,new CreateEquipmentRequest.NewCompany("NEW-CT-"+suffix,"Công ty mới "+suffix,"Liên hệ")));
  var row=equipmentManagement.create(request);long id=((Number)row.get("equipment_id")).longValue();long company=jdbc.queryForObject("SELECT id FROM service_provider WHERE name=?",Long.class,"Công ty mới "+suffix);
  assertThat(catalog.providerEquipment(company)).hasSize(1);assertThat(automation.contracts(company).get(0)).containsEntry("equipment_count",1L);
  assertThat(row.get("quarters")).isEqualTo("Q1, Q3");assertThat(row.get("warranty_end_date").toString()).isEqualTo("2028-06-30");
  equipmentManagement.remove(id);assertThat(catalog.providerEquipment(company)).isEmpty();assertThat(automation.contracts(company).get(0)).containsEntry("equipment_count",0L);
  assertThat(catalog.companies().stream().filter(c->((Number)c.get("id")).longValue()==company).findFirst().orElseThrow()).containsEntry("covered_equipment_count",0L);
  assertThat(catalog.equipment().stream().anyMatch(c->((Number)c.get("equipment_id")).longValue()==id)).isFalse();
 }
 @Test void creationValidationAndMutationPermissionsAreEnforcedOverHttp() throws Exception {
  long before=jdbc.queryForObject("SELECT count(*) FROM equipment",Long.class);
  mvc.perform(post("/api/equipment").header("Authorization","Bearer "+token).contentType("application/json").content("{\"equipmentCode\":\"MISSING-QUARTERS\",\"name\":\"Máy\",\"departmentId\":"+departmentA+",\"quarters\":[]}")).andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM equipment",Long.class)).isEqualTo(before);
  long admin=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='ADMIN' ORDER BY id LIMIT 1",Long.class);role(admin,UserRole.ADMIN,null);
  mvc.perform(delete("/api/equipment/"+eqA).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/plans/"+plan+"?version=0").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  mvc.perform(get("/api/maintenance-history").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 @Test void bulkTrackingPreservesDamageAndCreatesSearchableHistory() throws Exception {
  tracking.startAll(plan,new FinalizeReportRequest(planVersion()));var rows=tracking.tracking(plan);assertThat(rows).allSatisfy(r->assertThat(r).containsEntry("progress_status","IN_PROGRESS"));
  long execution=((Number)rows.stream().filter(r->((Number)r.get("item_id")).longValue()==a).findFirst().orElseThrow().get("execution_id")).longValue();progress(a,execution,MaintenanceProgressStatus.DAMAGE_DETECTED,"Hỏng cảm biến");
  tracking.markWorkDone(plan,new FinalizeReportRequest(planVersion()));assertThat(tracking.tracking(plan)).anySatisfy(r->assertThat(r).containsEntry("item_id",a).containsEntry("progress_status","DAMAGE_DETECTED"));
  tracking.complete(plan,new FinalizeReportRequest(planVersion()));String code=jdbc.queryForObject("SELECT equipment_code FROM equipment WHERE id=?",String.class,eqA);equipmentManagement.remove(eqA);
  mvc.perform(get("/api/maintenance-history").param("search",code).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].equipmentActive").value(false)).andExpect(jsonPath("$.content[0].reportStatus").value("DRAFT")).andExpect(jsonPath("$.content[0].status").value("REPAIR_REQUIRED"));
 }
 @Test void historyIsScopedToDepartmentAndBoardDelivery() throws Exception {
  finalized();String title="Bảo trì kiểm thử cập nhật";
  long board=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='BAN_GIAM_DOC' ORDER BY id LIMIT 1",Long.class);role(board,UserRole.BAN_GIAM_DOC,null);
  mvc.perform(get("/api/maintenance-history").param("search",title).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  role(vtyt,UserRole.PHONG_VTYT,null);delivery.send(plan,new FinalizeReportRequest(planVersion()));role(board,UserRole.BAN_GIAM_DOC,null);
  mvc.perform(get("/api/maintenance-history").param("search",title).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  long departmentUser=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='KHOA_PHONG' AND department_id=? ORDER BY id LIMIT 1",Long.class,departmentA);role(departmentUser,UserRole.KHOA_PHONG,departmentA);
  mvc.perform(get("/api/maintenance-history").param("search",title).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].equipmentId").value(eqA));
 }
 @Test void planHistoryGroupsDevicesAndSearchKeepsTheWholePlanSummary() throws Exception {
  finished();String code=jdbc.queryForObject("SELECT equipment_code FROM equipment WHERE id=?",String.class,eqA);
  mvc.perform(get("/api/maintenance-history/plans").param("search",code).param("size","1").header("Authorization","Bearer "+token))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].planId").value(plan))
   .andExpect(jsonPath("$.content[0].equipmentCount").value(2)).andExpect(jsonPath("$.content[0].completedCount").value(1))
   .andExpect(jsonPath("$.content[0].damagedCount").value(1)).andExpect(jsonPath("$.content[0].reportStatus").value("DRAFT"));
  mvc.perform(get("/api/maintenance-history").param("planId",String.valueOf(plan)).header("Authorization","Bearer "+token))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[*].planId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is((int)plan))));
  long departmentUser=jdbc.queryForObject("SELECT id FROM user_account WHERE active AND role_code='KHOA_PHONG' AND department_id=? ORDER BY id LIMIT 1",Long.class,departmentA);role(departmentUser,UserRole.KHOA_PHONG,departmentA);
  mvc.perform(get("/api/maintenance-history").param("planId",String.valueOf(plan)).header("Authorization","Bearer "+token))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].equipmentId").value(eqA));
  mvc.perform(get("/api/maintenance-history/plans").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
 }
 @Test void trackingListKeepsApprovedAndStartedPlansVisible() throws Exception {
  mvc.perform(get("/api/maintenance-tracking-plans").param("size","100").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == "+plan+")].status").value("APPROVED"));
  role(vtyt,UserRole.PHONG_VTYT,null);tracking.startAll(plan,new FinalizeReportRequest(planVersion()));
  mvc.perform(get("/api/maintenance-tracking-plans").param("size","100").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == "+plan+")].status").value("IN_PROGRESS"));
 }
 @Test void deletionRemovesOnlyNeverSubmittedDraftsAndChecksVersion() throws Exception {
  jdbc.update("UPDATE maintenance_plan SET status='DRAFT' WHERE id=?",plan);
  assertThatThrownBy(()->deletion.delete(plan,999)).hasMessageContaining("thay đổi");
  mvc.perform(delete("/api/plans/"+plan+"?version="+planVersion()).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan WHERE id=?",Long.class,plan)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=?",Long.class,plan)).isZero();
 }
 @Test void formerlySubmittedDraftCannotBeDeleted(){
  jdbc.update("UPDATE maintenance_plan SET status='DRAFT' WHERE id=?",plan);jdbc.update("INSERT INTO status_history(plan_id,actor_user_id,old_state,new_state,action,action_timestamp) VALUES (?,?,'DRAFT','SUBMITTED','SUBMIT',now())",plan,vtyt);
  assertThatThrownBy(()->deletion.delete(plan,planVersion())).hasMessageContaining("chưa từng gửi duyệt");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=?",Long.class,plan)).isEqualTo(2);
 }

 @Test void invalidWarrantyRollsBackNewCompanyAndContract(){
  String suffix=UUID.randomUUID().toString();var request=new CreateEquipmentRequest("ROLLBACK-EQ-"+suffix,"Máy mới",departmentA,null,null,Set.of(MaintenanceQuarter.Q1),LocalDate.of(2025,1,1),null,new CreateEquipmentRequest.NewContract("ROLLBACK-HD-"+suffix,"Hợp đồng mới",LocalDate.of(2026,1,1),LocalDate.of(2030,12,31),null,new CreateEquipmentRequest.NewCompany("ROLLBACK-CT-"+suffix,"Công ty mới",null)));
  var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  assertThatThrownBy(()->transaction.execute(status->equipmentManagement.create(request))).hasMessageContaining("Ngày hết bảo hành");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM service_provider WHERE code=?",Long.class,request.newContract().newCompany().code())).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_contract WHERE contract_code=?",Long.class,request.newContract().code())).isZero();
 }
}
