package vn.edu.medmaintenance.api.dto.response;

/** Existing UC06 draft so a VTYT browser can resume after refresh. */
public record VendorProposalDraftResponse(Long id, Long itemId, Integer itemVersion,
        Long providerId, String providerName, String rationale, String warrantyImpactNote) { }
