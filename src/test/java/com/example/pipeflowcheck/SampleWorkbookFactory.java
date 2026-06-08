package com.example.pipeflowcheck;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

final class SampleWorkbookFactory {

    private SampleWorkbookFactory() {
    }

    static byte[] sampleWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet nodes = workbook.createSheet("Nodes");
            row(nodes, 0, "node_id", "node_name", "node_type", "remark");
            row(nodes, 1, "N001", "Rain inlet A", "RAIN_INLET", "normal rain entrance");
            row(nodes, 2, "N002", "Rain well A1", "RAIN_WELL", "");
            row(nodes, 3, "N003", "River A", "RIVER", "");
            row(nodes, 4, "N004", "Sewage inlet B", "SEWAGE_INLET", "");
            row(nodes, 5, "N005", "WWTP", "WWTP", "");
            row(nodes, 6, "N006", "Combined well C", "COMBINED_WELL", "");
            row(nodes, 7, "N007", "River C", "RIVER", "");
            row(nodes, 8, "N008", "Rain inlet D", "RAIN_INLET", "");
            row(nodes, 9, "N009", "Loop well E", "RAIN_WELL", "");
            row(nodes, 10, "N010", "Loop well F", "RAIN_WELL", "");
            row(nodes, 11, "N011", "Rain outlet G", "RAIN_OUTLET", "");
            row(nodes, 12, "N012", "Sewage well H", "SEWAGE_WELL", "");

            Sheet edges = workbook.createSheet("Edges");
            row(edges, 0, "from_node_id", "to_node_id", "channel_type", "remark");
            row(edges, 1, "N001", "N002", "RAIN", "");
            row(edges, 2, "N002", "N003", "RAIN", "");
            row(edges, 3, "N004", "N005", "SEWAGE", "");
            row(edges, 4, "N004", "N002", "RAIN", "illegal sewage into rain");
            row(edges, 5, "N008", "N012", "COMBINED", "");
            row(edges, 6, "N006", "N005", "COMBINED", "valid polluted rain end");
            row(edges, 7, "N006", "N007", "COMBINED", "invalid polluted rain end");
            row(edges, 8, "N009", "N010", "RAIN", "");
            row(edges, 9, "N010", "N009", "RAIN", "");
            row(edges, 10, "N011", "N003", "RAIN", "");
            row(edges, 11, "N012", "N005", "SEWAGE", "");
            row(edges, 12, "N002", "N011", "RAIN", "second legal rain branch");

            Sheet tasks = workbook.createSheet("Tasks");
            row(tasks, 0, "task_id", "start_node_id", "start_type", "remark");
            row(tasks, 1, "T001", "N001", "RAIN", "rain all legal");
            row(tasks, 2, "T002", "N008", "RAIN", "rain enters combined");
            row(tasks, 3, "T003", "N004", "SEWAGE", "mixed legal and illegal sewage branches");
            row(tasks, 4, "T004", "N006", "RAIN", "special state reaches river");
            row(tasks, 5, "T005", "N009", "RAIN", "cycle");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] workbookWithMissingNodeId() throws Exception {
        byte[] source = sampleWorkbook();
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(source));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.getSheet("Nodes").getRow(1).getCell(0).setBlank();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] workbookWithDuplicateNodeId() throws Exception {
        byte[] source = sampleWorkbook();
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(source));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.getSheet("Nodes").getRow(2).getCell(0).setCellValue("N001");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] workbookWithMissingEdgeTarget() throws Exception {
        byte[] source = sampleWorkbook();
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(source));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.getSheet("Edges").getRow(1).getCell(1).setCellValue("N999");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
