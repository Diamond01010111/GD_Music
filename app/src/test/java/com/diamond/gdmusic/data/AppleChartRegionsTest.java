package com.diamond.gdmusic.data;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AppleChartRegionsTest {
    @Test public void canadaIsAppendedAfterFiveDefaults() {
        assertEquals(Arrays.asList("cn", "jp", "kr", "us", "gb", "ca"), AppleChartRegions.defaults("CA"));
    }
    @Test public void duplicateCountryIsNotAdded() {
        assertEquals(Arrays.asList("cn", "jp", "kr", "us", "gb"), AppleChartRegions.defaults("us"));
    }
    @Test public void missingOrInvalidRegionDoesNotCreateABrokenFeed() {
        assertEquals(5, AppleChartRegions.defaults(null).size());
        assertEquals(5, AppleChartRegions.defaults("").size());
        assertEquals(5, AppleChartRegions.defaults("ZZ").size());
        assertEquals(5, AppleChartRegions.defaults("419").size());
    }
}
