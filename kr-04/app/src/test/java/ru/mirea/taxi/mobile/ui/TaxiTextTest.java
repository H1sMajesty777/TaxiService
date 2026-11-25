package ru.mirea.taxi.mobile.ui;

import org.junit.Test;
import java.math.BigDecimal;

import static org.junit.Assert.*;

public class TaxiTextTest {
    @Test
    public void requiresPasswordLengthAndCharacterTypes() {
        assertThrows(IllegalArgumentException.class, () -> TaxiText.password("short1"));
        TaxiText.password("ExampleLong9");
    }

    @Test
    public void validatesFareAndAcceptsDecimalComma() {
        assertEquals(new BigDecimal("520.50"), TaxiText.fare("520,50"));
        assertThrows(IllegalArgumentException.class, () -> TaxiText.fare("0.01" + "7"));
    }

    @Test
    public void formatsStatusAndTime() {
        assertEquals("В поездке", TaxiText.status("IN_PROGRESS"));
        assertEquals("2026-11-25 09:30", TaxiText.time("2026-11-25T09:30:00"));
        assertThrows(IllegalArgumentException.class, () -> TaxiText.pickupAt("2026-02-30 09:30"));
    }
}
