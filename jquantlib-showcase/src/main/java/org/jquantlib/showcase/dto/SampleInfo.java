package org.jquantlib.showcase.dto;

/** Catalog entry describing one runnable program from the jquantlib-samples project. */
public record SampleInfo(
        String id,
        String className,
        String title,
        String description) {
}
