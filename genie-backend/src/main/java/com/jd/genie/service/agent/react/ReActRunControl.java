package com.jd.genie.service.agent.react;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation token for long-running ReAct provider calls. */
@Component
public class ReActRunControl {
    private final ConcurrentHashMap<String, Control> controls = new ConcurrentHashMap<>();

    public AtomicBoolean register(String runId, String ownerUserId) {
        Control control = new Control(ownerUserId);
        controls.put(runId, control);
        return control.cancelled;
    }

    public boolean cancel(String runId, String ownerUserId) {
        Control control = controls.get(runId);
        if (control == null || !control.ownerUserId.equals(ownerUserId)) return false;
        return control.cancelled.compareAndSet(false, true);
    }

    public void complete(String runId) { controls.remove(runId); }

    private static final class Control {
        private final String ownerUserId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private Control(String ownerUserId) { this.ownerUserId = ownerUserId; }
    }
}