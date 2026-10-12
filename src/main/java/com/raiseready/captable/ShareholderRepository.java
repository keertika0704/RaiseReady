package com.raiseready.captable;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShareholderRepository {

    private final JdbcTemplate jdbc;

    public ShareholderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The founder first, then everyone else in the order they came in. */
    public List<Shareholder> findAll() {
        return jdbc.query("SELECT id, name, shares FROM shareholder ORDER BY id",
                (rs, i) -> new Shareholder(rs.getLong("id"), rs.getString("name"), rs.getLong("shares")));
    }

    /**
     * Every share that exists, read while holding a lock on the holders until the surrounding database transaction
     * ends, so two investments saved at the same moment cannot both be sized from the same total.
     */
    public long totalSharesLocked() {
        return jdbc.queryForList("SELECT shares FROM shareholder FOR UPDATE", Long.class).stream()
                .mapToLong(Long::longValue).sum();
    }

    /** Adds shares to the holder with that name (any capitalisation), or creates the holder. */
    public void addShares(String name, long shares) {
        jdbc.update("""
                INSERT INTO shareholder (name, shares) VALUES (?, ?)
                ON CONFLICT (lower(name)) DO UPDATE SET shares = shareholder.shares + EXCLUDED.shares
                """, name, shares);
    }
}
