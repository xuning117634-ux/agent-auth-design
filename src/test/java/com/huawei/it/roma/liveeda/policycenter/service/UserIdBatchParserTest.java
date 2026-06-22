package com.huawei.it.roma.liveeda.policycenter.service;

import com.huawei.it.roma.liveeda.policycenter.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdBatchParserTest {

    @Test
    void parsesCommonSeparatorsAndRemovesDuplicates() {
        assertThat(UserIdBatchParser.parse(List.of("z123,c456；d789\nz123")))
                .containsExactly("z123", "c456", "d789");
    }

    @Test
    void rejectsEntriesWithoutValidUserIds() {
        assertThatThrownBy(() -> UserIdBatchParser.parse(List.of(" ,；\n")))
                .isInstanceOf(ApiException.class);
    }
}
