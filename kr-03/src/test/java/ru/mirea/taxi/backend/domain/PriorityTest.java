package ru.mirea.taxi.backend.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityTest {
    @Test
    void appliesSeatRuleAndFareBoundaries() {
        assertEquals(Priority.HIGH, Priority.calculate(true, new BigDecimal("100.00")));
        assertEquals(Priority.LOW, Priority.calculate(false, new BigDecimal("749.99")));
        assertEquals(Priority.NORMAL, Priority.calculate(false, new BigDecimal("750.00")));
        assertEquals(Priority.HIGH, Priority.calculate(false, new BigDecimal("1200.00")));
    }
}
