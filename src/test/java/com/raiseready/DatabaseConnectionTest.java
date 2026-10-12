package com.raiseready;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void connectsToPostgres() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("raiseready2");
    }
}
