package com.jd.genie.service.agent.plansolve;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryMergeAgentTest {

    @Test
    void mergesOnlyPersistedTaskFactsAndAssetIds() {
        SharedTaskMemoryStore store = mock(SharedTaskMemoryStore.class);
        when(store.list("run-1")).thenReturn(List.of(
                new SharedTaskMemoryItem("ocr", "OCR", "COMPLETE", 1, Map.of(), Map.of("text", "hello"), List.of("asset-source"), "", "OCR completed"),
                new SharedTaskMemoryItem("edit", "IMAGE_EDIT", "FAILED", 2, Map.of(), Map.of(), List.of(), "provider timeout", "provider timeout")));

        MergedTaskContext merged = new MemoryMergeAgent(store).merge("run-1");

        assertEquals(List.of("asset-source"), merged.assetIds());
        assertEquals(List.of("ocr: OCR completed"), merged.completedSummaries());
        assertEquals(List.of("edit: provider timeout"), merged.failures());
        assertTrue(merged.summaryInput().contains("traceableTasks=[ocr, edit]"));
    }
}