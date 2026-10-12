package com.raiseready.transactions;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepository {

    private static final RowMapper<Transaction> MAPPER = (rs, i) -> new Transaction(
            rs.getLong("id"),
            TransactionType.valueOf(rs.getString("type")),
            TransactionCategory.valueOf(rs.getString("category")),
            rs.getDate("occurred_on").toLocalDate(),
            rs.getBigDecimal("amount_inr"),
            rs.getString("description"),
            rs.getString("investor_name"),
            rs.getBigDecimal("equity_pct"),
            rs.getBigDecimal("pre_money_valuation_inr"),
            (Long) rs.getObject("shares_issued"),
            rs.getBoolean("recurring"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Saves the row and returns it as stored. The database sets the id and created time. */
    public Transaction insert(TransactionType type, TransactionCategory category, LocalDate occurredOn, BigDecimal amountInr,
                              String description, String investorName, BigDecimal equityPct,
                              BigDecimal preMoneyValuationInr, Long sharesIssued, boolean recurring) {
        return jdbc.queryForObject("""
                INSERT INTO transactions (type, category, occurred_on, amount_inr, description, investor_name, equity_pct,
                                          pre_money_valuation_inr, shares_issued, recurring)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING *
                """, MAPPER, type.name(), category.name(), Date.valueOf(occurredOn), amountInr, description, investorName, equityPct,
                preMoneyValuationInr, sharesIssued, recurring);
    }

    public Optional<Transaction> findById(long id) {
        return jdbc.query("SELECT * FROM transactions WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /** Overwrites the editable columns of one row and returns it as stored; empty if there is no such row. */
    public Optional<Transaction> update(long id, TransactionType type, TransactionCategory category, LocalDate occurredOn,
                                        BigDecimal amountInr, String description, String investorName, BigDecimal equityPct,
                                        BigDecimal preMoneyValuationInr, Long sharesIssued, boolean recurring) {
        return jdbc.query("""
                UPDATE transactions
                SET type = ?, category = ?, occurred_on = ?, amount_inr = ?, description = ?, investor_name = ?, equity_pct = ?,
                    pre_money_valuation_inr = ?, shares_issued = ?, recurring = ?
                WHERE id = ?
                RETURNING *
                """, MAPPER, type.name(), category.name(), Date.valueOf(occurredOn), amountInr, description, investorName, equityPct,
                preMoneyValuationInr, sharesIssued, recurring, id)
                .stream().findFirst();
    }

    /** Newest saved first, so a transaction that was just added is at the top. */
    public List<Transaction> findAllNewestFirst() {
        return jdbc.query("SELECT * FROM transactions ORDER BY id DESC", MAPPER);
    }

    /** All the money one investor has put in through equity investments (the name matches in any capitalisation). Zero if none. */
    public BigDecimal totalEquityInvestedBy(String investorName) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_inr), 0) FROM transactions
                WHERE category = 'EQUITY_INVESTMENT' AND lower(investor_name) = lower(?)
                """, BigDecimal.class, investorName);
    }

    /**
     * The sums the dashboard metrics are built from, in one pass over the table.
     *
     * @param after only transactions dated after this day count towards the windowed figures; cash counts everything
     */
    public Totals totals(LocalDate after) {
        return jdbc.queryForObject("""
                SELECT
                    COALESCE(SUM(CASE WHEN type IN ('REVENUE', 'FUNDING') THEN amount_inr
                                      WHEN type = 'EXPENSE' THEN -amount_inr END), 0) AS cash,
                    COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND occurred_on > ? THEN amount_inr END), 0) AS expenses,
                    COALESCE(SUM(CASE WHEN type = 'REVENUE' AND occurred_on > ? THEN amount_inr END), 0) AS revenue,
                    COALESCE(SUM(CASE WHEN type = 'REVENUE' AND category = 'SUBSCRIPTION' AND occurred_on > ? THEN amount_inr END), 0) AS subscription_revenue,
                    COUNT(CASE WHEN type = 'REVENUE' AND category = 'SUBSCRIPTION' AND occurred_on > ? THEN 1 END) AS subscription_count
                FROM transactions
                """,
                (rs, i) -> new Totals(rs.getBigDecimal("cash"), rs.getBigDecimal("expenses"), rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("subscription_revenue"), rs.getInt("subscription_count")),
                Date.valueOf(after), Date.valueOf(after), Date.valueOf(after), Date.valueOf(after));
    }

    /** {@code expenses}, {@code revenue} and the subscription figures cover the window only; {@code cash} is all time. */
    public record Totals(BigDecimal cash, BigDecimal expenses, BigDecimal revenue, BigDecimal subscriptionRevenue, int subscriptionCount) {
    }
}
