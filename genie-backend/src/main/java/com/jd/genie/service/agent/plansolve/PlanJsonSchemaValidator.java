package com.jd.genie.service.agent.plansolve;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-free enforcement for the JSON Schema published in
 * {@code plan-solve/agent-plan.schema.json}. The checks include cross-node
 * constraints (unique identifiers and acyclic dependencies) that JSON Schema
 * alone cannot express.
 */
@Component
public class PlanJsonSchemaValidator {
    private static final Set<String> SKIP_CONDITIONS = Set.of("never", "no_input_image", "previous_failed", "tool_unavailable");

    public PlanValidationResult validate(StructuredAgentPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan == null) return new PlanValidationResult(false, List.of("plan must be an object"));
        if (!"1.0".equals(plan.version())) errors.add("version must be 1.0");
        if (blank(plan.goal()) || plan.goal().length() > 2000) errors.add("goal must contain 1 to 2000 characters");
        if (plan.tasks().isEmpty() || plan.tasks().size() > 8) errors.add("tasks must contain 1 to 8 items");

        Set<String> ids = new HashSet<>();
        for (PlanTaskSpec task : plan.tasks()) {
            if (task == null) { errors.add("tasks must not contain null"); continue; }
            if (task.id() == null || !task.id().matches("^[a-z][a-z0-9-]{1,63}$")) errors.add("task id is invalid: " + task.id());
            else if (!ids.add(task.id())) errors.add("task id is duplicated: " + task.id());
            if (blank(task.title()) || task.title().length() > 160) errors.add("task title is invalid: " + task.id());
            if (task.kind() == null) errors.add("task kind is required: " + task.id());
            if (task.prompt() == null || task.prompt().length() > 8000) errors.add("task prompt is invalid: " + task.id());
            if (task.parallelGroup().length() > 64) errors.add("parallelGroup is too long: " + task.id());
            if (task.maxAttempts() < 1 || task.maxAttempts() > 3) errors.add("maxAttempts must be between 1 and 3: " + task.id());
            if (!SKIP_CONDITIONS.contains(task.skipWhen())) errors.add("skipWhen is unsupported: " + task.id());
            if (task.dependsOn().size() > 7) errors.add("too many dependencies: " + task.id());
            if (task.kind() == PlanTaskKind.HUMAN_CONFIRMATION && blank(task.confirmationMessage())) errors.add("confirmationMessage is required: " + task.id());
        }
        Map<String, PlanTaskSpec> tasks = plan.tasks().stream().filter(task -> task != null && task.id() != null).collect(java.util.stream.Collectors.toMap(PlanTaskSpec::id, task -> task, (left, right) -> left));
        for (PlanTaskSpec task : tasks.values()) {
            for (String dependency : task.dependsOn()) {
                if (!tasks.containsKey(dependency)) errors.add("unknown dependency " + dependency + " for " + task.id());
                if (task.id().equals(dependency)) errors.add("task cannot depend on itself: " + task.id());
            }
        }
        if (errors.isEmpty() && containsCycle(tasks)) errors.add("task dependency graph contains a cycle");
        return errors.isEmpty() ? PlanValidationResult.success() : new PlanValidationResult(false, errors);
    }

    private boolean containsCycle(Map<String, PlanTaskSpec> tasks) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : tasks.keySet()) if (walk(id, tasks, visiting, visited)) return true;
        return false;
    }

    private boolean walk(String id, Map<String, PlanTaskSpec> tasks, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return false;
        if (!visiting.add(id)) return true;
        for (String dependency : tasks.get(id).dependsOn()) if (tasks.containsKey(dependency) && walk(dependency, tasks, visiting, visited)) return true;
        visiting.remove(id);
        visited.add(id);
        return false;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
