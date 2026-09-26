package com.sales.smartBusiness.numbering;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How one document type is numbered for one company, e.g. {@code INV-2026-000001}.
 * The counter moves only when a number is actually reserved (at document validation),
 * so a discarded draft leaves no gap.
 */
@Entity
@Table(name = "numbering_sequences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_numbering_sequences_company_type",
                columnNames = {"company_id", "document_type"}))
@Getter
@Setter
@NoArgsConstructor
public class NumberingSequence extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(nullable = false, length = 10)
    private String prefix;

    /** Width of the zero-padded counter. */
    @Column(nullable = false)
    private int padding = 5;

    @Column(name = "include_year", nullable = false)
    private boolean includeYear = true;

    /** The number the next document will get. */
    @Column(name = "next_value", nullable = false)
    private long nextValue = 1;

    /** The year the counter was last used in — drives the reset when the year rolls over. */
    @Column(name = "year_of_last")
    private Integer yearOfLast;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Hands out the next number and moves the counter on. With the year in the number,
     * the counter restarts at 1 when the year rolls over.
     */
    public String allocate(int year) {
        if (includeYear && (yearOfLast == null || yearOfLast != year)) {
            nextValue = 1;
        }
        String number = format(year);
        nextValue++;
        yearOfLast = year;
        return number;
    }

    /** e.g. {@code INV-2026-000001} — uses {@link #nextValue}, so it shows the next number. */
    public String format(int year) {
        StringBuilder builder = new StringBuilder(prefix);
        if (includeYear) {
            builder.append('-').append(year);
        }
        return builder.append('-')
                .append(String.format("%0" + padding + "d", nextValue))
                .toString();
    }
}
