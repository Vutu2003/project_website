package vn.edu.medmaintenance.service;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.medmaintenance.api.dto.request.FinalizeReportRequest;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class ReportDeliveryService {
 private final JdbcTemplate jdbc;private final CurrentUser current;
 private final MaintenancePlanRepository plans;private final MaintenanceReportRepository reports;
 private final UserAccountRepository users;private final NotificationService notifications;private final EntityManager em;
 public ReportDeliveryService(JdbcTemplate jdbc,CurrentUser current,MaintenancePlanRepository plans,MaintenanceReportRepository reports,
  UserAccountRepository users,NotificationService notifications,EntityManager em){this.jdbc=jdbc;this.current=current;this.plans=plans;this.reports=reports;this.users=users;this.notifications=notifications;this.em=em;}
 @Transactional(readOnly=true)
 public Map<String,Object> delivery(long planId){
  if(!Set.of(UserRole.PHONG_VTYT,UserRole.BAN_GIAM_DOC).contains(current.get().role()))deny();
  var report=reports.findByPlan_Id(planId).orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Report"));
  var result=new LinkedHashMap<String,Object>();result.put("reportId",report.getId());result.put("canSend",report.getStatus()==ReportStatus.FINAL);
  result.put("departments",jdbc.queryForList("SELECT DISTINCT d.id,d.name FROM maintenance_plan_item i JOIN department d ON d.id=i.department_id_at_plan WHERE i.plan_id=? ORDER BY d.name",planId));
  result.put("deliveries",jdbc.queryForList("SELECT v.id,v.department_id,coalesce(d.name,'Ban Giám đốc') recipient,v.sent_at,u.display_name sent_by FROM maintenance_report_delivery v LEFT JOIN department d ON d.id=v.department_id JOIN user_account u ON u.id=v.sent_by_user_id WHERE v.report_id=? ORDER BY v.department_id NULLS FIRST",report.getId()));return result;
 }
 @Transactional
 public Map<String,Object> send(long planId,FinalizeReportRequest command){
  if(current.get().role()!=UserRole.PHONG_VTYT)deny();
  var plan=plans.findById(planId).orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Plan"));
  em.lock(plan,LockModeType.PESSIMISTIC_WRITE);em.refresh(plan);
  if(!Objects.equals(plan.getVersion(),command.version()))fail("OPTIMISTIC_LOCK_CONFLICT","Kế hoạch đã thay đổi. Vui lòng tải lại báo cáo.");
  var report=reports.findByPlan_Id(planId).orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Report"));
  if(report.getStatus()!=ReportStatus.FINAL || plan.getStatus()!=PlanStatus.REPORTED)fail("REPORT_NOT_FINAL","Hoàn tất báo cáo trước khi gửi.");
  // Idempotent delivery: retries never create duplicate receipts or notifications.
  if(jdbc.queryForObject("SELECT count(*) FROM maintenance_report_delivery WHERE report_id=?",Long.class,report.getId())>0)return delivery(planId);
  var actor=users.getReferenceById(current.get().id());var at=OffsetDateTime.now(ZoneOffset.UTC);
  jdbc.update("INSERT INTO maintenance_report_delivery(report_id,sent_by_user_id,sent_at) VALUES (?,?,?)",report.getId(),actor.getId(),at);
  String url="/plans/"+planId+"/report";
  notifications.notifyRole(UserRole.BAN_GIAM_DOC,null,actor,"REPORT_SHARED","VTYT gửi báo cáo bảo trì",plan.getTitle(),url);
  for(long department:jdbc.queryForList("SELECT DISTINCT department_id_at_plan FROM maintenance_plan_item WHERE plan_id=?",Long.class,planId)){
   jdbc.update("INSERT INTO maintenance_report_delivery(report_id,department_id,sent_by_user_id,sent_at) VALUES (?,?,?,?)",report.getId(),department,actor.getId(),at);
   notifications.notifyRole(UserRole.KHOA_PHONG,department,actor,"REPORT_SHARED","Báo cáo bảo trì của khoa/phòng",plan.getTitle(),url);
  }
  return delivery(planId);
 }
 @Transactional(readOnly=true)
 public PageResponse<Map<String,Object>> received(int page,int size){
  var viewer=current.get();String condition;Object scope;
  if(viewer.role()==UserRole.BAN_GIAM_DOC){condition="v.department_id IS NULL";scope=null;}
  else if(viewer.role()==UserRole.KHOA_PHONG && viewer.departmentId()!=null){condition="v.department_id=?";scope=viewer.departmentId();}
  else {deny();return null;}
  var parameters=scope==null?new Object[]{}:new Object[]{scope};
  String from=" FROM maintenance_report_delivery v JOIN maintenance_report r ON r.id=v.report_id JOIN maintenance_plan p ON p.id=r.plan_id JOIN user_account u ON u.id=v.sent_by_user_id WHERE r.status='FINAL' AND "+condition;
  long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class,parameters);
  var args=new ArrayList<Object>(Arrays.asList(parameters));args.add(size);args.add((long)page*size);
  var rows=jdbc.queryForList("SELECT p.id,p.title,p.period_start,p.period_end,p.status,p.plan_year,p.plan_quarter,v.sent_at,u.display_name sent_by,(SELECT count(*) FROM maintenance_plan_item i WHERE i.plan_id=p.id AND (v.department_id IS NULL OR i.department_id_at_plan=v.department_id)) equipment_count"+from+" ORDER BY v.sent_at DESC,v.id DESC LIMIT ? OFFSET ?",args.toArray());
  int pages=(int)((total+size-1)/size);return new PageResponse<>(rows,page,size,total,pages,page+1>=pages);
 }
 private void deny(){throw new BusinessRuleException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Bạn không có quyền truy cập báo cáo này.");}
 private void fail(String code,String message){throw new BusinessRuleException(HttpStatus.CONFLICT,code,message);}
}
