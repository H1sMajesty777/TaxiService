package ru.mirea.taxi.backend.filter;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.mirea.taxi.exception.BusinessException;

import java.util.Locale;
import java.util.Map;

public final class QueryFilters {
    private QueryFilters() {}

    public static PageRequest page(int page, int size, String sort, String direction, Map<String, String> allowed) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100) {
            throw new BusinessException("Размер страницы — от 1 до 100, номер страницы — от 0 до 100000.");
        }
        String property = allowed.get(sort);
        if (property == null || !("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction))) {
            throw new BusinessException("Выберите поддерживаемый порядок сортировки.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), property).and(Sort.by("id")));
    }

    public static String literalLike(String query) {
        return "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
