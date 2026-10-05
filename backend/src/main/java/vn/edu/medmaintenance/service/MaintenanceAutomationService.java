package vn.edu.medmaintenance.service;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
@Service
public class MaintenanceAutomationService {
 private final JdbcTemplate jdbc;
 private final int soonDays;
 public MaintenanceAutomationService(JdbcTemplate jdbc,@Value("${app.maintenance.due-soon-days:30}") int days){this.jdbc=jdbc;this.soonDays=Math.max(0,days);}
 public int soonDays(){return soonDays;}
 public static final String SCHEDULE_SQL="""
 SELECT e.id,e.equipment_code,e.name,d.id department_id,d.name department_name,e.maintenance_enabled,
 e.maintenance_interval_value,e.maintenance_interval_unit,e.commissioning_date,h.last_date,
 CASE WHEN e.maintenance_enabled AND coalesce(h.last_date,e.commissioning_date) IS NOT NULL THEN
 (coalesce(h.last_date,e.commissioning_date) + e.maintenance_interval_value *
 CASE e.maintenance_interval_unit WHEN 'DAY' THEN interval '1 day' WHEN 'MONTH' THEN interval '1 month' WHEN 'YEAR' THEN interval '1 year' END)::date END next_due
 FROM equipment e JOIN department d ON d.id=e.department_id
 LEFT JOIN (SELECT i.equipment_id,max((x.ended_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date) last_date
 FROM maintenance_plan_item i JOIN maintenance_execution x ON x.plan_item_id=i.id
 JOIN acceptance_record a ON a.execution_id=x.id
 WHERE x.ended_at IS NOT NULL AND a.acceptance_type='HANDOVER_ACCEPTANCE' AND a.result='PASS'
 AND a.department_confirmed_at IS NOT NULL AND a.vtyt_confirmed_at IS NOT NULL
 GROUP BY i.equipment_id) h ON h.equipment_id=e.id
 """;
 public Map<String,Object> schedule(long equipmentId){
  return jdbc.queryForList(SCHEDULE_SQL+" WHERE e.id=?",equipmentId).stream().findFirst()
   .orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Equipment"));
 }
 public void snapshot(MaintenancePlanItem item){
  var row=schedule(item.getEquipment().getId());
  item.setLastMaintenanceDate(date(row.get("last_date")));item.setMaintenanceDueDate(date(row.get("next_due")));
 }
 public static LocalDate date(Object value){return value==null?null:((java.sql.Date)value).toLocalDate();}
 public List<Map<String,Object>> contracts(Long provider){
  return jdbc.queryForList("""
   SELECT k.id,k.contract_code,k.contract_name,k.provider_id,p.name provider_name,k.start_date,k.end_date,k.active,k.notes,
   CASE WHEN NOT k.active OR NOT p.active THEN 'INACTIVE' WHEN k.end_date<(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 'EXPIRED' WHEN k.start_date>(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 'FUTURE' ELSE 'ACTIVE' END status,
   count(DISTINCT c.equipment_id) equipment_count
   FROM maintenance_contract k JOIN service_provider p ON p.id=k.provider_id
   LEFT JOIN maintenance_contract_equipment c ON c.contract_id=k.id
   WHERE (?::bigint IS NULL OR k.provider_id=?) GROUP BY k.id,p.id ORDER BY k.start_date DESC,k.id DESC
   """,provider,provider);
 }
 public Map<String,Object> contract(long id){return contracts(null).stream().filter(k->((Number)k.get("id")).longValue()==id).findFirst()
  .orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Contract"));}
 public List<Map<String,Object>> contractEquipment(long id){
  contract(id);
  return jdbc.queryForList("SELECT e.id,e.equipment_code,e.name,d.name department_name,e.active,(SELECT string_agg(s.quarter,', ' ORDER BY s.quarter) FROM equipment_maintenance_schedule s WHERE s.equipment_id=e.id) quarters FROM maintenance_contract_equipment m JOIN equipment e ON e.id=m.equipment_id JOIN department d ON d.id=e.department_id WHERE m.contract_id=? ORDER BY e.equipment_code",id);
 }
 public Map<String,Object> provider(long id){
  var p=jdbc.queryForList("SELECT id,code,name,contact_details,active FROM service_provider WHERE id=?",id).stream().findFirst()
   .orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Provider"));
  var contracts=contracts(id);p.put("contracts",contracts);
  p.put("covered_equipment_count",jdbc.queryForObject("""
   SELECT count(DISTINCT c.equipment_id) FROM maintenance_contract_equipment c JOIN maintenance_contract k ON k.id=c.contract_id JOIN service_provider p ON p.id=k.provider_id
   WHERE k.provider_id=? AND k.active AND p.active AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN k.start_date AND k.end_date
   """,Long.class,id));return p;
 }
 @Transactional public Map<String,Object> configure(long id,ScheduleInput input){
  schedule(id);
  if(input.enabled() && (input.intervalValue()==null || input.intervalUnit()==null))
   throw new vn.edu.medmaintenance.api.exception.InvalidParameterException("maintenance", "Chu kỳ bắt buộc khi bật bảo trì định kỳ");
  if((input.intervalValue()==null)!=(input.intervalUnit()==null))
   throw new vn.edu.medmaintenance.api.exception.InvalidParameterException("maintenance", "Nhập đủ giá trị và đơn vị chu kỳ");
  jdbc.update("UPDATE equipment SET maintenance_enabled=?,maintenance_interval_value=?,maintenance_interval_unit=?,commissioning_date=? WHERE id=?",
   input.enabled(),input.intervalValue(),input.intervalUnit(),input.commissioningDate(),id);return schedule(id);
 }
 public record ScheduleInput(boolean enabled,@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(1200) Integer intervalValue,
 @jakarta.validation.constraints.Pattern(regexp="DAY|MONTH|YEAR") String intervalUnit,LocalDate commissioningDate){}
}
