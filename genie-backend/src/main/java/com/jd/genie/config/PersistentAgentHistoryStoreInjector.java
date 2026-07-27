package com.jd.genie.config;

/**
 * Retained only so old local scripts referencing this source path do not fail.
 * Agent history now receives its dedicated DataSource through AgentPersistenceConfiguration.
 */
@Deprecated
final class PersistentAgentHistoryStoreInjector {
    private PersistentAgentHistoryStoreInjector() { }
}