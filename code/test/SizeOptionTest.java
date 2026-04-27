package tokyoera.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SizeOptionTest {

    @Test
    void displayValue_xs_returnsXS() {
        assertEquals("XS", SizeOption.XS.displayValue());
    }

    @Test
    void displayValue_s_returnsS() {
        assertEquals("S", SizeOption.S.displayValue());
    }

    @Test
    void displayValue_m_returnsM() {
        assertEquals("M", SizeOption.M.displayValue());
    }

    @Test
    void displayValue_l_returnsL() {
        assertEquals("L", SizeOption.L.displayValue());
    }

    @Test
    void displayValue_xl_returnsXL() {
        assertEquals("XL", SizeOption.XL.displayValue());
    }

    @Test
    void displayValue_2x_returns2X() {
        assertEquals("2X", SizeOption._2X.displayValue());
    }

    @Test
    void displayValue_3x_returns3X() {
        assertEquals("3X", SizeOption._3X.displayValue());
    }

    @Test
    void allValues_haveNonBlankDisplayValue() {
        for (SizeOption s : SizeOption.values()) {
            assertFalse(s.displayValue().isBlank(),
                    s.name() + " should have a non-blank displayValue");
        }
    }

    @Test
    void values_containsNineOptions() {
        assertEquals(7, SizeOption.values().length);
    }

    @Test
    void prefixedSizes_doNotContainUnderscore() {
        // 2X, 3X should display without underscores
        assertFalse(SizeOption._2X.displayValue().contains("_"));
        assertFalse(SizeOption._3X.displayValue().contains("_"));
    }
}
