package vn.edu.medmaintenance.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;

final class CatalogRules {
    private CatalogRules() { }
    static String required(String input, String code, String label) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank() || value.length() > 100)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, code, label + " bắt buộc, tối đa 100 ký tự.");
        return value;
    }
    static String optional(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.length() > 500)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_SERVICE_PROVIDER", "Liên hệ tối đa 500 ký tự.");
        return value.isEmpty() ? null : value;
    }
    static String search(String input) {
        String value = input == null ? "" : input.trim();
        if (value.length() > 100)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Từ khóa tối đa 100 ký tự.");
        return value;
    }
    static String pattern(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
