package vn.edu.medmaintenance.service;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import vn.edu.medmaintenance.api.dto.request.FinalizeReportRequest;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class MaintenanceTrackingService {
 private final JdbcTemplate jdbc;
 private final MaintenancePlanRepository plans;
 private final MaintenancePlanItemRepository items;
 private final MaintenanceExecutionRepository executions;
 private final MaintenanceReportRepository reports;
 private final UserAccountRepository users;
 private final CurrentUser current;
 private final WorkflowHistory history;
 private final EntityManager em;
 public MaintenanceTrackingService(JdbcTemplate jdbc,MaintenancePlanRepository plans,MaintenancePlanItemRepository items,
   MaintenanceExecutionRepository executions,MaintenanceReportRepository reports,UserAccountRepository users,
   CurrentUser current,WorkflowHistory history,EntityManager em){
  this.jdbc=jdbc;this.plans=plans;this.items=items;this.executions=executions;this.reports=reports;
  this.users=users;this.current=current;this.history=history;this.em=em;
 }
 private UserAccount planner(){if(current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"BUSINESS_ACCESS_DENIED","Chỉ Phòng VTYT được cập nhật bảo trì.");return users.getReferenceById(current.get().id());}
 private MaintenancePlan plan(long id){return plans.findById(id).orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Plan"));}
 @Transactional(readOnly=true)
 public List<Map<String,Object>> tracking(long planId){
  planner();plan(planId);
  var rows=jdbc.queryForList("""
   SELECT i.id item_id,i.equipment_id,e.equipment_code,e.name equipment_name,d.name department_name,
   i.status,i.version,p.name provider_name,x.id execution_id,x.started_at,x.ended_at,l.event_at updated_at,l.work_note,l.damage_note
   FROM maintenance_plan_item i JOIN equipment e ON e.id=i.equipment_id JOIN department d ON d.id=i.department_id_at_plan
   LEFT JOIN service_provider p ON p.id=i.assigned_provider_id
   LEFT JOIN LATERAL (SELECT v.* FROM maintenance_execution v WHERE v.plan_item_id=i.id ORDER BY v.attempt_no DESC,v.id DESC LIMIT 1) x ON true
   LEFT JOIN LATERAL (SELECT v.* FROM maintenance_progress_log v WHERE v.execution_id=x.id ORDER BY v.event_at DESC,v.id DESC LIMIT 1) l ON true
   WHERE i.plan_id=? ORDER BY e.equipment_code
   """,planId);
  rows.forEach(r->r.put("progress_status",stage(r)));return rows;
 }
 private String stage(Map<String,Object> r){
  String state=(String)r.get("status");
  if(state.equals("COMPLETED"))return "WORK_DONE";
  if(state.equals("REPAIR_REQUIRED"))return "DAMAGE_DETECTED";
  if(r.get("execution_id")==null || state.equals("REWORK_REQUIRED"))return "NOT_STARTED";
  if(Set.of("AWAITING_TECHNICAL_ACCEPTANCE","AWAITING_HANDOVER").contains(state) && r.get("ended_at")!=null)return "WORK_DONE";
  String first=Objects.toString(r.get("work_note"),"").split("\n",2)[0];
  if(first.equals("Bảo trì xong") || first.equals("Đã xử lý xong"))return "WORK_DONE";
  if(first.equals("Có hỏng hóc"))return "DAMAGE_DETECTED";
  return "IN_PROGRESS";
 }
 @Transactional
 public Map<String,Object> complete(long planId,FinalizeReportRequest command){
  UserAccount actor=planner();MaintenancePlan plan=plan(planId);em.lock(plan,LockModeType.PESSIMISTIC_WRITE);em.refresh(plan);
  if(!Objects.equals(plan.getVersion(),command.version()))fail("OPTIMISTIC_LOCK_CONFLICT","Kế hoạch đã thay đổi. Vui lòng tải lại.");
  if(!Set.of(PlanStatus.APPROVED,PlanStatus.IN_PROGRESS,PlanStatus.AWAITING_REPORT).contains(plan.getStatus()))fail("PLAN_NOT_EXECUTABLE","Kế hoạch chưa được phê duyệt hoặc đã báo cáo.");
  var rows=tracking(planId);if(rows.isEmpty())fail("PLAN_NOT_REPORTABLE","Kế hoạch chưa có thiết bị.");
  var unfinished=rows.stream().filter(r->!Set.of("WORK_DONE","DAMAGE_DETECTED").contains(r.get("progress_status")) ||
    (!Set.of("COMPLETED","REPAIR_REQUIRED").contains(r.get("status")) && !Set.of("IN_MAINTENANCE","AWAITING_TECHNICAL_ACCEPTANCE","AWAITING_HANDOVER").contains(r.get("status")))).toList();
  if(!unfinished.isEmpty())fail("MAINTENANCE_NOT_FINISHED","Cần cập nhật kết quả cho: "+String.join(", ",unfinished.stream().map(r->(String)r.get("equipment_code")).toList()));
  var byId=new HashMap<Long,MaintenancePlanItem>();for(var item:items.findAllByPlan_Id(planId))byId.put(item.getId(),item);
  OffsetDateTime at=OffsetDateTime.now(ZoneOffset.UTC);
  for(var row:rows){var item=byId.get(((Number)row.get("item_id")).longValue());
   if(Set.of(PlanItemStatus.COMPLETED,PlanItemStatus.REPAIR_REQUIRED).contains(item.getStatus()))continue;
   var execution=executions.findById(((Number)row.get("execution_id")).longValue()).orElseThrow();
   boolean damaged=row.get("progress_status").equals("DAMAGE_DETECTED");
   String reason=Objects.toString(row.get("damage_note"),"").trim();
   if(damaged && reason.isBlank())reason=Objects.toString(row.get("work_note"),"Có hỏng hóc");
   if(execution.getEndedAt()==null)execution.setEndedAt(at);
   if(execution.getResultNote()==null)execution.setResultNote(damaged?reason:Objects.toString(row.get("work_note"),"Bảo trì xong"));
   String old=item.getStatus().name();item.setStatus(damaged?PlanItemStatus.REPAIR_REQUIRED:PlanItemStatus.COMPLETED);
   // This is a VTYT completion decision, not a fabricated department handover signature.
   history.itemTransition(item,actor,old,item.getStatus().name(),"VTYT_COMPLETE_MAINTENANCE",damaged?reason:"VTYT xác nhận hoàn thành bảo trì",at);
  }
  if(plan.getStatus()!=PlanStatus.AWAITING_REPORT){String old=plan.getStatus().name();plan.setStatus(PlanStatus.AWAITING_REPORT);history.plan(plan,actor,old,"AWAITING_REPORT","VTYT_COMPLETE_MAINTENANCE","VTYT xác nhận kết quả bảo trì và lập báo cáo",at);}
  MaintenanceReport report=reports.findByPlan_Id(planId).orElse(null);
  if(report==null){report=new MaintenanceReport();report.setPlan(plan);report.setCreatedByUser(actor);report.setStatus(ReportStatus.DRAFT);report.setReportDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
   report.setWorkDone(String.join("\n",rows.stream().map(r->r.get("equipment_code")+" · "+r.get("equipment_name")+": "+(r.get("progress_status").equals("WORK_DONE")?"Bảo trì xong":"Có hỏng hóc")+"\n"+Objects.toString(r.get("work_note"),"")).toList()));
   long damaged=rows.stream().filter(r->r.get("progress_status").equals("DAMAGE_DETECTED")).count();
   report.setAchieved((rows.size()-damaged)+" thiết bị đã hoàn thành bảo trì.");report.setNotAchieved(damaged+" thiết bị có hỏng hóc, cần xử lý tiếp.");
   report.setCauses(String.join("\n",rows.stream().filter(r->r.get("progress_status").equals("DAMAGE_DETECTED")).map(r->r.get("equipment_code")+": "+Objects.toString(r.get("damage_note"),Objects.toString(r.get("work_note"),""))).toList()));reports.save(report);
  }
  em.flush();return Map.of("planId",planId,"reportId",report.getId(),"planVersion",plan.getVersion(),"status",plan.getStatus());
 }
 private void fail(String code,String message){throw new BusinessRuleException(HttpStatus.CONFLICT,code,message);}
}
