package com.sales.smartBusiness.printprofile;

import com.sales.smartBusiness.bankaccount.CompanyBankAccountResponse;
import com.sales.smartBusiness.bankaccount.CompanyBankAccountService;
import com.sales.smartBusiness.company.CompanyResponse;
import com.sales.smartBusiness.company.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintProfileServiceTest {

    @Mock private CompanyService companyService;
    @Mock private CompanyBankAccountService bankAccountService;

    @InjectMocks private PrintProfileService service;

    private static CompanyBankAccountResponse account(String label, boolean shown) {
        CompanyBankAccountResponse account = new CompanyBankAccountResponse();
        account.setLabel(label);
        account.setBankName("BIAT");
        account.setRib("07000000000000000000");
        account.setCurrency("TND");
        account.setShowOnDocuments(shown);
        return account;
    }

    @Test
    @DisplayName("carries the identity, the images and the currency the printed document needs")
    void carriesIdentity() {
        CompanyResponse company = new CompanyResponse();
        company.setName("ABC Distribution");
        company.setTaxId("1234567A/A/M/000");
        company.setAddress("12 Rue de Carthage");
        company.setPostalCode("1002");
        company.setCity("Tunis");
        company.setPhone("+216 71 000 000");
        company.setEmail("contact@abc.tn");
        company.setCurrency("TND");
        company.setLogoDataUri("data:image/png;base64,AA==");
        company.setStampDataUri("data:image/png;base64,AQ==");
        when(companyService.find()).thenReturn(company);
        when(bankAccountService.findAll()).thenReturn(List.of());

        PrintProfileResponse profile = service.find();

        assertThat(profile.getName()).isEqualTo("ABC Distribution");
        assertThat(profile.getTaxId()).isEqualTo("1234567A/A/M/000");
        assertThat(profile.getCity()).isEqualTo("Tunis");
        assertThat(profile.getCurrency()).isEqualTo("TND");
        assertThat(profile.getLogoDataUri()).isEqualTo("data:image/png;base64,AA==");
        assertThat(profile.getStampDataUri()).isEqualTo("data:image/png;base64,AQ==");
        assertThat(profile.getBankAccounts()).isEmpty();
    }

    @Test
    @DisplayName("lists only the bank accounts the company chose to show on its documents")
    void onlyShownBankAccounts() {
        when(companyService.find()).thenReturn(new CompanyResponse());
        when(bankAccountService.findAll()).thenReturn(List.of(account("Main", true), account("Private", false)));

        PrintProfileResponse profile = service.find();

        assertThat(profile.getBankAccounts()).extracting(PrintProfileResponse.BankAccount::getLabel)
                .containsExactly("Main");
        assertThat(profile.getBankAccounts().get(0).getRib()).isEqualTo("07000000000000000000");
    }
}
