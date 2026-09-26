package com.sales.smartBusiness.numbering;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * The document type is taken from the path, not the body — there is one sequence per
 * type and it is never created or removed through the API.
 */
@Getter
@Setter
public class NumberingSequenceRequest {

    @NotBlank(message = "Prefix is required")
    @Size(max = 10, message = "Prefix must not exceed 10 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
            message = "Prefix may only contain letters, digits, underscore and hyphen")
    private String prefix;

    @Min(value = 1, message = "Padding must be at least 1")
    @Max(value = 10, message = "Padding cannot exceed 10")
    private int padding;

    private boolean includeYear;

    @Min(value = 1, message = "The next number must be at least 1")
    private long nextValue;

    private boolean active;
}
