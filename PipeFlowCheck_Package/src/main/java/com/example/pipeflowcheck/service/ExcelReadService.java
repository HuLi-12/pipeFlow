package com.example.pipeflowcheck.service;

import com.example.pipeflowcheck.enums.ChannelType;
import com.example.pipeflowcheck.enums.NodeType;
import com.example.pipeflowcheck.enums.StartType;
import com.example.pipeflowcheck.model.CheckTask;
import com.example.pipeflowcheck.model.Edge;
import com.example.pipeflowcheck.model.Node;
import com.example.pipeflowcheck.model.PipeNetworkData;

import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExcelReadService {

    private final DataFormatter formatter = new DataFormatter();

    public PipeNetworkData read(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Map<String, Node> nodeMap = readNodes(requiredSheet(workbook, "Nodes"));
            List<Edge> edges = readEdges(requiredSheet(workbook, "Edges"));
            List<CheckTask> tasks = readTasks(requiredSheet(workbook, "Tasks"));
            validateEdgeReferences(nodeMap, edges);
            return new PipeNetworkData(nodeMap, edges, tasks);
        } catch (IOException e) {
            throw new IllegalArgumentException("Excel 读取失败", e);
        }
    }

    private Map<String, Node> readNodes(Sheet sheet) {
        Map<String, Integer> headers = headers(sheet);
        requireHeaders(sheet, headers, "node_id", "node_name", "node_type");

        Map<String, Node> nodes = new LinkedHashMap<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlank(row)) {
                continue;
            }

            Node node = new Node();
            node.setNodeId(requiredValue(sheet, row, headers, "node_id"));
            node.setNodeName(requiredValue(sheet, row, headers, "node_name"));
            node.setNodeType(enumValue(NodeType.class, requiredValue(sheet, row, headers, "node_type"), sheet, row));
            node.setRemark(optionalValue(row, headers, "remark"));
            if (nodes.containsKey(node.getNodeId())) {
                throw new IllegalArgumentException("节点重复：" + node.getNodeId());
            }
            nodes.put(node.getNodeId(), node);
        }
        return nodes;
    }

    private List<Edge> readEdges(Sheet sheet) {
        Map<String, Integer> headers = headers(sheet);
        requireHeaders(sheet, headers, "from_node_id", "to_node_id", "channel_type");

        List<Edge> edges = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlank(row)) {
                continue;
            }

            Edge edge = new Edge();
            edge.setFromNodeId(requiredValue(sheet, row, headers, "from_node_id"));
            edge.setToNodeId(requiredValue(sheet, row, headers, "to_node_id"));
            edge.setChannelType(enumValue(ChannelType.class, requiredValue(sheet, row, headers, "channel_type"), sheet, row));
            edge.setRemark(optionalValue(row, headers, "remark"));
            edges.add(edge);
        }
        return edges;
    }

    private List<CheckTask> readTasks(Sheet sheet) {
        Map<String, Integer> headers = headers(sheet);
        requireHeaders(sheet, headers, "task_id", "start_node_id", "start_type");

        List<CheckTask> tasks = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlank(row)) {
                continue;
            }

            CheckTask task = new CheckTask();
            task.setTaskId(requiredValue(sheet, row, headers, "task_id"));
            task.setStartNodeId(requiredValue(sheet, row, headers, "start_node_id"));
            task.setStartType(enumValue(StartType.class, requiredValue(sheet, row, headers, "start_type"), sheet, row));
            task.setRemark(optionalValue(row, headers, "remark"));
            tasks.add(task);
        }
        return tasks;
    }

    private void validateEdgeReferences(Map<String, Node> nodeMap, List<Edge> edges) {
        Set<String> edgeKeys = new HashSet<>();
        for (Edge edge : edges) {
            if (!nodeMap.containsKey(edge.getFromNodeId())) {
                throw new IllegalArgumentException("边引用节点不存在：" + edge.getFromNodeId());
            }
            if (!nodeMap.containsKey(edge.getToNodeId())) {
                throw new IllegalArgumentException("边引用节点不存在：" + edge.getToNodeId());
            }

            String edgeKey = edge.getFromNodeId() + "->" + edge.getToNodeId() + ":" + edge.getChannelType();
            if (!edgeKeys.add(edgeKey)) {
                throw new IllegalArgumentException("边重复：" + edgeKey);
            }
        }
    }

    private Sheet requiredSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new IllegalArgumentException("缺少 Sheet：" + name);
        }
        return sheet;
    }

    private Map<String, Integer> headers(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new IllegalArgumentException("Sheet " + sheet.getSheetName() + " 缺少表头");
        }

        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : headerRow) {
            String name = formatter.formatCellValue(cell).trim();
            if (!name.isEmpty()) {
                headers.put(name, cell.getColumnIndex());
            }
        }
        return headers;
    }

    private void requireHeaders(Sheet sheet, Map<String, Integer> headers, String... requiredNames) {
        for (String requiredName : requiredNames) {
            if (!headers.containsKey(requiredName)) {
                throw new IllegalArgumentException("Sheet " + sheet.getSheetName() + " 缺少必填列：" + requiredName);
            }
        }
    }

    private String requiredValue(Sheet sheet, Row row, Map<String, Integer> headers, String name) {
        String value = optionalValue(row, headers, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Sheet " + sheet.getSheetName()
                    + " 第 " + (row.getRowNum() + 1) + " 行缺少必填字段：" + name);
        }
        return value;
    }

    private String optionalValue(Row row, Map<String, Integer> headers, String name) {
        Integer columnIndex = headers.get(name);
        if (columnIndex == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(columnIndex)).trim();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, Sheet sheet, Row row) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Sheet " + sheet.getSheetName()
                    + " 第 " + (row.getRowNum() + 1) + " 行枚举值非法：" + value, e);
        }
    }

    private boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
