package vn.edu.medmaintenance.api.dto.request;

/** Operational updates; these do not replace the item workflow state. */
public enum MaintenanceProgressStatus {
    IN_PROGRESS("Đang bảo trì"),
    PAUSED("Tạm dừng"),
    WAITING_PARTS("Chờ linh kiện"),
    WAITING_PROVIDER("Chờ đơn vị bảo trì"),
    WORK_DONE("Bảo trì xong"),
    DAMAGE_DETECTED("Có hỏng hóc");

    private final String label;
    MaintenanceProgressStatus(String label) { this.label = label; }
    public String label() { return label; }
}
