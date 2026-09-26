package com.sales.smartBusiness.audit;

/** What an audit line points at. Typed so a filter cannot be built on a typo. */
public enum AuditEntity {
    USER,
    ROLE,
    BRANCH,
    COMPANY,
    BANK_ACCOUNT,
    TAX,
    NUMBERING
}
