package com.almonium.famsubbe.admin

enum class AdminActionTargetType {
    COST_CALCULATION_RUN,
    INVOICE_GENERATION_RUN,
    INVOICE,
    SUBSCRIBER,
    CHARGE,
    MEMBERSHIP,
    SUBSCRIPTION_SERVICE,
    // Retired: see AdminActionType.PINNED_POST_GENERATED.
    PINNED_POST,
    ACCOUNT,
    ADMIN_INVITE,
    BANK_TRANSACTION,
    BANK_SENDER
}
