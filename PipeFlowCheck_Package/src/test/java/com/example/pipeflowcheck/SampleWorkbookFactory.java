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

    static byte[] workbookWithStartTypeMismatch() throws Exception {
        byte[] source = sampleWorkbook();
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(source));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // T002: keep start_node_id = N008 (RAIN_INLET) but make start_type incompatible on purpose.
            workbook.getSheet("Tasks").getRow(2).getCell(2).setCellValue("SEWAGE");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] multiScenarioWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet nodes = workbook.createSheet("Nodes");
            row(nodes, 0, "node_id", "node_name", "node_type", "remark");
            row(nodes, 1, "M001", "Rain inlet multi", "RAIN_INLET", "valid rain multi-branch");
            row(nodes, 2, "M002", "Rain well multi", "RAIN_WELL", "");
            row(nodes, 3, "M003", "River multi", "RIVER", "");
            row(nodes, 4, "M004", "Rain outlet multi", "RAIN_OUTLET", "");
            row(nodes, 5, "M005", "Sewage inlet valid", "SEWAGE_INLET", "");
            row(nodes, 6, "M006", "Sewage well valid", "SEWAGE_WELL", "");
            row(nodes, 7, "M007", "WWTP main", "WWTP", "");
            row(nodes, 8, "M008", "Rain inlet combined", "RAIN_INLET", "");
            row(nodes, 9, "M009", "Combined well mixed", "COMBINED_WELL", "");
            row(nodes, 10, "M010", "River after combined", "RIVER", "");
            row(nodes, 11, "M011", "Dead rain well", "RAIN_WELL", "");
            row(nodes, 12, "M012", "Loop well A", "RAIN_WELL", "");
            row(nodes, 13, "M013", "Loop well B", "RAIN_WELL", "");
            row(nodes, 14, "M014", "Terminal with downstream", "RAIN_OUTLET", "");
            row(nodes, 15, "M015", "River after outlet", "RIVER", "");
            row(nodes, 16, "M016", "Life sewage inlet", "LIFE_SEWAGE_INLET", "");
            row(nodes, 17, "M017", "Life sewage well", "SEWAGE_WELL", "");
            row(nodes, 18, "M018", "Lake valid", "LAKE", "");
            row(nodes, 19, "M019", "Rain inlet lake", "RAIN_INLET", "");
            row(nodes, 20, "M020", "Rain well lake", "RAIN_WELL", "");
            row(nodes, 21, "M021", "Sewage inlet illegal channel", "SEWAGE_INLET", "");
            row(nodes, 22, "M022", "Orphan normal node", "NORMAL", "not used by any task");
            row(nodes, 23, "M023", "Rain inlet terminal check", "RAIN_INLET", "");

            Sheet edges = workbook.createSheet("Edges");
            row(edges, 0, "from_node_id", "to_node_id", "channel_type", "remark");
            row(edges, 1, "M001", "M002", "RAIN", "");
            row(edges, 2, "M002", "M003", "RAIN", "valid rain to river");
            row(edges, 3, "M002", "M004", "RAIN", "valid rain to outlet");
            row(edges, 4, "M005", "M006", "SEWAGE", "");
            row(edges, 5, "M006", "M007", "SEWAGE", "valid sewage to WWTP");
            row(edges, 6, "M008", "M009", "COMBINED", "rain enters combined");
            row(edges, 7, "M009", "M007", "COMBINED", "combined to WWTP");
            row(edges, 8, "M009", "M010", "COMBINED", "combined to river invalid");
            row(edges, 9, "M012", "M013", "RAIN", "");
            row(edges, 10, "M013", "M012", "RAIN", "cycle");
            row(edges, 11, "M014", "M015", "RAIN", "terminal has downstream");
            row(edges, 12, "M016", "M017", "LIFE_SEWAGE", "");
            row(edges, 13, "M017", "M007", "SEWAGE", "life sewage to WWTP");
            row(edges, 14, "M019", "M020", "RAIN", "");
            row(edges, 15, "M020", "M018", "RAIN", "valid rain to lake");
            row(edges, 16, "M021", "M002", "RAIN", "illegal sewage to rain channel");
            row(edges, 17, "M023", "M014", "RAIN", "rain reaches outlet that still has downstream");

            Sheet tasks = workbook.createSheet("Tasks");
            row(tasks, 0, "task_id", "start_node_id", "start_type", "remark");
            row(tasks, 1, "MS001", "M001", "RAIN", "rain all branches legal");
            row(tasks, 2, "MS002", "M005", "SEWAGE", "sewage legal");
            row(tasks, 3, "MS003", "M008", "RAIN", "rain enters combined then mixed endpoints");
            row(tasks, 4, "MS004", "M021", "SEWAGE", "sewage enters rain channel");
            row(tasks, 5, "MS005", "M011", "RAIN", "dead end");
            row(tasks, 6, "MS006", "M012", "RAIN", "cycle");
            row(tasks, 7, "MS007", "M999", "RAIN", "missing start node");
            row(tasks, 8, "MS008", "M001", "SEWAGE", "start type mismatch");
            row(tasks, 9, "MS009", "M023", "RAIN", "terminal node has downstream");
            row(tasks, 10, "MS010", "M016", "LIFE_SEWAGE", "life sewage legal");
            row(tasks, 11, "MS011", "M019", "RAIN", "rain to lake legal");
            row(tasks, 12, "MS012", "M009", "RAIN", "start at combined well");

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
