package com.example.pipeflowcheck;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticPageTest {

    @Test
    void pageShowsTemplateDownloadAndFieldRequirements() throws Exception {
        String html = Files.readString(Path.of("src", "main", "resources", "static", "index.html"), StandardCharsets.UTF_8);

        assertTrue(html.contains("下载标准模板"));
        assertTrue(html.contains("/api/pipe-flow/template"));
        assertTrue(html.contains("Nodes"));
        assertTrue(html.contains("Edges"));
        assertTrue(html.contains("Tasks"));
        assertTrue(html.contains("node_id"));
        assertTrue(html.contains("channel_type"));
        assertTrue(html.contains("start_type"));
    }
}
