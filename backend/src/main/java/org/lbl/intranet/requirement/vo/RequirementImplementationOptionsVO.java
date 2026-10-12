package org.lbl.intranet.requirement.vo;

import java.util.List;

public record RequirementImplementationOptionsVO(List<SelectionOptionVO> transactions,
                                                 List<SelectionOptionVO> developers,
                                                 Integer activatedVersionNo) { }
