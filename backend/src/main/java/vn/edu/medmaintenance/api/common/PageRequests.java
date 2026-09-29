package vn.edu.medmaintenance.api.common;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.exception.InvalidParameterException;

public final class PageRequests {
    private PageRequests() { }

    public static PageRequest create(PageQuery query, Set<String> allowedFields,
            String defaultField, Sort.Direction defaultDirection) {
        Integer page = query.getPage(), size = query.getSize();
        if (page == null || page < 0) throw new InvalidParameterException("page", "page must be at least 0");
        if (size == null || size < 1 || size > 100) {
            throw new InvalidParameterException("size", "size must be between 1 and 100");
        }
        String field = defaultField;
        Sort.Direction direction = defaultDirection;
        if (query.getSort() != null) {
            String[] parts = query.getSort().split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new InvalidParameterException("sort", "sort must be field[,asc|desc]");
            }
            field = parts[0].trim();
            if (parts.length == 2) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim());
                } catch (IllegalArgumentException exception) {
                    throw new InvalidParameterException("sort", "sort direction must be asc or desc");
                }
            }
        }
        if (!allowedFields.contains(field)) {
            throw new InvalidParameterException("sort", "sort field is not allowed");
        }
        Sort sort = Sort.by(new Sort.Order(direction, field));
        if (!"id".equals(field)) sort = sort.and(Sort.by("id"));
        return PageRequest.of(page, size, sort);
    }

    public static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new InvalidParameterException(field, field + " must be positive");
        }
    }
}
