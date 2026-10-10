package org.vfeeg.eegfaktura.billing.contract;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.vfeeg.eegfaktura.billing.domain.BillingMasterdata;
import org.vfeeg.eegfaktura.billing.domain.MeteringPointType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The column contract of {@code base.billing_masterdata} (M6): which columns the entity {@link
 * BillingMasterdata} reads, which the database view offers, and what does not fit. Billing reads the
 * view through {@code @Subselect}, which {@code ddl-auto=validate} never checks.
 */
final class MasterdataColumns {

    /** PostgreSQL {@code information_schema.columns.data_type} values each Java type can read. */
    private static final Map<Class<?>, Set<String>> READABLE = Map.of(
            String.class, Set.of("text", "character varying", "uuid"),
            BigDecimal.class, Set.of("numeric", "integer", "smallint", "bigint", "double precision", "real"),
            Integer.class, Set.of("integer", "smallint"),
            Boolean.class, Set.of("boolean"),
            LocalDate.class, Set.of("date"),
            // no @Enumerated: Hibernate maps the enum by ordinal (PRODUCER = 0, CONSUMER = 1)
            MeteringPointType.class, Set.of("integer", "smallint"));

    private static final CamelCaseToUnderscoresNamingStrategy NAMING = new CamelCaseToUnderscoresNamingStrategy();

    private MasterdataColumns() {
    }

    /** Column name → Java type of every persistent field of the entity except the generated {@code id}. */
    static Map<String, Class<?>> entityColumns() {
        Map<String, Class<?>> columns = new TreeMap<>();
        for (Field field : BillingMasterdata.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("id")) {
                continue;
            }
            columns.put(columnName(field.getName()), field.getType());
        }
        return columns;
    }

    /** The physical column name, through the naming strategy Spring Boot configures for Hibernate. */
    static String columnName(String fieldName) {
        return NAMING.toPhysicalColumnName(Identifier.toIdentifier(fieldName), null).getText();
    }

    /** Column name → {@code data_type} of a relation in the database, in column order. */
    static Map<String, String> databaseColumns(Connection connection, String schema, String relation)
            throws SQLException {
        Map<String, String> columns = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select column_name, data_type from information_schema.columns "
                        + "where table_schema = ? and table_name = ? order by ordinal_position")) {
            statement.setString(1, schema);
            statement.setString(2, relation);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.put(rows.getString(1), rows.getString(2));
                }
            }
        }
        return columns;
    }

    /**
     * Every column the entity reads that the view lacks or offers with a type the field cannot take;
     * one line per problem, naming the column and the entity field. Empty = the contract holds.
     */
    static List<String> problems(Map<String, Class<?>> entity, Map<String, String> view) {
        List<String> problems = new ArrayList<>();
        entity.forEach((column, type) -> {
            String dataType = view.get(column);
            if (dataType == null) {
                problems.add("missing column " + column + " (" + type.getSimpleName() + " field of BillingMasterdata)");
            } else if (!READABLE.getOrDefault(type, Set.of()).contains(dataType)) {
                problems.add("column " + column + " is " + dataType + ", the " + type.getSimpleName()
                        + " field of BillingMasterdata reads " + READABLE.getOrDefault(type, Set.of()));
            }
        });
        return problems;
    }

    private static final Pattern VIEW_START = Pattern.compile("CREATE VIEW billing_masterdata_v3\\b");
    private static final Pattern VIEW_END = Pattern.compile("(?m)^\\s*FROM v3\\.participant\\b");
    private static final Pattern ALIAS = Pattern.compile("(?im)\\bAS\\s+([a-z_0-9]+)\\s*,?\\s*$");

    /**
     * The column names of v3's {@code billing_masterdata_v3} from the copied migration file: the
     * {@code AS} aliases of its select list (every column has one). Names only — v3's schema is not
     * applied here.
     */
    static Set<String> v3ViewColumnNames(String resource) throws IOException {
        String sql;
        try (InputStream in = MasterdataColumns.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource " + resource);
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher start = VIEW_START.matcher(sql);
        Matcher end = VIEW_END.matcher(sql);
        if (!start.find() || !end.find(start.end())) {
            throw new IllegalStateException("no select list of billing_masterdata_v3 in " + resource);
        }
        Set<String> names = new TreeSet<>();
        Matcher alias = ALIAS.matcher(sql.substring(start.end(), end.start()));
        while (alias.find()) {
            names.add(alias.group(1).toLowerCase());
        }
        return names;
    }

    /** Names in {@code required} that {@code offered} lacks, sorted. */
    static Set<String> missing(Iterable<String> required, Set<String> offered) {
        Set<String> missing = new TreeSet<>();
        required.forEach(name -> {
            if (!offered.contains(name)) {
                missing.add(name);
            }
        });
        return missing;
    }
}
