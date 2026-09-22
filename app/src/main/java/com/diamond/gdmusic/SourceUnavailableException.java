package com.diamond.gdmusic;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/** A temporary provider failure: skip its remaining candidates in this resolution. */
public final class SourceUnavailableException extends IOException {
    private static final long serialVersionUID = 1L;
    public final String source;
    public interface Listener { void onUnavailable(String source); }
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    public static void addListener(Listener listener) { listeners.add(listener); }
    public static void removeListener(Listener listener) { listeners.remove(listener); }
    public SourceUnavailableException(String source, String reason) {
        super(source + "音源暂时不可用：" + reason);
        this.source = source;
    }
    static SourceUnavailableException report(String source, String reason) {
        SourceUnavailableException error = new SourceUnavailableException(source, reason);
        for (Listener listener : listeners) listener.onUnavailable(source);
        return error;
    }
}
