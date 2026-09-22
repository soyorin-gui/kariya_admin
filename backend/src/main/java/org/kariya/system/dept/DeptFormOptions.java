package org.kariya.system.dept;

import java.util.List;

public record DeptFormOptions(List<Option> departments, List<Option> leaders) {
    public record Option(Long value, String label) {
    }
}
