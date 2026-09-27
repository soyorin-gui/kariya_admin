package org.lbl.system.user.vo;

import java.util.List;

public record UserFormOptions(List<DepartmentOption> departments, List<Option> roles) {
    public record DepartmentOption(Long value, String label, Long parentId) {
    }

    public record Option(Long value, String label) {
    }
}
