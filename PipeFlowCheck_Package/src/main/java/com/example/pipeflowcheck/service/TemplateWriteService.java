package com.example.pipeflowcheck.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class TemplateWriteService {

    public byte[] write() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeSheet(workbook.createSheet("Nodes"), "node_id", "node_name", "node_type", "remark");
            writeSheet(workbook.createSheet("Edges"), "from_node_id", "to_node_id", "channel_type", "remark");
            writeSheet(workbook.createSheet("Tasks"), "task_id", "start_node_id", "start_type", "remark");
            writeTemplateInfo(workbook.createSheet("Template"));
            for (Sheet sheet : workbook) {
                autoSize(sheet);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("模板 Excel 生成失败", e);
        }
    }

    private void writeSheet(Sheet sheet, String... headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    private void writeTemplateInfo(Sheet sheet) {
        row(sheet, 0, "sheet", "required_columns", "optional_columns", "allowed_values");
        row(sheet, 1, "Nodes", "node_id,node_name,node_type", "remark",
                "node_type: RAIN_INLET,SEWAGE_INLET,LIFE_SEWAGE_INLET,RAIN_WELL,SEWAGE_WELL,COMBINED_WELL,RAIN_OUTLET,RIVER,LAKE,WWTP,NORMAL");
        row(sheet, 2, "Edges", "from_node_id,to_node_id,channel_type", "remark",
                "channel_type: RAIN,SEWAGE,COMBINED,LIFE_SEWAGE,CUSTOM");
        row(sheet, 3, "Tasks", "task_id,start_node_id,start_type", "remark",
                "start_type: RAIN,SEWAGE,LIFE_SEWAGE,CUSTOM");
    }

    private void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private void autoSize(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            return;
        }
        for (int i = 0; i < header.getLastCellNum(); i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
