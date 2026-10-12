package com.raiseready.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Talks to the real database; each test is rolled back, so nothing it adds is left behind. */
@SpringBootTest
@Transactional
class TransactionServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 1);

    @Autowired
    TransactionService service;

    @Autowired
    TransactionRepository repository;

    /** An equity investment gets a ₹1 crore pre-money unless a test says otherwise, so tests about other rules aren't tripped by a missing valuation. */
    private static NewTransaction request(TransactionType type, TransactionCategory category, String amount, String investor) {
        return request(type, category, amount, investor, category == TransactionCategory.EQUITY_INVESTMENT ? "10000000" : null);
    }

    private static NewTransaction request(TransactionType type, TransactionCategory category, String amount, String investor, String preMoney) {
        return new NewTransaction(type, category, DAY, new BigDecimal(amount), "  Laptop   purchase ", investor,
                preMoney == null ? null : new BigDecimal(preMoney));
    }

    @Test
    void savesTheRowAndTheDatabaseSetsTheCreatedTime() {
        Transaction saved = service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "1500", null));

        assertThat(saved.id()).isPositive();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(repository.findAllNewestFirst().get(0).id()).isEqualTo(saved.id());
    }

    @Test
    void roundsTheAmountToTwoDecimalPlaces() {
        assertThat(service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "10.005", null)).amountInr())
                .isEqualByComparingTo("10.01");
        assertThat(service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "10.004", null)).amountInr())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void tidiesTheDescription() {
        assertThat(service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "5", null)).description())
                .isEqualTo("Laptop purchase");
    }

    @Test
    void recurringFlagComesFromTheCategory() {
        assertThat(service.add(request(TransactionType.EXPENSE, TransactionCategory.SALARIES, "5", null)).recurring()).isTrue();
        assertThat(service.add(request(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, "5", null)).recurring()).isTrue();
        assertThat(service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "5", null)).recurring()).isFalse();
        assertThat(service.add(request(TransactionType.REVENUE, TransactionCategory.ONE_TIME_SALE, "5", null)).recurring()).isFalse();
    }

    @Test
    void categoryMustBelongToTheType() {
        assertThatThrownBy(() -> service.add(request(TransactionType.EXPENSE, TransactionCategory.SUBSCRIPTION, "5", null)))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("category"));
    }

    @Test
    void equityInvestmentNeedsAnInvestorName() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "5000", blank)))
                    .isInstanceOfSatisfying(TransactionRuleException.class,
                            e -> assertThat(e.getErrors()).containsOnlyKeys("investorName"));
        }
    }

    @Test
    void investorNameIsTidiedAndKeptForFunding() {
        Transaction saved = service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "5000", "  Ananya    Rao "));

        assertThat(saved.investorName()).isEqualTo("Ananya Rao");
    }

    @Test
    void investorNameIsClearedWhenTheTypeIsNotFunding() {
        assertThat(service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "5", "Ananya Rao")).investorName()).isNull();
        assertThat(service.add(request(TransactionType.REVENUE, TransactionCategory.SUBSCRIPTION, "5", "Ananya Rao")).investorName()).isNull();
    }

    @Test
    void anAmountThatRoundsToZeroIsRejected() {
        assertThatThrownBy(() -> service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "0.004", null)))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("amount"));
    }

    @Test
    void reportsEveryBrokenRuleAtOnceAndSavesNothing() {
        int before = repository.findAllNewestFirst().size();

        assertThatThrownBy(() -> service.add(request(TransactionType.FUNDING, TransactionCategory.TRAVEL, "0.001", null)))
                .isInstanceOfSatisfying(TransactionRuleException.class,
                        e -> assertThat(e.getErrors()).containsOnlyKeys("category", "amount"));
        assertThat(repository.findAllNewestFirst()).hasSize(before);
    }

    @Test
    void everyCategoryBelongsToOneTypeAndEachTypeHasCategories() {
        for (TransactionType type : TransactionType.values()) {
            assertThat(TransactionCategory.values()).anyMatch(c -> c.getType() == type);
        }
    }

    // ---- editing: same rules as adding ----

    @Test
    void updateReplacesTheEditableFieldsAndKeepsTheIdAndCreatedTime() {
        Transaction original = service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "100", null));

        Transaction updated = service.update(original.id(), new NewTransaction(
                TransactionType.EXPENSE, TransactionCategory.MARKETING, DAY.minusDays(3), new BigDecimal("250.5"), "Flyers", null, null));

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(updated.category()).isEqualTo(TransactionCategory.MARKETING);
        assertThat(updated.occurredOn()).isEqualTo(DAY.minusDays(3));
        assertThat(updated.amountInr()).isEqualByComparingTo("250.50");
        assertThat(updated.description()).isEqualTo("Flyers");
        assertThat(service.get(original.id())).isEqualTo(updated);
        assertThat(repository.findAllNewestFirst().stream().filter(t -> t.id() == original.id())).hasSize(1);
    }

    @Test
    void updateWorksOutTheRecurringFlagAgainFromTheNewCategory() {
        Transaction original = service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "100", null));
        assertThat(original.recurring()).isFalse();

        Transaction recurring = service.update(original.id(), request(TransactionType.EXPENSE, TransactionCategory.RENT, "100", null));
        assertThat(recurring.recurring()).isTrue();

        Transaction notRecurring = service.update(original.id(), request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "100", null));
        assertThat(notRecurring.recurring()).isFalse();
    }

    @Test
    void updateClearsTheInvestorNameWhenTheTypeStopsBeingFunding() {
        Transaction funding = service.add(request(TransactionType.FUNDING, TransactionCategory.LOAN, "5000", "Ananya Rao"));
        assertThat(funding.investorName()).isEqualTo("Ananya Rao");

        Transaction expense = service.update(funding.id(), request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "5000", "Ananya Rao"));

        assertThat(expense.investorName()).isNull();
    }

    @Test
    void updateAppliesTheSameRulesAndLeavesTheSavedRowUntouched() {
        Transaction original = service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "100", null));

        assertThatThrownBy(() -> service.update(original.id(), request(TransactionType.EXPENSE, TransactionCategory.SUBSCRIPTION, "100", null)))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("category"));
        assertThatThrownBy(() -> service.update(original.id(), request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "100", "  ")))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("investorName"));
        assertThatThrownBy(() -> service.update(original.id(), request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "0.001", null)))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("amount"));

        assertThat(service.get(original.id())).isEqualTo(original);
    }

    @Test
    void updatingOrReadingAnUnknownIdIsNotFound() {
        long missing = repository.findAllNewestFirst().stream().mapToLong(Transaction::id).max().orElse(0) + 1000;

        assertThatThrownBy(() -> service.update(missing, request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "5", null)))
                .isInstanceOf(TransactionNotFoundException.class);
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(TransactionNotFoundException.class);
    }

    // ---- equity: pre-money in, equity and shares out (the cap table itself is in CapTableTest) ----

    @Test
    void equityInvestmentSavesItsPreMoneyAndTheEquityAndPostMoneyFollowFromIt() {
        Transaction saved = service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "2000000", "Ananya Rao", "18000000"));

        assertThat(saved.preMoneyValuationInr()).isEqualByComparingTo("18000000.00");
        // ₹20 lakh into a company worth ₹1.8 crore makes it worth ₹2 crore, of which the investor owns 10%.
        assertThat(saved.postMoneyValuationInr()).isEqualByComparingTo("20000000.00");
        assertThat(saved.equityPct()).isEqualByComparingTo("10.00");
        assertThat(saved.sharesIssued()).isPositive();
        assertThat(service.get(saved.id())).isEqualTo(saved);
    }

    @Test
    void equityIsRoundedToTwoDecimalPlaces() {
        // 1000 / (29000 + 1000) = 3.333...%
        assertThat(service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "1000", "A", "29000")).equityPct())
                .isEqualByComparingTo("3.33");
    }

    @Test
    void equityInvestmentNeedsAPositivePreMoney() {
        for (String bad : new String[] {null, "0", "-5", "0.004"}) {
            assertThatThrownBy(() -> service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "1000", "A", bad)))
                    .as("pre-money %s", bad)
                    .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("preMoneyValuation"));
        }
    }

    @Test
    void preMoneyAndSharesAreDroppedForEveryOtherCategory() {
        Transaction loan = service.add(request(TransactionType.FUNDING, TransactionCategory.LOAN, "1000", "A", "10000000"));
        Transaction expense = service.add(request(TransactionType.EXPENSE, TransactionCategory.TRAVEL, "1000", null, "10000000"));

        for (Transaction t : new Transaction[] {loan, expense}) {
            assertThat(t.equityPct()).isNull();
            assertThat(t.preMoneyValuationInr()).isNull();
            assertThat(t.postMoneyValuationInr()).isNull();
            assertThat(t.sharesIssued()).isNull();
        }
    }

    @Test
    void anInvestmentTooSmallToBuyAShareIsRejectedAndNothingIsSaved() {
        int before = repository.findAllNewestFirst().size();

        // ₹1 into a ₹100 crore company is a hundred-millionth of a percent.
        assertThatThrownBy(() -> service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "1", "A", "1000000000")))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("amount"));
        assertThat(repository.findAllNewestFirst()).hasSize(before);
    }

    @Test
    void anInvestorCannotBeCalledFounder() {
        assertThatThrownBy(() -> service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "1000", " founder ")))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("investorName"));
    }

    @Test
    void anEquityInvestmentThatCreatedSharesCanOnlyChangeItsDateAndDescription() {
        Transaction equity = service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "2000000", "A", "18000000"));

        Transaction edited = service.update(equity.id(), new NewTransaction(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT,
                DAY.minusDays(1), new BigDecimal("2000000"), "Corrected date", "A", new BigDecimal("18000000")));

        assertThat(edited.occurredOn()).isEqualTo(DAY.minusDays(1));
        assertThat(edited.description()).isEqualTo("Corrected date");
        assertThat(edited.sharesIssued()).isEqualTo(equity.sharesIssued());
        assertThat(edited.equityPct()).isEqualByComparingTo(equity.equityPct());
    }

    @Test
    void changingTheMoneyTermsOfAnInvestmentThatCreatedSharesIsRefusedAndNothingChanges() {
        Transaction equity = service.add(request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "2000000", "A", "18000000"));

        assertThatThrownBy(() -> service.update(equity.id(), request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "3000000", "A", "18000000")))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("amount"));
        assertThatThrownBy(() -> service.update(equity.id(), request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "2000000", "B", "18000000")))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("investorName"));
        assertThatThrownBy(() -> service.update(equity.id(), request(TransactionType.FUNDING, TransactionCategory.EQUITY_INVESTMENT, "2000000", "A", "20000000")))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsOnlyKeys("preMoneyValuation"));
        assertThatThrownBy(() -> service.update(equity.id(), request(TransactionType.FUNDING, TransactionCategory.LOAN, "2000000", "A", null)))
                .isInstanceOfSatisfying(TransactionRuleException.class, e -> assertThat(e.getErrors()).containsKeys("category", "preMoneyValuation"));

        assertThat(service.get(equity.id())).isEqualTo(equity);
    }
}
