package com.sales.smartBusiness.printprofile;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a printed document carries about the company that issues it — nothing more.
 * Read by whoever can see sales or purchase documents, who has no right to read the company
 * settings: this is the printable subset, not the settings themselves.
 */
@Getter
@Setter
public class PrintProfileResponse {

    private String name;
    private String email;
    private String phone;
    private String address;
    private String postalCode;
    private String city;
    /** The matricule fiscal. */
    private String taxId;
    private String currency;

    /** "data:{contentType};base64,...", ready for an img src. Null when none is set. */
    private String logoDataUri;
    private String stampDataUri;

    /** Only the accounts the company chose to show on its documents. */
    private List<BankAccount> bankAccounts = new ArrayList<>();

    @Getter
    @Setter
    public static class BankAccount {
        private String label;
        private String bankName;
        private String rib;
        private String currency;
    }
}
