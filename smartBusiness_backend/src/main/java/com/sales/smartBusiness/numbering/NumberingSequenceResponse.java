package com.sales.smartBusiness.numbering;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NumberingSequenceResponse {

    private DocumentType documentType;
    private String documentTypeLabel;
    private String prefix;
    private int padding;
    private boolean includeYear;
    private long nextValue;
    private boolean active;
    /** What the next document's number will look like, at the current year. */
    private String preview;
    private LocalDateTime updatedAt;
}
