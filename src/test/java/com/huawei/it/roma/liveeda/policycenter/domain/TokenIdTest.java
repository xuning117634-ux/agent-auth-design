package com.huawei.it.roma.liveeda.policycenter.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenIdTest {

    @Test
    void parsesValidTokenId() {
        TokenId tokenId = TokenId.parse("agent-a:user-42:conversation-99");

        assertThat(tokenId.raw()).isEqualTo("agent-a:user-42:conversation-99");
        assertThat(tokenId.agentId()).isEqualTo("agent-a");
        assertThat(tokenId.userId()).isEqualTo("user-42");
        assertThat(tokenId.conversationId()).isEqualTo("conversation-99");
    }

    @Test
    void rejectsTokenIdWithWrongSegmentCount() {
        assertThatThrownBy(() -> TokenId.parse("agent-a:user-42"))
                .isInstanceOf(InvalidTokenIdException.class);
    }

    @Test
    void rejectsTokenIdWithBlankSegment() {
        assertThatThrownBy(() -> TokenId.parse("agent-a::conversation-99"))
                .isInstanceOf(InvalidTokenIdException.class);
    }
}
