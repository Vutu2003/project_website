package vn.edu.medmaintenance.service;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.PlanCommandResponse;
@Service
public class QuarterlyPlanningService {
 private final JdbcTemplate jdbc; private final PlanningService planning;
 public QuarterlyPlanningService(JdbcTemplate jdbc,PlanningService planning){this.jdbc=jdbc;this.planning=planning;}
 // Warranty dates remain equipment evidence, independent of maintenance-contract expiry.
 public static final String WARRANTY_JOIN="""
 LEFT JOIN LATERAL (SELECT c.effective_from warranty_start_date,c.warranty_expires_on warranty_end_date
   FROM maintenance_coverage c WHERE c.equipment_id=e.id AND c.warranty_expires_on IS NOT NULL
   ORDER BY c.verified_at DESC NULLS LAST,c.id DESC LIMIT 1) w ON true
 """;
 public static final String WARRANTY_COLUMNS="""
 w.warranty_start_date,w.warranty_end_date,
 CASE WHEN w.warranty_end_date IS NULL THEN 'UNRECORDED'
 WHEN w.warranty_start_date>(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date OR w.warranty_end_date<(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date THEN 'EXPIRED' ELSE 'VALID' END warranty_status,
 w.warranty_end_date-(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date warranty_days_remaining,
 """;
 public static final String EQUIPMENT_SQL="""
 SELECT e.id equipment_id,e.equipment_code,e.name equipment_name,d.name department_name,e.active,
 """+WARRANTY_COLUMNS+"""
 coalesce((SELECT string_agg(s.quarter,', ' ORDER BY s.quarter) FROM equipment_maintenance_schedule s WHERE s.equipment_id=e.id),'') quarters
 FROM equipment e JOIN department d ON d.id=e.department_id
 """+WARRANTY_JOIN;
 private static final String CONTRACT_SQL="""
 SELECT m.equipment_id,k.id contract_id,k.contract_code,k.contract_name,k.provider_id,p.name provider_name,k.start_date,k.end_date,k.active,
 (k.active AND p.active AND ?::date BETWEEN k.start_date AND k.end_date) valid
 FROM maintenance_contract_equipment m JOIN maintenance_contract k ON k.id=m.contract_id JOIN service_provider p ON p.id=k.provider_id
 ORDER BY k.start_date DESC,k.id DESC
 """;
 @Transactional(readOnly=true)
 public Map<String,Object> preview(int year,MaintenanceQuarter quarter){
  validateYear(year);var reference=quarter.referenceDate(year);
  var rows=jdbc.queryForList(EQUIPMENT_SQL+" WHERE e.active AND EXISTS(SELECT 1 FROM equipment_maintenance_schedule s WHERE s.equipment_id=e.id AND s.quarter=?) ORDER BY e.equipment_code",quarter.name());
  var contracts=jdbc.queryForList(CONTRACT_SQL,reference);
  var byEquipment=new HashMap<Long,List<Map<String,Object>>>();
  for(var c:contracts)if(Boolean.TRUE.equals(c.get("valid")))byEquipment.computeIfAbsent(((Number)c.get("equipment_id")).longValue(),k->new ArrayList<>()).add(c);
  for(var row:rows){var valid=byEquipment.getOrDefault(((Number)row.get("equipment_id")).longValue(),List.of());
   row.put("classification",valid.isEmpty()?"NOT_FREE":"FREE");row.put("contract_status",valid.isEmpty()?"EXPIRED":"VALID");row.put("conflict",valid.size()>1);
   if(valid.size()==1)row.putAll(valid.get(0));
  }
  var result=new LinkedHashMap<String,Object>();result.put("year",year);result.put("quarter",quarter);result.put("title",quarter.title(year));result.put("periodStart",quarter.start(year));result.put("periodEnd",quarter.end(year));result.put("referenceDate",reference);result.put("equipment",rows);return result;
 }
 @Transactional
 public PlanCommandResponse create(QuarterlyPlanRequest input){
  var preview=preview(input.year(),input.quarter());
  var rows=(List<?>)preview.get("equipment");var proposals=new HashMap<Long,QuarterlyPlanRequest.Proposal>();
  if(input.proposals()!=null)for(var p:input.proposals())if(proposals.put(p.equipmentId(),p)!=null)fail("DUPLICATE_PROPOSAL","Thiết bị xuất hiện nhiều lần.");
  var items=new ArrayList<PlanItemInput>();
  for(var value:rows){var row=(Map<?,?>)value;long id=((Number)row.get("equipment_id")).longValue();var proposal=proposals.remove(id);
   if(Boolean.TRUE.equals(row.get("conflict")))fail("CONTRACT_CONFLICT","Thiết bị "+row.get("equipment_code")+" có nhiều hợp đồng hợp lệ.");
   items.add(new PlanItemInput(id,input.quarter().referenceDate(input.year()),null,null,proposal==null?null:proposal.proposedProviderId(),proposal==null?null:proposal.rationale(),null,null,null));
  }
  if(!proposals.isEmpty())fail("INVALID_QUARTER_EQUIPMENT","Đề xuất không thuộc danh sách thiết bị của quý.");
  if(items.isEmpty())fail("EMPTY_QUARTER","Quý này chưa có thiết bị hoạt động được lên lịch.");
  return planning.createQuarterly(input.year(),input.quarter(),items);
 }
 public List<Map<String,Object>> companies(){return jdbc.queryForList("""
 SELECT p.id,p.code,p.name,p.contact_details,p.active,
 count(DISTINCT k.id) contract_count,
 count(DISTINCT k.id) FILTER(WHERE k.active AND p.active AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN k.start_date AND k.end_date) active_contract_count,
 count(DISTINCT m.equipment_id) FILTER(WHERE e.active) covered_equipment_count
 FROM service_provider p LEFT JOIN maintenance_contract k ON k.provider_id=p.id LEFT JOIN maintenance_contract_equipment m ON m.contract_id=k.id
 LEFT JOIN equipment e ON e.id=m.equipment_id
 GROUP BY p.id ORDER BY p.name
 """);}
 public List<Map<String,Object>> equipment(){
  var rows=jdbc.queryForList(EQUIPMENT_SQL+" WHERE e.active ORDER BY e.equipment_code");
  var contracts=jdbc.queryForList(CONTRACT_SQL,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
  var validIds=new HashSet<Long>();for(var c:contracts)if(Boolean.TRUE.equals(c.get("valid")))validIds.add(((Number)c.get("equipment_id")).longValue());
  for(var row:rows)row.put("contract_status",validIds.contains(((Number)row.get("equipment_id")).longValue())?"VALID":"EXPIRED");return rows;
 }
 public List<Map<String,Object>> providerEquipment(long providerId){
  if(jdbc.queryForObject("SELECT count(*) FROM service_provider WHERE id=?",Long.class,providerId)==0)
   throw new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Provider");
  return jdbc.queryForList("SELECT x.*,k.contracts FROM ("+EQUIPMENT_SQL+") x JOIN (SELECT m.equipment_id,string_agg(k.contract_code,', ' ORDER BY k.contract_code) contracts FROM maintenance_contract_equipment m JOIN maintenance_contract k ON k.id=m.contract_id WHERE k.provider_id=? GROUP BY m.equipment_id) k ON k.equipment_id=x.equipment_id WHERE x.active ORDER BY x.equipment_code",providerId);
 }
 public Map<String,Object> equipment(long id){
  var row=jdbc.queryForList(EQUIPMENT_SQL+" WHERE e.id=?",id).stream().findFirst().orElseThrow(()->new vn.edu.medmaintenance.api.exception.ResourceNotFoundException("Equipment"));
  row.put("contracts",jdbc.queryForList("SELECT c.* FROM ("+CONTRACT_SQL+") c WHERE c.equipment_id=?",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")),id));return row;
 }
 private void validateYear(int year){if(year<2000 || year>2100)throw new BusinessRuleException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_YEAR","Năm phải từ 2000 đến 2100.");}
 private void fail(String code,String message){throw new BusinessRuleException(org.springframework.http.HttpStatus.CONFLICT,code,message);}
}
