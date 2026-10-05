package vn.edu.medmaintenance.service;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.api.exception.ResourceNotFoundException;
@Service
public class PlanDeletionService {
 private final JdbcTemplate jdbc;private final CurrentUser current;
 public PlanDeletionService(JdbcTemplate jdbc,CurrentUser current){this.jdbc=jdbc;this.current=current;}
 @Transactional public void delete(long id,int version){
  if(current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ VTYT được xóa kế hoạch nháp.");
  var rows=jdbc.queryForList("SELECT status,version FROM maintenance_plan WHERE id=? FOR UPDATE",id);
  if(rows.isEmpty())throw new ResourceNotFoundException("Plan");var plan=rows.get(0);
  if(!Objects.equals(plan.get("version"),version))throw new BusinessRuleException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","Kế hoạch đã thay đổi. Vui lòng tải lại.");
  if(!plan.get("status").equals("DRAFT")||jdbc.queryForObject("SELECT count(*) FROM approval_request r LEFT JOIN maintenance_plan_item i ON i.id=r.plan_item_id WHERE (r.plan_id=? OR i.plan_id=?) AND (r.request_type='PLAN_APPROVAL' OR r.submitted_at IS NOT NULL OR r.status<>'DRAFT')",Long.class,id,id)>0||jdbc.queryForObject("SELECT count(*) FROM status_history WHERE plan_id=? AND new_state='SUBMITTED'",Long.class,id)>0)
   throw new BusinessRuleException(HttpStatus.CONFLICT,"PLAN_ALREADY_SUBMITTED","Chỉ xóa được kế hoạch nháp chưa từng gửi duyệt.");
  if(jdbc.queryForObject("SELECT count(*) FROM maintenance_execution x JOIN maintenance_plan_item i ON i.id=x.plan_item_id WHERE i.plan_id=?",Long.class,id)>0||jdbc.queryForObject("SELECT count(*) FROM maintenance_report WHERE plan_id=?",Long.class,id)>0)
   throw new BusinessRuleException(HttpStatus.CONFLICT,"PLAN_HAS_EXECUTION","Kế hoạch đã có dữ liệu thực hiện, không thể xóa.");
  jdbc.update("DELETE FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)",id);
  jdbc.update("DELETE FROM status_history WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)",id,id);
  jdbc.update("DELETE FROM maintenance_plan_item WHERE plan_id=?",id);jdbc.update("DELETE FROM maintenance_plan WHERE id=?",id);
 }
}
