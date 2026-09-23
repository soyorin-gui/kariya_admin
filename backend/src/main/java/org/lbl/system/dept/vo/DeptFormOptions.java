package org.lbl.system.dept.vo;

import java.util.List;

public record DeptFormOptions(List<Option> departments, List<Option> leaders, boolean canCreate) {
    public record Option(Long value, String label) {
    }
}
