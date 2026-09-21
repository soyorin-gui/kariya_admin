package org.kariya.system.user;

import java.util.List;

public record UserFormOptions(List<Option> departments, List<Option> roles) {
    public record Option(Long value, String label) {
    }
}
