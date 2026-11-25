package ru.mirea.taxi.backend.filter;

import org.junit.jupiter.api.Test;
import ru.mirea.taxi.exception.BusinessException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryFiltersTest {
    @Test
    void textSearchKeepsSqlWildcardsLiteral() {
        assertEquals("%маршрут \\%\\_\\\\%", QueryFilters.literalLike("   МАРШРУТ %_\\  "));
    }

    @Test
    void boundsPageAndAcceptsOnlyKnownSortFields() {
        var allowed = Map.of("fare", "fare");
        assertEquals(20, QueryFilters.page(0, 20, "fare", "asc", allowed).getPageSize());
        assertThrows(BusinessException.class, () -> QueryFilters.page(0, 101, "fare", "asc", allowed));
        assertThrows(BusinessException.class, () -> QueryFilters.page(0, 20, "unknown", "asc", allowed));
    }
}
