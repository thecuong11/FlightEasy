package com.flighteasy.service;

import com.flighteasy.dto.BookingReportRow;
import com.flighteasy.dto.ReportRequest;
import com.flighteasy.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final BookingRepository bookingRepository;

    public byte[] exportRevenueReport (ReportRequest request) throws IOException {
        List<BookingReportRow> data = bookingRepository.findForReport(
                request.fromDate().atStartOfDay(),
                request.toDate().atTime(23, 59, 59)
        );
        return buildExcelFile(data, request);
    }

    private byte[] buildExcelFile(List<BookingReportRow> data, ReportRequest request) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Báo cáo doanh thu");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.index);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.index);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("#,##0"));

            CellStyle altStyle = workbook.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.index);
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {
                    "STT", "Mã PNR", "Ngày đặt", "Chặng bay",
                    "Số HK", "Hạng vé", "Giá vé (VNĐ)", "Phí DV (VNĐ)",
                    "Tổng tiền (VNĐ)", "Trạng thái", "Hãng bay"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < data.size(); i++) {
                BookingReportRow row = data.get(i);
                Row excelRow = sheet.createRow(i + 1);

                excelRow.createCell(0).setCellValue(i + 1);
                excelRow.createCell(1).setCellValue(row.getPnrCode());
                excelRow.createCell(2).setCellValue(row.getBookingDate() != null ? row.getBookingDate().toString() : "");
                excelRow.createCell(3).setCellValue(row.getRoute());
                excelRow.createCell(4).setCellValue(row.getPassengerCount());
                excelRow.createCell(5).setCellValue(row.getClassType());

                createCurrencyCell(excelRow, 6, row.getSubtotal(), currencyStyle);
                createCurrencyCell(excelRow, 7, row.getServiceFee(), currencyStyle);
                createCurrencyCell(excelRow, 8, row.getTotalPrice(), currencyStyle);

                excelRow.createCell(9).setCellValue(row.getStatus());
                excelRow.createCell(10).setCellValue(row.getAirlineName());
            }

            int summaryRowIdx = data.size() + 2;
            Row summaryRow = sheet.createRow(summaryRowIdx);
            summaryRow.createCell(0).setCellValue("TỔNG CỘNG");

            Cell totalCell = summaryRow.createCell(8);
            totalCell.setCellFormula("SUM(I2:I" + (data.size() + 1) + ")");
            totalCell.setCellStyle(currencyStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createCurrencyCell(Row row, int colIdx, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }
}
