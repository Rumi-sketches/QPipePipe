package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.car.app.connection.CarConnection;

import org.junit.Test;

public class CarConnectionMonitorTest {
    @Test
    public void unknownOrNotConnectedMeansNoCar() {
        assertFalse(CarConnectionMonitor.isConnectedType(null));
        assertFalse(CarConnectionMonitor.isConnectedType(
                CarConnection.CONNECTION_TYPE_NOT_CONNECTED));
    }

    @Test
    public void projectionAndNativeMeanCar() {
        assertTrue(CarConnectionMonitor.isConnectedType(CarConnection.CONNECTION_TYPE_PROJECTION));
        assertTrue(CarConnectionMonitor.isConnectedType(CarConnection.CONNECTION_TYPE_NATIVE));
    }
}
