package org.vfeeg.eegfaktura.billing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.vfeeg.eegfaktura.billing.config.JacksonConfig;
import org.vfeeg.eegfaktura.billing.model.BillingConfigDTO;
import org.vfeeg.eegfaktura.billing.model.BillingRunDTO;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * The responses the callers read (M6, optional part 3): each DTO, filled in every field and written by
 * the ObjectMapper of production ({@link JacksonConfig}), has exactly the JSON fields of its snapshot
 * in {@code contracts/responses/}, and every field a caller reads ({@code readBy}) is among them. A
 * renamed or dropped field fails here; a new field needs a conscious snapshot update.
 */
@JsonTest
@Import(JacksonConfig.class)
class CallerResponseContractTests {

    static final String MODEL = "org.vfeeg.eegfaktura.billing.model.";

    @Autowired
    ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"DoBillingResults", "BillingRunDTO", "ParticipantAmount", "BillingDocumentDTO",
            "BillingDocumentFileDTO", "BillingConfigDTO", "ErrorResponse"})
    void fieldsMatchTheSnapshotAndCoverWhatTheCallersRead(String dto) throws Exception {
        JsonNode snapshot = ContractFixtures.read("responses/" + dto + ".json");
        Set<String> written = fieldPaths(mapper.valueToTree(filled(Class.forName(MODEL + dto))));
        assertThat(dto + " snapshot", written, is(texts(snapshot.get("fields"))));
        snapshot.get("readBy").fields().forEachRemaining(reader -> {
            Set<String> unread = texts(reader.getValue().get("fields"));
            unread.removeAll(written);
            assertThat(dto + " fields read by " + reader.getKey() + " but not written", unread, is(empty()));
        });
    }

    /** Both callers parse the times as ISO strings ({@code WRITE_DATES_AS_TIMESTAMPS} off). */
    @Test
    void datesAreIsoStrings() {
        BillingRunDTO run = new BillingRunDTO();
        run.setRunStatusDateTime(LocalDateTime.of(2024, 6, 28, 10, 15, 30));
        assertThat(mapper.valueToTree(run).get("runStatusDateTime").asText(), is("2024-06-28T10:15:30"));
    }

    /** Lombok's {@code isCreateCreditNotesForAllProducers} field is the JSON key without {@code is}. */
    @Test
    void creditNoteFlagIsWrittenWithoutIs() {
        BillingConfigDTO config = new BillingConfigDTO();
        config.setCreateCreditNotesForAllProducers(true);
        JsonNode json = mapper.valueToTree(config);
        assertThat(json.get("createCreditNotesForAllProducers").asBoolean(), is(true));
        assertThat(json.has("isCreateCreditNotesForAllProducers"), is(false));
    }

    /** Field paths of a JSON tree; arrays of objects as {@code name[].field}. */
    static Set<String> fieldPaths(JsonNode node) {
        Set<String> paths = new TreeSet<>();
        collect(node, "", paths);
        return paths;
    }

    private static void collect(JsonNode node, String prefix, Set<String> paths) {
        node.fields().forEachRemaining(field -> {
            JsonNode value = field.getValue();
            if (value.isArray() && !value.isEmpty() && value.get(0).isObject()) {
                collect(value.get(0), prefix + field.getKey() + "[].", paths);
            } else {
                paths.add(prefix + field.getKey());
            }
        });
    }

    private static Set<String> texts(JsonNode array) {
        Set<String> texts = new TreeSet<>();
        array.forEach(value -> texts.add(value.asText()));
        return texts;
    }

    /** An instance with every field set to a non-null value; lists hold one filled element. */
    static Object filled(Class<?> type) throws Exception {
        Object instance = type.getDeclaredConstructor().newInstance();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            field.set(instance, value(field.getGenericType()));
        }
        return instance;
    }

    private static Object value(Type type) throws Exception {
        if (type instanceof ParameterizedType list && list.getRawType() == List.class) {
            return List.of(value(list.getActualTypeArguments()[0]));
        }
        Class<?> raw = (Class<?>) type;
        if (raw == String.class) {
            return "x";
        } else if (raw == UUID.class) {
            return UUID.fromString("00000000-0000-0000-0000-00000000a001");
        } else if (raw == BigDecimal.class) {
            return new BigDecimal("1.50");
        } else if (raw == Integer.class || raw == int.class) {
            return 1;
        } else if (raw == Long.class || raw == long.class) {
            return 1L;
        } else if (raw == Boolean.class || raw == boolean.class) {
            return true;
        } else if (raw == LocalDate.class) {
            return LocalDate.of(2024, 6, 28);
        } else if (raw == LocalDateTime.class) {
            return LocalDateTime.of(2024, 6, 28, 10, 15, 30);
        } else if (raw.isEnum()) {
            return raw.getEnumConstants()[0];
        }
        return filled(raw);
    }
}
