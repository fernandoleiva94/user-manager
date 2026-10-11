package com.sevenb.user_manager.client;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body de {@code POST /internal/v1/tenants/{tenantId}/trial} de centauro-billing.
 * No incluye {@code planCode}: billing aplica su plan de trial por defecto.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BillingTrialRequest(String companyName, String billingEmail, String source) {

    public BillingTrialRequest withoutBillingEmail() {
        return new BillingTrialRequest(companyName, null, source);
    }
}
