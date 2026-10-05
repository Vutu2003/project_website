package vn.edu.medmaintenance.service;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
class PeriodicScheduleTest {
 private final LocalDate today=LocalDate.of(2026,10,5);
 @Test void derivesFromLastCompletedMaintenance(){assertThat(PeriodicSchedule.next(true,6,"MONTH",LocalDate.of(2026,4,5),LocalDate.of(2020,1,1))).isEqualTo(today);}
 @Test void usesCommissioningOnlyWithoutHistory(){assertThat(PeriodicSchedule.next(true,12,"MONTH",null,LocalDate.of(2025,10,5))).isEqualTo(today);}
 @Test void respectsMonthEndAndLeapYear(){assertThat(PeriodicSchedule.next(true,1,"MONTH",LocalDate.of(2026,1,31),null)).isEqualTo(LocalDate.of(2026,2,28));assertThat(PeriodicSchedule.next(true,1,"YEAR",LocalDate.of(2024,2,29),null)).isEqualTo(LocalDate.of(2025,2,28));}
 @Test void daysAndMissingSchedule(){assertThat(PeriodicSchedule.next(true,10,"DAY",today,null)).isEqualTo(today.plusDays(10));assertThat(PeriodicSchedule.next(false,6,"MONTH",today,null)).isNull();assertThat(PeriodicSchedule.next(true,6,"MONTH",null,null)).isNull();}
 @Test void allDueStatesAndBoundaries(){assertThat(PeriodicSchedule.status(today.minusDays(1),today,30)).isEqualTo("OVERDUE");assertThat(PeriodicSchedule.status(today,today,30)).isEqualTo("DUE");assertThat(PeriodicSchedule.status(today.plusDays(30),today,30)).isEqualTo("DUE_SOON");assertThat(PeriodicSchedule.status(today.plusDays(31),today,30)).isEqualTo("NOT_DUE");assertThat(PeriodicSchedule.status(null,today,30)).isEqualTo("NO_SCHEDULE");}
}
