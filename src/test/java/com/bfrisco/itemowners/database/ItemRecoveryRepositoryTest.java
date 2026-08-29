package com.bfrisco.itemowners.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRecoveryRepositoryTest {
    @Test
    void onlyOneConcurrentReservationCanWinAndCompletionClosesTheLoss(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryRepository.init(tempDirectory.resolve("recoveries.db").toFile());
        ItemRecoveryRepository.recordLoss(
                "ABCD-EFGH-IJKL",
                "owner-id",
                "entity-id",
                "item-data",
                System.currentTimeMillis(),
                "world",
                1,
                2,
                3
        );

        ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId("ABCD-EFGH-IJKL");
        assertNotNull(recovery);
        assertEquals(ItemRecoveryStatus.AVAILABLE, recovery.getStatus());

        int contenders = 24;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            attempts.add(executor.submit(() -> {
                start.await();
                return ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D);
            }));
        }

        start.countDown();
        int winners = 0;
        for (Future<Boolean> attempt : attempts) {
            if (attempt.get()) {
                winners++;
            }
        }
        executor.shutdownNow();

        assertEquals(1, winners);
        assertTrue(ItemRecoveryRepository.markChargeIntent(recovery.getId()));
        assertTrue(ItemRecoveryRepository.beginWithdrawal(recovery.getId()));
        assertTrue(ItemRecoveryRepository.markCharged(recovery.getId(), 975.5D));
        assertTrue(ItemRecoveryRepository.markIssued(recovery.getId()));
        assertTrue(ItemRecoveryRepository.beginDelivery(recovery.getId()));
        assertTrue(ItemRecoveryRepository.complete(recovery.getId()));
        assertFalse(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));

        ItemRecovery completed = ItemRecoveryRepository.findLatestByItemId("ABCD-EFGH-IJKL");
        assertEquals(ItemRecoveryStatus.COMPLETED, completed.getStatus());
        assertEquals(975.5D, completed.getQuotedFee());
        assertNull(completed.getOpenItemId());
    }

    @Test
    void anUnpaidReservationCanBeReleasedAndRetried(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryRepository.init(tempDirectory.resolve("recoveries.db").toFile());
        ItemRecoveryRepository.recordLoss(
                "MNOP-QRST-UVWX",
                "owner-id",
                "second-entity-id",
                "item-data",
                System.currentTimeMillis(),
                "world",
                4,
                5,
                6
        );

        ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId("MNOP-QRST-UVWX");
        assertTrue(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));
        assertTrue(ItemRecoveryRepository.markChargeIntent(recovery.getId()));
        assertTrue(ItemRecoveryRepository.cancelBeforeIssue(recovery.getId()));
        assertTrue(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));
        assertTrue(ItemRecoveryRepository.markChargeIntent(recovery.getId()));
        assertTrue(ItemRecoveryRepository.beginWithdrawal(recovery.getId()));
        assertTrue(ItemRecoveryRepository.markCharged(recovery.getId(), 1000D));
        assertTrue(ItemRecoveryRepository.markIssued(recovery.getId()));
        assertTrue(ItemRecoveryRepository.beginDelivery(recovery.getId()));
        assertTrue(ItemRecoveryRepository.beginDeliveryRefund(recovery.getId()));
        assertFalse(ItemRecoveryRepository.beginDeliveryRefund(recovery.getId()));
        assertEquals(ItemRecoveryStatus.REFUND_INTENT, ItemRecoveryRepository.findLatestByItemId("MNOP-QRST-UVWX").getStatus());
        assertTrue(ItemRecoveryRepository.finishRefund(recovery.getId()));
        assertTrue(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));
    }

    @Test
    void duplicateEntitiesKeepTheItemPermanentlyConflictLocked(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryRepository.init(tempDirectory.resolve("recoveries.db").toFile());
        String itemId = "YZ12-3456-7890";
        ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "first-entity",
                "item-data",
                System.currentTimeMillis(),
                "world",
                7,
                8,
                9
        );

        assertFalse(ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "second-entity",
                "other-item-data",
                System.currentTimeMillis(),
                "world",
                7,
                8,
                9
        ));

        ItemRecovery conflicted = ItemRecoveryRepository.findLatestByItemId(itemId);
        assertEquals(ItemRecoveryStatus.CONFLICTED, conflicted.getStatus());
        assertEquals(itemId, conflicted.getOpenItemId());

        assertFalse(ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "third-entity",
                "third-item-data",
                System.currentTimeMillis(),
                "world",
                7,
                8,
                9
        ));
        assertEquals(ItemRecoveryStatus.CONFLICTED, ItemRecoveryRepository.findLatestByItemId(itemId).getStatus());
    }

    @Test
    void aDuplicateDespawnWinsTheRaceAgainstReservation(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryRepository.init(tempDirectory.resolve("recoveries.db").toFile());
        String itemId = "RACE-TEST-0001";
        ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "race-first-entity",
                "item-data",
                System.currentTimeMillis(),
                "world",
                10,
                11,
                12
        );
        ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId(itemId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> reservation = executor.submit(() -> {
            start.await();
            return ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D);
        });
        Future<Boolean> conflict = executor.submit(() -> {
            start.await();
            return ItemRecoveryRepository.recordLoss(
                    itemId,
                    "owner-id",
                    "race-second-entity",
                    "other-item-data",
                    System.currentTimeMillis(),
                    "world",
                    10,
                    11,
                    12
            );
        });

        start.countDown();
        reservation.get();
        assertFalse(conflict.get());
        executor.shutdownNow();

        assertEquals(ItemRecoveryStatus.CONFLICTED, ItemRecoveryRepository.findLatestByItemId(itemId).getStatus());
        assertFalse(ItemRecoveryRepository.markChargeIntent(recovery.getId()));
    }

    @Test
    void conflictAtChargeIntentPreventsWithdrawalFromBeginning(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryRepository.init(tempDirectory.resolve("charge-intent-conflict.db").toFile());
        String itemId = "CHRG-INTN-0001";
        ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "charge-intent-first",
                "item-data",
                System.currentTimeMillis(),
                "world",
                13,
                14,
                15
        );
        ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId(itemId);
        assertTrue(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));
        assertTrue(ItemRecoveryRepository.markChargeIntent(recovery.getId()));

        assertFalse(ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "charge-intent-second",
                "other-item-data",
                System.currentTimeMillis(),
                "world",
                13,
                14,
                15
        ));

        assertEquals(ItemRecoveryStatus.CONFLICTED, ItemRecoveryRepository.findLatestByItemId(itemId).getStatus());
        assertFalse(ItemRecoveryRepository.beginWithdrawal(recovery.getId()));
    }

    @Test
    void duplicateDespawnsDoNotMutateCommittedRecoveryStates(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryStatus[] committedStates = {
                ItemRecoveryStatus.WITHDRAWING,
                ItemRecoveryStatus.CHARGED,
                ItemRecoveryStatus.ISSUED,
                ItemRecoveryStatus.DELIVERING,
                ItemRecoveryStatus.REFUND_INTENT
        };

        for (int index = 0; index < committedStates.length; index++) {
            ItemRecoveryStatus expectedStatus = committedStates[index];
            ItemRecoveryRepository.init(tempDirectory.resolve("committed-" + expectedStatus + ".db").toFile());
            String itemId = "CMT" + index + "-STAT-0001";
            ItemRecoveryRepository.recordLoss(
                    itemId,
                    "owner-id",
                    "committed-first-" + index,
                    "item-data",
                    System.currentTimeMillis(),
                    "world",
                    16,
                    17,
                    18
            );
            ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId(itemId);
            assertTrue(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));
            assertTrue(ItemRecoveryRepository.markChargeIntent(recovery.getId()));
            assertTrue(ItemRecoveryRepository.beginWithdrawal(recovery.getId()));

            if (expectedStatus == ItemRecoveryStatus.CHARGED
                    || expectedStatus == ItemRecoveryStatus.ISSUED
                    || expectedStatus == ItemRecoveryStatus.DELIVERING
                    || expectedStatus == ItemRecoveryStatus.REFUND_INTENT) {
                assertTrue(ItemRecoveryRepository.markCharged(recovery.getId(), 1000D));
            }
            if (expectedStatus == ItemRecoveryStatus.ISSUED
                    || expectedStatus == ItemRecoveryStatus.DELIVERING
                    || expectedStatus == ItemRecoveryStatus.REFUND_INTENT) {
                assertTrue(ItemRecoveryRepository.markIssued(recovery.getId()));
            }
            if (expectedStatus == ItemRecoveryStatus.DELIVERING
                    || expectedStatus == ItemRecoveryStatus.REFUND_INTENT) {
                assertTrue(ItemRecoveryRepository.beginDelivery(recovery.getId()));
            }
            if (expectedStatus == ItemRecoveryStatus.REFUND_INTENT) {
                assertTrue(ItemRecoveryRepository.beginDeliveryRefund(recovery.getId()));
            }

            assertFalse(ItemRecoveryRepository.recordLoss(
                    itemId,
                    "owner-id",
                    "committed-second-" + index,
                    "other-item-data",
                    System.currentTimeMillis(),
                    "world",
                    16,
                    17,
                    18
            ));
            assertEquals(expectedStatus, ItemRecoveryRepository.findLatestByItemId(itemId).getStatus());
        }
    }

    @Test
    void invalidatingAnAvailableRecoveryClosesItPermanently(@TempDir Path tempDirectory) throws Exception {
        ItemRecoveryRepository.init(tempDirectory.resolve("invalidated.db").toFile());
        String itemId = "INVL-DATE-0001";
        ItemRecoveryRepository.recordLoss(
                itemId,
                "owner-id",
                "invalidated-entity",
                "item-data",
                System.currentTimeMillis(),
                "world",
                19,
                20,
                21
        );
        ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId(itemId);

        assertTrue(ItemRecoveryRepository.invalidateAvailable(recovery.getId()));
        assertFalse(ItemRecoveryRepository.reserve(recovery.getId(), "owner-id", 1000D));

        ItemRecovery invalidated = ItemRecoveryRepository.findLatestByItemId(itemId);
        assertEquals(ItemRecoveryStatus.INVALIDATED, invalidated.getStatus());
        assertNull(invalidated.getOpenItemId());
    }
}
