package com.diamond.gdmusic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GdMusicApiRecoveryTest {

    @Test
    public void currentSourceIsRetriedBeforeFallbackSources() {
        List<String> sources = new ArrayList<>(
                Arrays.asList("joox", "netease", "bilibili", "kuwo")
        );

        GdMusicApi.prioritizeCurrentSourceForRecovery(sources, "netease");

        assertEquals(
                Arrays.asList("netease", "joox", "bilibili", "kuwo"),
                sources
        );
    }

    @Test
    public void currentSourceIsNotDuplicatedWhenAlreadyFirst() {
        List<String> sources = new ArrayList<>(
                Arrays.asList("netease", "joox", "bilibili")
        );

        GdMusicApi.prioritizeCurrentSourceForRecovery(sources, "netease");

        assertEquals(
                Arrays.asList("netease", "joox", "bilibili"),
                sources
        );
    }
}
