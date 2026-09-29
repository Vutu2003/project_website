package vn.edu.medmaintenance.api.dto.response;

import vn.edu.medmaintenance.persistence.enums.PlanStatus;

public record PlanCommandResponse(Long id, PlanStatus status, Integer version, Long approvalRequestId) { }
