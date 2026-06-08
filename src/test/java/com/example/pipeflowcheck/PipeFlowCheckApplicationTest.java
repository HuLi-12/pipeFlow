package com.example.pipeflowcheck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipeFlowCheckApplicationTest {

    @Test
    void exposesApplicationClassForSpringBootStartup() {
        assertEquals("com.example.pipeflowcheck.PipeFlowCheckApplication",
                PipeFlowCheckApplication.class.getName());
    }
}
