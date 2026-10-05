package vn.edu.medmaintenance.service;
import java.time.LocalDate;
public final class PeriodicSchedule {
 private PeriodicSchedule(){}
 public static LocalDate next(boolean enabled,Integer value,String unit,LocalDate last,LocalDate commissioning){
  LocalDate base=last==null?commissioning:last;
  if(!enabled || value==null || value<1 || unit==null || base==null)return null;
  return switch(unit){case "DAY" -> base.plusDays(value); case "MONTH" -> base.plusMonths(value); case "YEAR" -> base.plusYears(value); default -> null;};
 }
 public static String status(LocalDate due,LocalDate today,int soonDays){
  if(due==null)return "NO_SCHEDULE";
  if(due.isBefore(today))return "OVERDUE";
  if(due.equals(today))return "DUE";
  return due.isAfter(today.plusDays(soonDays))?"NOT_DUE":"DUE_SOON";
 }
}
