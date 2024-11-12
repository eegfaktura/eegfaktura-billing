package org.vfeeg.eegfaktura.billing.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * Clearing period identifiers have the following form:
 * Identifier := "Abr_"(Period-Type)"-"(Year)"-"(Period)
 * (Period-Type) := Y | H | Q | M
 * (Year) := Year in yyyy format
 * (Period) := when Type = Y ... empty, when H = 1 | 2, when Q = (1-4), when M = (1-12)
 * Examples:
 * - "Abr_Y-2024"
 * - "Abr_YH-2024-2"
 * - "Abr_YQ-2024-3"
 * - "Abr_YM-2024-11"
 */
 public class ClearingPeriodIdentifierTool {

    private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final static DateTimeFormatter defaultDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public static String asText(String clearingPeriodIdentifier, DateTimeFormatter textDateTimeFormatter) {
        // if string ist null or empty or too small return empty string
        if (textDateTimeFormatter == null
                || clearingPeriodIdentifier == null
                || clearingPeriodIdentifier.isEmpty()) {
            return clearingPeriodIdentifier;
        }
        // split string into tokens using "-" or "_" as delimiter
        String[] tokens = clearingPeriodIdentifier.split("[-_]");
        if (tokens.length < 3) {
            return clearingPeriodIdentifier;
        }

        String periodType = tokens[1];
        String year = tokens[2];

        // check if year is a valid calendar year
        if (!year.matches("\\d{4}")) {
            return clearingPeriodIdentifier;
        }

        LocalDate startDate;
        LocalDate endDate;

        // depending on periodType (Y, YH, YQ or YM) we calculate start and end date from year and period
        switch (periodType) {
            case "Y" -> {
                // create a date from String in format "yyyyMMdd"
                startDate = LocalDate.parse(year+"0101", dateTimeFormatter);
                endDate = startDate.with(TemporalAdjusters.lastDayOfYear());
            }
            case "YH" -> {
                String period = tokens.length > 3 ? tokens[3] : "";
                switch (period) {
                    case "1" -> {
                        startDate = LocalDate.parse(year + "0101", dateTimeFormatter);
                        endDate = LocalDate.parse(year + "0630", dateTimeFormatter);
                    }
                    case "2" -> {
                        startDate = LocalDate.parse(year + "0701", dateTimeFormatter);
                        endDate = LocalDate.parse(year + "1231", dateTimeFormatter);
                    }
                    default -> {
                        return clearingPeriodIdentifier;
                    }
                }
            }
            case "YQ" -> {
                String period = tokens.length > 3 ? tokens[3] : "";
                switch (period) {
                    case "1" -> {
                        startDate = LocalDate.parse(year + "0101", dateTimeFormatter);
                        endDate = LocalDate.parse(year + "0331", dateTimeFormatter);
                    }
                    case "2" -> {
                        startDate = LocalDate.parse(year + "0401", dateTimeFormatter);
                        endDate = LocalDate.parse(year + "0630", dateTimeFormatter);
                    }
                    case "3" -> {
                        startDate = LocalDate.parse(year + "0701", dateTimeFormatter);
                        endDate = LocalDate.parse(year + "0930", dateTimeFormatter);
                    }
                    case "4" -> {
                        startDate = LocalDate.parse(year + "1001", dateTimeFormatter);
                        endDate = LocalDate.parse(year + "1231", dateTimeFormatter);
                    }
                    default -> {
                        return clearingPeriodIdentifier;
                    }
                }
            }
            case "YM" -> {
                String period = tokens.length > 3 ? tokens[3] : "";
                if (!period.matches("[1-9]|1[0-2]")) {
                    return clearingPeriodIdentifier;
                }
                startDate = LocalDate.parse(year + (period.length()==1?"0":"") + period + "01", dateTimeFormatter);
                endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
            }
            default -> {
                return clearingPeriodIdentifier;
            }
        }

        return textDateTimeFormatter.format(startDate) + " - " + textDateTimeFormatter.format(endDate);

    }

    public static String asText(String clearingPeriodIdentifier) {
        return asText(clearingPeriodIdentifier, defaultDateTimeFormatter);
    }

}
