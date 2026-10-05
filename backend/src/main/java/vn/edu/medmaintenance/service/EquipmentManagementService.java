package vn.edu.medmaintenance.service;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import vn.edu.medmaintenance.api.dto.request.CreateEquipmentRequest;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.api.exception.ResourceNotFoundException;
@Service
public class EquipmentManagementService {
 private final JdbcTemplate jdbc;private final CurrentUser current;private final QuarterlyPlanningService catalog;
 public EquipmentManagementService(JdbcTemplate jdbc,CurrentUser current,QuarterlyPlanningService catalog){this.jdbc=jdbc;this.current=current;this.catalog=catalog;}
 private void vtyt(){if(current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Chỉ VTYT được thêm hoặc xóa thiết bị.");}
 private void conflict(String code,String message){throw new BusinessRuleException(HttpStatus.CONFLICT,code,message);}
 private void invalid(String message){throw new BusinessRuleException(HttpStatus.BAD_REQUEST,"INVALID_EQUIPMENT",message);}
 private String code(String value){return value.trim().toUpperCase(Locale.ROOT);}
 private void lockCode(String value){jdbc.execute("SELECT pg_advisory_xact_lock("+(long)value.hashCode()+")");}
 @Transactional
 public Map<String,Object> create(CreateEquipmentRequest input){
  vtyt();String equipmentCode=code(input.equipmentCode());lockCode(equipmentCode);
  if(jdbc.queryForObject("SELECT count(*) FROM equipment WHERE upper(equipment_code)=?",Long.class,equipmentCode)>0)conflict("EQUIPMENT_CODE_EXISTS","Mã thiết bị đã tồn tại, kể cả trong hồ sơ đã xóa.");
  if(jdbc.queryForList("SELECT id FROM department WHERE id=? AND active FOR SHARE",Long.class,input.departmentId()).isEmpty())invalid("Chọn khoa/phòng đang hoạt động.");
  if(input.contractId()!=null&&input.newContract()!=null)invalid("Chỉ chọn một hợp đồng cho thiết bị mới.");
  Long contract=input.contractId();
  if(input.newContract()!=null){var k=input.newContract();if(k.endDate().isBefore(k.startDate()))invalid("Ngày hết hạn hợp đồng phải từ ngày bắt đầu trở đi.");
   if((k.providerId()==null)==(k.newCompany()==null))invalid("Chọn công ty hiện có hoặc nhập công ty mới.");
   String contractCode=code(k.code());lockCode(contractCode);
   if(jdbc.queryForObject("SELECT count(*) FROM maintenance_contract WHERE upper(contract_code)=?",Long.class,contractCode)>0)conflict("CONTRACT_CODE_EXISTS","Mã hợp đồng đã tồn tại.");
   Long provider=k.providerId();
   if(k.newCompany()!=null){var p=k.newCompany();String providerCode=code(p.code());lockCode(providerCode);
    if(jdbc.queryForObject("SELECT count(*) FROM service_provider WHERE upper(code)=?",Long.class,providerCode)>0)conflict("PROVIDER_CODE_EXISTS","Mã công ty đã tồn tại. Hãy chọn công ty hiện có.");
    provider=jdbc.queryForObject("INSERT INTO service_provider(code,name,contact_details) VALUES (?,?,?) RETURNING id",Long.class,providerCode,p.name().trim(),p.contact());
   } else if(jdbc.queryForList("SELECT id FROM service_provider WHERE id=? AND active FOR SHARE",Long.class,provider).isEmpty())invalid("Công ty đã ngừng hoạt động.");
   contract=jdbc.queryForObject("INSERT INTO maintenance_contract(contract_code,contract_name,provider_id,start_date,end_date) VALUES (?,?,?,?,?) RETURNING id",Long.class,contractCode,k.name().trim(),provider,k.startDate(),k.endDate());
  }
  if(contract!=null){var matches=jdbc.queryForList("SELECT c.id,c.start_date FROM maintenance_contract c JOIN service_provider p ON p.id=c.provider_id WHERE c.id=? AND c.active AND p.active FOR SHARE OF c,p",contract);
   if(matches.isEmpty())invalid("Hợp đồng hoặc công ty không còn hoạt động.");
   LocalDate start=((java.sql.Date)matches.get(0).get("start_date")).toLocalDate();
   if(input.warrantyEndDate()!=null&&input.warrantyEndDate().isBefore(start))invalid("Ngày hết bảo hành không được trước ngày bắt đầu hồ sơ hợp đồng.");
  }
  long id=jdbc.queryForObject("INSERT INTO equipment(equipment_code,name,department_id,model,serial_number,maintenance_enabled) VALUES (?,?,?,?,?,false) RETURNING id",Long.class,equipmentCode,input.name().trim(),input.departmentId(),input.model(),input.serialNumber());
  for(var q:input.quarters())jdbc.update("INSERT INTO equipment_maintenance_schedule(equipment_id,quarter) VALUES (?,?)",id,q.name());
  if(contract!=null)jdbc.update("INSERT INTO maintenance_contract_equipment(contract_id,equipment_id) VALUES (?,?)",contract,id);
  if(contract!=null||input.warrantyEndDate()!=null)jdbc.update("INSERT INTO maintenance_coverage(equipment_id,contract_id,classification,verified_by_user_id,verified_at,basis_note,warranty_expires_on,provider_id) VALUES (?,?,?,?,now(),?,?,(SELECT provider_id FROM maintenance_contract WHERE id=?))",id,contract,contract==null?"NOT_FREE":"FREE",current.get().id(),"VTYT nhập hồ sơ thiết bị",input.warrantyEndDate(),contract);
  return catalog.equipment(id);
 }
 @Transactional
 public void remove(long id){vtyt();if(jdbc.queryForList("SELECT id FROM equipment WHERE id=? FOR UPDATE",Long.class,id).isEmpty())throw new ResourceNotFoundException("Equipment");
  // Archive the current catalog entry; plan snapshots, coverage, executions and reports remain intact.
  jdbc.update("UPDATE equipment SET active=false WHERE id=?",id);
 }
}
