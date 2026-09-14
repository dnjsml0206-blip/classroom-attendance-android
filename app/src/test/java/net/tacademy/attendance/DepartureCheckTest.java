package net.tacademy.attendance;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DepartureCheckTest {
    @Test public void uncertaintyAndBorderlinePositionsDoNotCountAsLeaving() {
        DepartureCheck d = new DepartureCheck();
        assertFalse(d.update(34,5,30,1000,1000));
        assertFalse(d.update(34,5,30,21000,21000));
        assertFalse(d.update(300,80,30,41000,41000));
        assertFalse(d.update(Float.NaN,5,30,61000,61000));
        assertFalse(d.update(300,Float.POSITIVE_INFINITY,30,81000,81000));
    }
    @Test public void distinctAccurateOutsideFixesMustBeSeparatedByTenSeconds() {
        DepartureCheck d = new DepartureCheck();
        assertFalse(d.update(70,5,30,1000,1000));
        assertFalse(d.update(70,5,30,1000,31000));
        assertFalse(d.update(70,5,30,9000,9000));
        assertTrue(d.update(70,5,30,11000,11000));
    }
    @Test public void ReturningOrInaccurateFixRestartsConfirmation() {
        DepartureCheck d = new DepartureCheck();
        assertFalse(d.update(70,5,30,1000,1000));
        assertFalse(d.update(20,5,30,12000,12000));
        assertFalse(d.update(70,5,30,22000,22000));
        assertFalse(d.update(70,80,30,32000,32000));
        assertFalse(d.update(70,5,30,42000,42000));
        assertTrue(d.update(70,5,30,62000,62000));
    }
    @Test public void OldFutureAndOutOfOrderFixesCannotTrigger() {
        DepartureCheck d = new DepartureCheck();
        assertFalse(d.update(70,5,30,1000,122000));
        assertFalse(d.update(70,5,30,200000,190000));
        assertFalse(d.update(70,5,30,220000,220000));
        assertFalse(d.update(70,5,30,219000,230000));
        assertTrue(d.update(70,5,30,240000,240000));
    }
    @Test public void LongGapsRequireNewConfirmation() {
        DepartureCheck d = new DepartureCheck();
        assertFalse(d.update(70,5,30,1000,1000));
        assertFalse(d.update(70,5,30,180000,180000));
        assertTrue(d.update(70,5,30,200000,200000));
    }
}
