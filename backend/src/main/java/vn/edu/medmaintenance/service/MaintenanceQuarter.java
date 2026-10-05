package vn.edu.medmaintenance.service;
import java.time.LocalDate;
public enum MaintenanceQuarter {
 Q1(1,"I"),Q2(4,"II"),Q3(7,"III"),Q4(10,"IV");
 private final int month; private final String roman;
 MaintenanceQuarter(int month,String roman){this.month=month;this.roman=roman;}
 public LocalDate start(int year){return LocalDate.of(year,month,1);}
 public LocalDate end(int year){return start(year).plusMonths(3).minusDays(1);}
 public String title(int year){return "Kế hoạch bảo trì Quý "+roman+" năm "+year;}
 // All periodic contract decisions use the quarter start, including edits and submission.
 public LocalDate referenceDate(int year){return start(year);}
}
