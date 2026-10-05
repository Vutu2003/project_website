package vn.edu.medmaintenance.api.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.service.BusinessRuleException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api")
public class MaintenanceOverviewController {
 private final JdbcTemplate jdbc;private final CurrentUser current;
 public MaintenanceOverviewController(JdbcTemplate jdbc,CurrentUser current){this.jdbc=jdbc;this.current=current;}
 @GetMapping("/maintenance-tracking-plans")
 public PageResponse<Map<String,Object>> plans(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) PlanStatus status){
  if(current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ VTYT theo dõi tiến độ.");
  String from=" FROM maintenance_plan p JOIN user_account u ON u.id=p.created_by_user_id WHERE p.status IN ('APPROVED','IN_PROGRESS','AWAITING_REPORT')";
  var args=new ArrayList<Object>();if(status!=null){from+=" AND p.status=?";args.add(status.name());}
  long total=jdbc.queryForObject("SELECT count(*)"+from,Long.class,args.toArray());args.add(page.getSize());args.add((long)page.getPage()*page.getSize());
  var rows=jdbc.queryForList("""
   SELECT p.id,p.title,p.status,p.version,p.period_start "periodStart",p.period_end "periodEnd",p.plan_year "planYear",p.plan_quarter "planQuarter",p.created_at "createdAt",p.created_by_user_id "createdByUserId",u.display_name "createdByName",
   (SELECT count(*) FROM maintenance_plan_item i WHERE i.plan_id=p.id) "equipmentCount",
   EXISTS(SELECT 1 FROM maintenance_plan_item i WHERE i.plan_id=p.id AND i.status IN ('PLANNED','PENDING_PROPOSAL','WAITING_VENDOR_APPROVAL')) "pendingVendorApproval"
   """+from+" ORDER BY p.created_at DESC,p.id DESC LIMIT ? OFFSET ?",args.toArray());
  int pages=(int)((total+page.getSize()-1)/page.getSize());return new PageResponse<>(rows,page.getPage(),page.getSize(),total,pages,page.getPage()+1>=pages);
 }
 @GetMapping("/maintenance-history")
 public PageResponse<Map<String,Object>> history(@Valid @ModelAttribute PageQuery page,@RequestParam(defaultValue="") String search,@RequestParam(required=false) Long planId){
  var viewer=current.get();String scope;var args=new ArrayList<Object>();
  switch(viewer.role()){
   case PHONG_VTYT -> scope="true";
   case BAN_GIAM_DOC -> scope="r.status='FINAL' AND EXISTS(SELECT 1 FROM maintenance_report_delivery v WHERE v.report_id=r.id AND v.department_id IS NULL)";
   case KHOA_PHONG -> {if(viewer.departmentId()==null)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"DEPARTMENT_SCOPE_VIOLATION","Tài khoản cần thuộc khoa/phòng.");scope="i.department_id_at_plan=?";args.add(viewer.departmentId());}
   default -> throw new BusinessRuleException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Bạn không có quyền xem lịch sử bảo trì.");
  }
  String from="""
   FROM maintenance_plan_item i JOIN maintenance_plan p ON p.id=i.plan_id JOIN equipment e ON e.id=i.equipment_id JOIN department d ON d.id=i.department_id_at_plan
   LEFT JOIN maintenance_report r ON r.plan_id=p.id
   LEFT JOIN LATERAL(SELECT x.* FROM maintenance_execution x WHERE x.plan_item_id=i.id ORDER BY x.attempt_no DESC LIMIT 1) x ON true
   LEFT JOIN service_provider s ON s.id=coalesce(x.provider_id,i.assigned_provider_id)
   LEFT JOIN LATERAL(SELECT max(h.action_timestamp) at FROM status_history h WHERE h.plan_item_id=i.id AND h.new_state IN ('COMPLETED','REPAIR_REQUIRED')) h ON true
   WHERE i.status IN ('COMPLETED','REPAIR_REQUIRED') AND p.status IN ('AWAITING_REPORT','REPORTED','CLOSED') AND
   """+scope;
  if(planId!=null){vn.edu.medmaintenance.api.common.PageRequests.requirePositive(planId,"planId");from+=" AND p.id=?";args.add(planId);}
  if(!search.isBlank()){from+=" AND (e.equipment_code ILIKE ? ESCAPE '!' OR e.name ILIKE ? ESCAPE '!' OR p.title ILIKE ? ESCAPE '!')";String match="%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(match);args.add(match);args.add(match);}
  long total=jdbc.queryForObject("SELECT count(*) "+from,Long.class,args.toArray());args.add(page.getSize());args.add((long)page.getPage()*page.getSize());
  String report=viewer.role()==UserRole.KHOA_PHONG?"CASE WHEN EXISTS(SELECT 1 FROM maintenance_report_delivery v WHERE v.report_id=r.id AND v.department_id=i.department_id_at_plan) THEN r.status END":"r.status";
  var rows=jdbc.queryForList("SELECT i.id \"itemId\",p.id \"planId\",p.title \"planTitle\",p.status \"planStatus\",e.id \"equipmentId\",e.equipment_code \"equipmentCode\",e.name \"equipmentName\",e.active \"equipmentActive\",d.name \"departmentName\",s.name provider,i.status,coalesce(h.at,x.ended_at,r.finalized_at) \"completedAt\",x.result_note \"resultNote\","+report+" \"reportStatus\",r.report_date \"reportDate\" "+from+" ORDER BY coalesce(h.at,x.ended_at,r.finalized_at) DESC NULLS LAST,i.id DESC LIMIT ? OFFSET ?",args.toArray());
  int pages=(int)((total+page.getSize()-1)/page.getSize());return new PageResponse<>(rows,page.getPage(),page.getSize(),total,pages,page.getPage()+1>=pages);
 }
 @GetMapping("/maintenance-history/plans")
 public PageResponse<Map<String,Object>> historyPlans(@Valid @ModelAttribute PageQuery page,@RequestParam(defaultValue="") String search){
  if(current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ VTYT tra cứu lịch sử theo kế hoạch.");
  var args=new ArrayList<Object>();
  String from="""
   FROM maintenance_plan p JOIN maintenance_plan_item i ON i.plan_id=p.id
   LEFT JOIN maintenance_report r ON r.plan_id=p.id
   LEFT JOIN LATERAL(SELECT x.ended_at FROM maintenance_execution x WHERE x.plan_item_id=i.id ORDER BY x.attempt_no DESC LIMIT 1) x ON true
   LEFT JOIN LATERAL(SELECT max(h.action_timestamp) at FROM status_history h WHERE h.plan_item_id=i.id AND h.new_state IN ('COMPLETED','REPAIR_REQUIRED')) h ON true
   WHERE p.status IN ('AWAITING_REPORT','REPORTED','CLOSED') AND i.status IN ('COMPLETED','REPAIR_REQUIRED')
   """;
  if(!search.isBlank()){
   // Match the plan without removing its other devices from the summary counts.
   from+=" AND (p.title ILIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM maintenance_plan_item m JOIN equipment e ON e.id=m.equipment_id WHERE m.plan_id=p.id AND (e.equipment_code ILIKE ? ESCAPE '!' OR e.name ILIKE ? ESCAPE '!')))";
   String match="%"+search.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(match);args.add(match);args.add(match);
  }
  long total=jdbc.queryForObject("SELECT count(DISTINCT p.id) "+from,Long.class,args.toArray());
  args.add(page.getSize());args.add((long)page.getPage()*page.getSize());
  var rows=jdbc.queryForList("""
   SELECT p.id "planId",p.title "planTitle",p.period_start "periodStart",p.period_end "periodEnd",
   p.plan_year "planYear",p.plan_quarter "planQuarter",count(*) "equipmentCount",
   count(*) FILTER(WHERE i.status='COMPLETED') "completedCount",
   count(*) FILTER(WHERE i.status='REPAIR_REQUIRED') "damagedCount",
   max(coalesce(h.at,x.ended_at,r.finalized_at)) "completedAt",r.status "reportStatus"
   """+from+" GROUP BY p.id,r.status ORDER BY \"completedAt\" DESC NULLS LAST,p.id DESC LIMIT ? OFFSET ?",args.toArray());
  int pages=(int)((total+page.getSize()-1)/page.getSize());return new PageResponse<>(rows,page.getPage(),page.getSize(),total,pages,page.getPage()+1>=pages);
 }
}
