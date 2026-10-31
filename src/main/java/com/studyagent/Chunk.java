package com.studyagent;

/**
 * A piece of uploaded course material.
 *
 * @param id      unique id, e.g. "lecture3.pdf#4" for the fifth chunk of lecture3.pdf
 * @param content the chunk's text
 * @param source  the file name the chunk came from
 */
public record Chunk(String id, String content, String source) {
}
