package com.sunny.paintfactory.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSortTest {
    @Test
    void dateAndDocumentNumberSortingAreStable() {
        assertThat(DocumentSort.sql("date","asc","d","n","i")).isEqualTo("d ASC,n ASC,i ASC");
        assertThat(DocumentSort.sql("date","desc","d","n","i")).isEqualTo("d DESC,n ASC,i ASC");
        assertThat(DocumentSort.sql("documentNo","asc","d","n","i")).isEqualTo("n ASC,d ASC,i ASC");
        assertThat(DocumentSort.sql("documentNo","desc","d","n","i")).isEqualTo("n DESC,d ASC,i ASC");
    }

    @Test
    void rejectsUnknownInputInsteadOfBuildingSql() {
        assertThatThrownBy(() -> DocumentSort.sql("total","asc","d","n","i")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> DocumentSort.sql("date","drop table","d","n","i")).isInstanceOf(ResponseStatusException.class);
    }
}
