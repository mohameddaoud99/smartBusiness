package com.sales.smartBusiness.printprofile;

import com.sales.smartBusiness.bankaccount.CompanyBankAccountResponse;
import com.sales.smartBusiness.bankaccount.CompanyBankAccountService;
import com.sales.smartBusiness.company.CompanyResponse;
import com.sales.smartBusiness.company.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles what the header, footer and stamp of a printed document need, through the services
 * of the features that own the data — company identity and bank accounts stay written once.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrintProfileService {

    private final CompanyService companyService;
    private final CompanyBankAccountService bankAccountService;

    public PrintProfileResponse find() {
        CompanyResponse company = companyService.find();

        PrintProfileResponse profile = new PrintProfileResponse();
        profile.setName(company.getName());
        profile.setEmail(company.getEmail());
        profile.setPhone(company.getPhone());
        profile.setAddress(company.getAddress());
        profile.setPostalCode(company.getPostalCode());
        profile.setCity(company.getCity());
        profile.setTaxId(company.getTaxId());
        profile.setCurrency(company.getCurrency());
        profile.setLogoDataUri(company.getLogoDataUri());
        profile.setStampDataUri(company.getStampDataUri());

        bankAccountService.findAll().stream()
                .filter(CompanyBankAccountResponse::isShowOnDocuments)
                .map(PrintProfileService::toBankAccount)
                .forEach(profile.getBankAccounts()::add);
        return profile;
    }

    private static PrintProfileResponse.BankAccount toBankAccount(CompanyBankAccountResponse account) {
        PrintProfileResponse.BankAccount bankAccount = new PrintProfileResponse.BankAccount();
        bankAccount.setLabel(account.getLabel());
        bankAccount.setBankName(account.getBankName());
        bankAccount.setRib(account.getRib());
        bankAccount.setCurrency(account.getCurrency());
        return bankAccount;
    }
}
