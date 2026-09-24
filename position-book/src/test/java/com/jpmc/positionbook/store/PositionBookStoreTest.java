package com.jpmc.positionbook.store;

import com.jpmc.positionbook.enums.EventType;
import com.jpmc.positionbook.model.Position;
import com.jpmc.positionbook.model.TradeEvent;
import com.jpmc.positionbook.store.PositionBookStore.CancelResult;
import com.jpmc.positionbook.store.PositionBookStore.SellResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PositionBookStoreTest {

    private PositionBookStore store;

    @BeforeEach
    void setUp() {
        store = new PositionBookStore();
    }

    private TradeEvent buildEvent(int id, EventType eventType, String account, String security, int quantity) {
        return TradeEvent.builder()
                .id(id).eventType(eventType)
                .accountNumber(account).securityIdentifier(security)
                .quantity(quantity).build();
    }

    private Position getPosition(String accountNumber, String securityIdentifier) {
        return store.getPositionsByAccount(accountNumber).get(accountNumber + ":" + securityIdentifier);
    }

    @Test
    void saveBuyEvent_firstTime_returnsTrue() {
        TradeEvent event = buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100);

        boolean result = store.saveBuyEvent("1:ACC1", event, "ACC1:SEC1");

        assertThat(result).isTrue();
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(100);
    }

    @Test
    void saveBuyEvent_updatesPositionQuantityAndHistory() {
        TradeEvent event1 = buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100);
        TradeEvent event2 = buildEvent(2, EventType.BUY, "ACC1", "SEC1", 50);

        store.saveBuyEvent("1:ACC1", event1, "ACC1:SEC1");
        store.saveBuyEvent("2:ACC1", event2, "ACC1:SEC1");

        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(150);
        assertThat(getPosition("ACC1", "SEC1").getEventHistory()).hasSize(2);
    }

    @Test
    void saveBuyEvent_duplicateEventKey_returnsFalse() {
        TradeEvent event = buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100);

        store.saveBuyEvent("1:ACC1", event, "ACC1:SEC1");
        boolean result = store.saveBuyEvent("1:ACC1", event, "ACC1:SEC1");

        assertThat(result).isFalse();
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(100);
    }

    @Test
    void saveBuyEvent_differentAccounts_separatePositions() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");
        store.saveBuyEvent("2:ACC2", buildEvent(2, EventType.BUY, "ACC2", "SEC1", 50), "ACC2:SEC1");

        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(100);
        assertThat(getPosition("ACC2", "SEC1").getQuantity()).isEqualTo(50);
    }

    @Test
    void trySell_success_deductsQuantity() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");

        SellResult result = store.trySell("2:ACC1", buildEvent(2, EventType.SELL, "ACC1", "SEC1", 40), "ACC1:SEC1");

        assertThat(result).isEqualTo(SellResult.SUCCESS);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(60);
    }

    @Test
    void trySell_positionNotFound_returnsNotFound() {
        SellResult result = store.trySell("1:ACC1", buildEvent(1, EventType.SELL, "ACC1", "SEC1", 50), "ACC1:SEC1");

        assertThat(result).isEqualTo(SellResult.NOT_FOUND);
    }

    @Test
    void trySell_insufficientQuantity_returnsInsufficient() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 30), "ACC1:SEC1");

        SellResult result = store.trySell("2:ACC1", buildEvent(2, EventType.SELL, "ACC1", "SEC1", 50), "ACC1:SEC1");

        assertThat(result).isEqualTo(SellResult.INSUFFICIENT);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(30);
    }

    @Test
    void trySell_duplicateEventKey_returnsDuplicate() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");
        store.trySell("2:ACC1", buildEvent(2, EventType.SELL, "ACC1", "SEC1", 20), "ACC1:SEC1");

        SellResult result = store.trySell("2:ACC1", buildEvent(2, EventType.SELL, "ACC1", "SEC1", 20), "ACC1:SEC1");

        assertThat(result).isEqualTo(SellResult.DUPLICATE);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(80);
    }

    @Test
    void trySell_concurrentSells_noOverselling() throws InterruptedException {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");

        int threadCount = 10;
        int sellQuantity = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int eventId = i + 2;
            executor.submit(() -> {
                try {
                    SellResult result = store.trySell(
                            eventId + ":ACC1",
                            buildEvent(eventId, EventType.SELL, "ACC1", "SEC1", sellQuantity),
                            "ACC1:SEC1");
                    if (result == SellResult.SUCCESS) successCount.incrementAndGet();
                    if (result == SellResult.INSUFFICIENT) insufficientCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(0);
    }

    @Test
    void tryCancel_buyEvent_reversesQuantity() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");

        CancelResult result = store.tryCancel("1:ACC1", buildEvent(1, EventType.CANCEL, "ACC1", "SEC1", 0));

        assertThat(result).isEqualTo(CancelResult.SUCCESS);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(0);
    }

    @Test
    void tryCancel_sellEvent_restoresQuantity() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");
        store.trySell("2:ACC1", buildEvent(2, EventType.SELL, "ACC1", "SEC1", 40), "ACC1:SEC1");

        CancelResult result = store.tryCancel("2:ACC1", buildEvent(2, EventType.CANCEL, "ACC1", "SEC1", 0));

        assertThat(result).isEqualTo(CancelResult.SUCCESS);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(100);
    }

    @Test
    void tryCancel_eventNotFound_returnsNotFound() {
        CancelResult result = store.tryCancel("99:ACC1", buildEvent(99, EventType.CANCEL, "ACC1", "SEC1", 0));

        assertThat(result).isEqualTo(CancelResult.NOT_FOUND);
    }

    @Test
    void tryCancel_alreadyCancelled_returnsAlreadyCancelled() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");
        store.tryCancel("1:ACC1", buildEvent(1, EventType.CANCEL, "ACC1", "SEC1", 0));

        CancelResult result = store.tryCancel("1:ACC1", buildEvent(1, EventType.CANCEL, "ACC1", "SEC1", 0));

        assertThat(result).isEqualTo(CancelResult.ALREADY_CANCELLED);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(0);
    }

    @Test
    void tryCancel_concurrentCancels_onlyOneSucceeds() throws InterruptedException {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    CancelResult result = store.tryCancel("1:ACC1",
                            buildEvent(1, EventType.CANCEL, "ACC1", "SEC1", 0));
                    if (result == CancelResult.SUCCESS) successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(0);
    }

    @Test
    void getEvent_existingKey_returnsEvent() {
        TradeEvent event = buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100);
        store.saveBuyEvent("1:ACC1", event, "ACC1:SEC1");

        assertThat(store.getEvent("1:ACC1")).isEqualTo(event);
    }

    @Test
    void getEvent_missingKey_returnsNull() {
        assertThat(store.getEvent("99:ACC1")).isNull();
    }

    @Test
    void getPositionsByAccount_existingAccount_returnsPositions() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");
        store.saveBuyEvent("2:ACC1", buildEvent(2, EventType.BUY, "ACC1", "SEC2", 50), "ACC1:SEC2");

        assertThat(store.getPositionsByAccount("ACC1")).hasSize(2);
        assertThat(getPosition("ACC1", "SEC1").getQuantity()).isEqualTo(100);
        assertThat(getPosition("ACC1", "SEC2").getQuantity()).isEqualTo(50);
    }

    @Test
    void getPositionsByAccount_unknownAccount_returnsEmptyMap() {
        assertThat(store.getPositionsByAccount("UNKNOWN")).isEmpty();
    }

    @Test
    void tryCancel_cancelEvent_appearsInHistory() {
        store.saveBuyEvent("1:ACC1", buildEvent(1, EventType.BUY, "ACC1", "SEC1", 100), "ACC1:SEC1");
        TradeEvent cancelEvent = buildEvent(1, EventType.CANCEL, "ACC1", "SEC1", 0);

        store.tryCancel("1:ACC1", cancelEvent);

        assertThat(getPosition("ACC1", "SEC1").getEventHistory()).hasSize(2);
        assertThat(getPosition("ACC1", "SEC1").getEventHistory().get(1).getEventType())
                .isEqualTo(EventType.CANCEL);
    }
}
