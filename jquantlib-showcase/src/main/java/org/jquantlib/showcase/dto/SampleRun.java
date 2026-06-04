package org.jquantlib.showcase.dto;

/** Captured result of running one jquantlib-samples program. */
public record SampleRun(
        String id,
        String title,
        String className,
        String output,
        long elapsedMs,
        boolean ok) {
}
