package org.vfeeg.eegfaktura.billing.util;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class StringTools {

    public static String nullSafeJoin(final String separator, String ... s) {
        assert separator!=null;
        return Arrays.stream(s).filter(Objects::nonNull).filter(e->e.trim().length()>0).collect(Collectors.joining(separator));
    }

}
