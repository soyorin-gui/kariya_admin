package org.lbl.intranet.requirement.vo;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record RequirementListVO(Long id, String oaNo, Integer requirementType, String title, String description, LocalDate expectedOnlineDate,
                                Long transactionId, String transactionCode, Integer status, Long developerUserId,
                                String developerName, Long createdBy, LocalDateTime createdTime,
                                LocalDateTime updatedTime) { }
